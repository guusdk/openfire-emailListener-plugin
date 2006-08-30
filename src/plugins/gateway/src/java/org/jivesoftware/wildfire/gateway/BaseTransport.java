/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.SessionManager;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.roster.*;
import org.jivesoftware.wildfire.roster.Roster;
import org.jivesoftware.wildfire.user.UserAlreadyExistsException;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.*;
import org.xmpp.packet.PacketError.Condition;

import java.util.*;

/**
 * Base class of all transport implementations.
 *
 * Handles all transport non-specific tasks and provides the glue that holds
 * together server interactions and the legacy service.  Does the bulk of
 * the XMPP related work.  Also note that this represents the transport
 * itself, not an individual session with the transport.
 *
 * @author Daniel Henninger
 */
public abstract class BaseTransport implements Component, RosterEventListener {

    /**
     * Create a new BaseTransport instance.
     */
    public BaseTransport() {
        // We've got nothing to do here.
    }

    /**
     * Set up the transport instance.
     *
     * @param type Type of the transport.
     * @param description Description of the transport (for Disco).
     */
    public void setup(TransportType type, String description) {
        this.description = description;
        this.transportType = type;
    }

    /**
     * Handles initialization of the transport.
     */
    public void initialize(JID jid, ComponentManager componentManager) {
        this.jid = jid;
        this.componentManager = componentManager;
    }

    /**
     * Manages all active sessions.
     * @see org.jivesoftware.wildfire.gateway.TransportSessionManager
     */
    public final TransportSessionManager sessionManager = new TransportSessionManager();

    /**
     * Manages registration information.
     * @see org.jivesoftware.wildfire.gateway.RegistrationManager
     */
    public final RegistrationManager registrationManager = new RegistrationManager();

    /**
     * JID of the transport in question.
     */
    public JID jid = null;

    /**
     * Description of the transport in question.
     */
    public String description = null;

    /**
     * Component Manager associated with transport.
     */
    public ComponentManager componentManager = null;

    /**
     * Manager component for user rosters.
     */
    public final RosterManager rosterManager = new RosterManager();

    /**
     * Type of the transport in question.
     * @see org.jivesoftware.wildfire.gateway.TransportType
     */
    public TransportType transportType = null;

    private final String DISCO_INFO = "http://jabber.org/protocol/disco#info";
    private final String DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    private final String IQ_GATEWAY = "jabber:iq:gateway";
    private final String IQ_REGISTER = "jabber:iq:register";
    private final String IQ_VERSION = "jabber:iq:version";

    /**
     * Handles all incoming XMPP stanzas, passing them to individual
     * packet type handlers.
     *
     * @param packet The packet to be processed.
     */
    public void processPacket(Packet packet) {
        try {
            List<Packet> reply = new ArrayList<Packet>();
            if (packet instanceof IQ) {
                reply.addAll(processPacket((IQ)packet));
            }
            else if (packet instanceof Presence) {
                reply.addAll(processPacket((Presence)packet));
            }
            else if (packet instanceof Message) {
                reply.addAll(processPacket((Message)packet));
            }
            else {
                Log.info("Received an unhandled packet: " + packet.toString());
            }

            if (reply.size() > 0) {
                for (Packet p : reply) {
                    this.sendPacket(p);
                }
            }
        }
        catch (Exception e) {
            Log.error("Error occured while processing packet:", e);
        }
    }

    /**
     * Handles all incoming message stanzas.
     *
     * @param packet The message packet to be processed.
     */
    private List<Packet> processPacket(Message packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        try {
            TransportSession session = sessionManager.getSession(from);
            session.sendMessage(to, packet.getBody());
        }
        catch (NotFoundException e) {
            // TODO: Should return an error packet here
            Log.debug("Unable to find session.");
        }

        return reply;
    }

    /**
     * Handles all incoming presence stanzas.
     *
     * @param packet The presence packet to be processed.
     */
    private List<Packet> processPacket(Presence packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        if (packet.getType() == Presence.Type.error) {
            // We don't want to do anything with this.  Ignore it.
            return reply;
        }

        try {
            if (to.getNode() == null) {
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (registrations.isEmpty()) {
                    // User is not registered with us.
                    Log.debug("Unable to find registration.");
                    return reply;
                }
                Registration registration = registrations.iterator().next();

                // This packet is to the transport itself.
                if (packet.getType() == null) {
                    // A user's resource has come online.
                    TransportSession session;
                    try {
                        session = sessionManager.getSession(from);

                        if (session.hasResource(from.getResource())) {
                            Log.debug("An existing resource has changed status: " + from);

                            if (session.getPriority(from.getResource()) != packet.getPriority()) {
                                session.updatePriority(from.getResource(), packet.getPriority());
                            }
                            if (session.isHighestPriority(from.getResource())) {
                                // Well, this could represent a status change.
                                session.updateStatus(getPresenceType(packet), packet.getStatus());
                            }
                        }
                        else {
                            Log.debug("A new resource has come online: " + from);

                            // This is a new resource, lets send them what we know.
                            session.addResource(from.getResource(), packet.getPriority());
                            // Tell the new resource what the state of their buddy list is.
                            session.resendContactStatuses(from);
                            // If this priority is the highest, treat it's status as golden
                            if (session.isHighestPriority(from.getResource())) {
                                session.updateStatus(getPresenceType(packet), packet.getStatus());
                            }
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("A new session has come online: " + from);

                        session = this.registrationLoggedIn(registration, from, getPresenceType(packet), packet.getStatus(), packet.getPriority());
                        sessionManager.storeSession(from, session);

                    }
                }
                else if (packet.getType() == Presence.Type.unavailable) {
                    // A user's resource has gone offline.
                    TransportSession session;
                    try {
                        session = sessionManager.getSession(from);
                        if (session.getResourceCount() > 1) {
                            String resource = from.getResource();

                            // Just one of the resources, lets adjust accordingly.
                            if (session.isHighestPriority(resource)) {
                                Log.debug("A high priority resource (of multiple) has gone offline: " + from);

                                // Ooh, the highest resource went offline, drop to next highest.
                                session.removeResource(resource);

                                // Lets ask the next highest resource what it's presence is.
                                Presence p = new Presence(Presence.Type.probe);
                                p.setTo(session.getJIDWithHighestPriority());
                                p.setFrom(this.getJID());
                                sendPacket(p);
                            }
                            else {
                                Log.debug("A low priority resource (of multiple) has gone offline: " + from);

                                // Meh, lower priority, big whoop.
                                session.removeResource(resource);
                            }
                        }
                        else {
                            Log.debug("A final resource has gone offline: " + from);

                            // No more resources, byebye.
                            if (session.isLoggedIn()) {
                                this.registrationLoggedOut(session);
                            }

                            sessionManager.removeSession(from);
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring unavailable presence for inactive seession.");
                    }
                }
                else if (packet.getType() == Presence.Type.probe) {
                    // Client is asking for presence status.
                    TransportSession session;
                    try {
                        session = sessionManager.getSession(from);
                        if (session.isLoggedIn()) {
                            Presence p = new Presence();
                            p.setTo(from);
                            p.setFrom(to);
                            this.sendPacket(p);
                        }
                    }
                    catch (NotFoundException e) {
                        Log.debug("Ignoring probe presence for inactive session.");
                    }
                }
                else {
                    Log.debug("Ignoring this packet:" + packet.toString());
                    // Anything else we will ignore for now.
                }
            }
            else {
                // This packet is to a user at the transport.
                try {
                    TransportSession session = sessionManager.getSession(from);

                    if (packet.getType() == Presence.Type.probe) {
                        // Presence probe, lets try to tell them.
                        session.retrieveContactStatus(packet.getTo());
                    }
                    else if (packet.getType() == Presence.Type.subscribe) {
                        // User wants to add someone to their legacy roster.
                        session.addContact(packet.getTo());
                    }
                    else if (packet.getType() == Presence.Type.unsubscribe) {
                        // User wants to remove someone from their legacy roster.
                        session.removeContact(packet.getTo());
                    }
                    else {
                        // Anything else we will ignore for now.
                    }
                }
                catch (NotFoundException e) {
                    // Well we just don't care then.
                }
            }
        }
        catch (Exception e) {
            Log.error("Exception while processing packet: ", e);
        }

        return reply;
    }

    /**
     * Handles all incoming iq stanzas.
     *
     * @param packet The iq packet to be processed.
     */
    private List<Packet> processPacket(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.error) {
            // Lets not start a loop.  Ignore.
            return reply;
        }

        String xmlns = null;
        Element child = (packet).getChildElement();
        if (child != null) {
            xmlns = child.getNamespaceURI();
        }

        if (xmlns == null) {
            // No namespace defined.
            Log.debug("No XMLNS:" + packet.toString());
            IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.bad_request);
            reply.add(error);
            return reply;
        }

        if (xmlns.equals(DISCO_INFO)) {
            reply.addAll(handleDiscoInfo(packet));
        }
        else if (xmlns.equals(DISCO_ITEMS)) {
            reply.addAll(handleDiscoItems(packet));
        }
        else if (xmlns.equals(IQ_GATEWAY)) {
            reply.addAll(handleIQGateway(packet));
        }
        else if (xmlns.equals(IQ_REGISTER)) {
            reply.addAll(handleIQRegister(packet));
        }
        else if (xmlns.equals(IQ_VERSION)) {
            reply.addAll(handleIQVersion(packet));
        }
        else {
            Log.debug("Unable to handle iq request:" + xmlns);
            IQ error = IQ.createResultIQ(packet);
            error.setError(Condition.bad_request);
            reply.add(error);
        }

        return reply;
    }

    /**
     * Handle service discovery info request.
     *
     * @param packet An IQ packet in the disco info namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleDiscoInfo(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getTo().getNode() == null) {
            // Requested info from transport itself.
            IQ result = IQ.createResultIQ(packet);
            Element response = DocumentHelper.createElement(QName.get("query", DISCO_INFO));
            response.addElement("identity")
                    .addAttribute("category", "gateway")
                    .addAttribute("type", this.transportType.toString())
                    .addAttribute("name", this.description);
            response.addElement("feature")
                    .addAttribute("var", IQ_GATEWAY);
            response.addElement("feature")
                    .addAttribute("var", IQ_REGISTER);
            response.addElement("feature")
                    .addAttribute("var", IQ_VERSION);
            result.setChildElement(response);
            reply.add(result);
        }

        return reply;
    }

    /**
     * Handle service discovery items request.
     *
     * @param packet An IQ packet in the disco items namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleDiscoItems(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        IQ result = IQ.createResultIQ(packet);
        reply.add(result);
        return reply;
    }

    /**
     * Handle gateway translation service request.
     *
     * @param packet An IQ packet in the iq gateway namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQGateway(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            Element query = DocumentHelper.createElement(QName.get("query", IQ_GATEWAY));
            query.addElement("desc").addText("Please enter the person's "+this.getName()+" username.");
            query.addElement("prompt");
            result.setChildElement(query);
            reply.add(result);
        }
        else if (packet.getType() == IQ.Type.set) {
            IQ result = IQ.createResultIQ(packet);
            String prompt = null;
            Element promptEl = packet.getChildElement().element("prompt");
            if (promptEl != null) {
                prompt = promptEl.getTextTrim();
            }
            if (prompt == null) {
                result.setError(Condition.bad_request);
            }
            else {
                JID jid = this.convertIDToJID(prompt);
                Element query = DocumentHelper.createElement(QName.get("query", IQ_GATEWAY));
                // This is what Psi expects
                query.addElement("prompt").addText(jid.toString());
                // This is JEP complient
                query.addElement("jid").addText(jid.toString());
                result.setChildElement(query);
            }
            reply.add(result);
        }

        return reply;
    }

    /**
     * Handle registration request.
     *
     * @param packet An IQ packet in the iq registration namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQRegister(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();
        JID from = packet.getFrom();
        JID to = packet.getTo();

        Element remove = packet.getChildElement().element("remove");
        if (remove != null) {
            // User wants to unregister.  =(
            // this.convinceNotToLeave() ... kidding.
            IQ result = IQ.createResultIQ(packet);

            // Tell the end user the transport went byebye.
            Presence unavailable = new Presence(Presence.Type.unavailable);
            unavailable.setTo(from);
            unavailable.setFrom(to);
            reply.add(unavailable);

            try {
                this.deleteRegistration(from);
            }
            catch (UserNotFoundException e) {
                Log.error("Error cleaning up contact list of: " + from);
                result.setError(Condition.bad_request);
            }

            reply.add(result);
        }
        else {
            // User wants to register with the transport.
            String username = null;
            String password = null;

            try {
                DataForm form = new DataForm(packet.getChildElement().element("x"));
                List<FormField> fields = form.getFields();
                for (FormField field : fields) {
                    String var = field.getVariable();
                    if (var.equals("username")) {
                        username = field.getValues().get(0);
                    }
                    else if (var.equals("password")) {
                        password = field.getValues().get(0);
                    }
                }
            }
            catch (Exception e) {
                // No with data form apparantly
            }

            if (packet.getType() == IQ.Type.set) {
                Element userEl = packet.getChildElement().element("username");
                Element passEl = packet.getChildElement().element("password");

                if (userEl != null) {
                    username = userEl.getTextTrim();
                }

                if (passEl != null) {
                    password = passEl.getTextTrim();
                }

                if (username == null || password == null) {
                    // Found nothing from stanza, lets yell.
                    IQ result = IQ.createResultIQ(packet);
                    result.setError(Condition.bad_request);
                    reply.add(result);
                }
                else {
                    Log.info("Registered " + packet.getFrom() + " as " + username);
                    IQ result = IQ.createResultIQ(packet);
                    Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                    result.setChildElement(response);
                    reply.add(result);

                    try {
                        this.addNewRegistration(from, username, password);
                    }
                    catch (UserNotFoundException e) {
                        Log.error("Someone attempted to register with the gateway who is not registered with the server: " + from);
                        IQ eresult = IQ.createResultIQ(packet);
                        eresult.setError(Condition.bad_request);
                        reply.add(eresult);
                    }

                    // Lets ask them what their presence is, maybe log
                    // them in immediately.
                    Presence p = new Presence(Presence.Type.probe);
                    p.setTo(from);
                    p.setFrom(to);
                    reply.add(p);
                }
            }
            else if (packet.getType() == IQ.Type.get) {
                Element response = DocumentHelper.createElement(QName.get("query", IQ_REGISTER));
                IQ result = IQ.createResultIQ(packet);

                DataForm form = new DataForm(DataForm.Type.form);
                form.addInstruction("Please enter your " + this.getName() + " username and password.");

                FormField usernameField = form.addField();
                usernameField.setLabel("Username");
                usernameField.setVariable("username");
                usernameField.setType(FormField.Type.text_single);

                FormField passwordField = form.addField();
                passwordField.setLabel("Password");
                passwordField.setVariable("password");
                passwordField.setType(FormField.Type.text_private);

                response.addElement("instructions").addText("Please enter your " + this.getName() + " username and password.");
                Collection<Registration> registrations = registrationManager.getRegistrations(from, this.transportType);
                if (registrations.iterator().hasNext()) {
                    Registration registration = registrations.iterator().next();
                    response.addElement("registered");
                    response.addElement("username").addText(registration.getUsername());
                    response.addElement("password").addText(registration.getPassword());
                }
                else {
                    response.addElement("username");
                    response.addElement("password");
                }

                result.setChildElement(response);

                reply.add(result);
            }
        }

        return reply;
    }

    /**
     * Handle version request.
     *
     * @param packet An IQ packet in the iq version namespace.
     * @return A list of IQ packets to be returned to the user.
     */
    private List<Packet> handleIQVersion(IQ packet) {
        List<Packet> reply = new ArrayList<Packet>();

        if (packet.getType() == IQ.Type.get) {
            IQ result = IQ.createResultIQ(packet);
            Element query = DocumentHelper.createElement(QName.get("query", IQ_VERSION));
            query.addElement("name").addText("Wildfire " + this.getDescription());
            query.addElement("version").addText(XMPPServer.getInstance().getServerInfo().getVersion().getVersionString() + " - " + this.getVersionString());
            query.addElement("os").addText(System.getProperty("os.name"));
            result.setChildElement(query);
            reply.add(result);
        }

        return reply;
    }

    /**
     * Converts a legacy username to a JID.
     *
     * @param username Username to be converted to a JID.
     * @return The legacy username as a JID.
     */
    public JID convertIDToJID(String username) {
        return new JID(username.replace('@', '%').replace(" ", ""), this.jid.getDomain(), null);
    }

    /**
     * Converts a JID to a legacy username.
     *
     * @param jid JID to be converted to a legacy username.
     * @return THe legacy username as a String.
     */
    public String convertJIDToID(JID jid) {
        return jid.getNode().replace('%', '@');
    }

    /**
     * Gets an easy to use presence type from a presence packet.
     *
     * @param packet A presence packet from which the type will be pulled.
     */
    public PresenceType getPresenceType(Presence packet) {
        Presence.Type ptype = packet.getType();
        Presence.Show stype = packet.getShow();

        if (stype == Presence.Show.chat) {
            return PresenceType.chat;
        }
        else if (stype == Presence.Show.away) {
            return PresenceType.away;
        }
        else if (stype == Presence.Show.xa) {
            return PresenceType.xa;
        }
        else if (stype == Presence.Show.dnd) {
            return PresenceType.dnd;
        }
        else if (ptype == Presence.Type.unavailable) {
            return PresenceType.unavailable;
        }
        else if (packet.isAvailable()) {
            return PresenceType.available;
        }
        else {
            return PresenceType.unknown;
        }
    }

    /**
     * Handles startup of the transport.
     */
    public void start() {
        RosterEventDispatcher.addListener(this);
        // Probe all registered users [if they are logged in] to auto-log them in
        for (Registration registration : registrationManager.getRegistrations()) {
            if (SessionManager.getInstance().getSessionCount(registration.getJID().getNode()) > 0) {
                Presence p = new Presence(Presence.Type.probe);
                p.setFrom(this.getJID());
                p.setTo(registration.getJID());
                sendPacket(p);
            }
        }
    }

    /**
     * Handles shutdown of the transport.
     *
     * Cleans up all active sessions.
     */
    public void shutdown() {
        RosterEventDispatcher.removeListener(this);
        // Disconnect everyone's session
        for (TransportSession session : sessionManager.getSessions()) {
            registrationLoggedOut(session);
        }
    }

    /**
     * Returns the jid of the transport.
     */
    public JID getJID() {
        return this.jid;
    }

    /**
     * Returns the name (type) of the transport.
     */
    public String getName() {
        return transportType.toString();
    }

    /**
     * Returns the description of the transport.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the component manager of the transport.
     */
    public ComponentManager getComponentManager() {
        return componentManager;
    }

    /**
     * Returns the registration manager of the transport.
     */
    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    /**
     * Returns the session manager of the transport.
     */
    public TransportSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Retains the version string for later requests.
     */
    private String versionString = null;

    /**
     * Returns the version string of the gateway.
     */
    public String getVersionString() {
        if (versionString == null) {
            PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
            versionString = pluginManager.getVersion(pluginManager.getPlugin("gateway"));
        }
        return versionString;
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param groups List of group the item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, ArrayList<String> groups) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            try {
                RosterItem gwitem = roster.getRosterItem(contactjid);
                Boolean changed = false;
                if (gwitem.getSubStatus() != RosterItem.SUB_BOTH) {
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    changed = true;
                }
                if (gwitem.getAskStatus() != RosterItem.ASK_NONE) {
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
                    changed = true;
                }
                if (nickname != null && !gwitem.getNickname().equals(nickname)) {
                    gwitem.setNickname(nickname);
                    changed = true;
                }
                List<String> curgroups = gwitem.getGroups();
                if (curgroups != groups) {
                    try {
                        gwitem.setGroups(groups);
                        changed = true;
                    }
                    catch (Exception ee) {
                        // Oooookay, ignore then.
                    }
                }
                if (changed) {
                    roster.updateRosterItem(gwitem);
                }
            }
            catch (UserNotFoundException e) {
                try {
                    // Create new roster item for the gateway service or legacy contact. Only
                    // roster items related to the gateway service will be persistent. Roster
                    // items of legacy users are never persisted in the DB.
                    RosterItem gwitem =
                            roster.createRosterItem(contactjid, true, contactjid.getNode() == null);
                    gwitem.setSubStatus(RosterItem.SUB_BOTH);
                    gwitem.setAskStatus(RosterItem.ASK_NONE);
                    gwitem.setNickname(nickname);
                    try {
                        gwitem.setGroups(groups);
                    }
                    catch (Exception ee) {
                        // Oooookay, ignore then.
                    }
                    roster.updateRosterItem(gwitem);
                }
                catch (UserAlreadyExistsException ee) {
                    Log.error("getRosterItem claims user exists, but couldn't find via getRosterItem?");
                    // TODO: Should we throw exception or something?
                }
                catch (Exception ee) {
                    Log.error("createRosterItem caused exception: " + ee.getMessage());
                    // TODO: Should we throw exception or something?
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Either updates or adds a JID to a user's roster.
     *
     * Tries to only edit the roster if it has to.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactjid JID to add to roster.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, JID contactjid, String nickname, String group) throws UserNotFoundException {
        ArrayList<String> groups = null;
        if (group != null) {
            groups = new ArrayList<String>();
            groups.add(group);
        }
        addOrUpdateRosterItem(userjid, contactjid, nickname, groups);
    }

    /**
     * Either updates or adds a contact to a user's roster.
     *
     * @param userjid JID of user to have item added to their roster.
     * @param contactid String contact name, will be translated to JID.
     * @param nickname Nickname of item. (can be null)
     * @param group Group item is to be placed in. (can be null)
     * @throws UserNotFoundException if userjid not found.
     */
    public void addOrUpdateRosterItem(JID userjid, String contactid, String nickname, String group) throws UserNotFoundException {
        try {
            addOrUpdateRosterItem(userjid, convertIDToJID(contactid), nickname, group);
        }
        catch (UserNotFoundException e) {
            // Pass it on down.
            throw e;
        }
    }

    /**
     * Removes a roster item from a user's roster.
     *
     * @param userjid JID of user whose roster we will interact with.
     * @param contactjid JID to be removed from roster.
     * @throws UserNotFoundException if userjid not found.
     */
    void removeFromRoster(JID userjid, JID contactjid) throws UserNotFoundException {
        // Clean up the user's contact list.
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().toBareJID().equals(contactjid.toBareJID())) {
                    try {
                        roster.deleteRosterItem(ri.getJid(), false);
                    }
                    catch (Exception e) {
                        Log.error("Error removing roster item: " + ri.toString());
                        // TODO: Should we say something?
                    }
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Removes a roster item from a user's roster based on a legacy contact.
     *
     * @param userjid JID of user whose roster we will interact with.
     * @param contactid Contact to be removed, will be translated to JID.
     * @throws UserNotFoundException if userjid not found.
     */
    void removeFromRoster(JID userjid, String contactid) throws UserNotFoundException {
        // Clean up the user's contact list.
        try {
            removeFromRoster(userjid, convertIDToJID(contactid));
        }
        catch (UserNotFoundException e) {
            // Pass it on through.
            throw e;
        }
    }

    /**
     * Sync a user's roster with their legacy contact list.
     *
     * Given a collection of transport buddies, syncs up the user's
     * roster by fixing any nicknames, group assignments, adding and removing
     * roster items, and generally trying to make the jabber roster list
     * assigned to the transport's JID look at much like the legacy buddy
     * list as possible.  This is a very extensive operation.  You do not
     * want to do this very often.  Typically once right after the person
     * has logged into the legacy service.
     *
     * @param userjid JID of user who's roster we are syncing with.
     * @param legacyitems List of TransportBuddy's to be synced.
     * @throws UserNotFoundException if userjid not found.
     */
    public void syncLegacyRoster(JID userjid, List<TransportBuddy> legacyitems) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(userjid.getNode());

            // First thing first, we want to build ourselves an easy mapping.
            Map<JID,TransportBuddy> legacymap = new HashMap<JID,TransportBuddy>();
            for (TransportBuddy buddy : legacyitems) {
                //Log.debug("ROSTERSYNC: Mapping "+buddy.getName());
                legacymap.put(convertIDToJID(buddy.getName()), buddy);
            }

            // Now, lets go through the roster and see what matches up.
            for (RosterItem ri : roster.getRosterItems()) {
                if (!ri.getJid().getDomain().equals(this.jid.getDomain())) {
                    // Not our contact to care about.
                    continue;
                }
                if (ri.getJid().getNode() == null) {
                    // This is a transport instance, lets leave it alone.
                    continue;
                }
                JID jid = new JID(ri.getJid().toBareJID());
                if (legacymap.containsKey(jid)) {
                    //Log.debug("ROSTERSYNC: We found, updating " + jid.toString());
                    // Ok, matched a legacy to jabber roster item
                    // Lets update if there are differences
                    TransportBuddy buddy = legacymap.get(jid);
                    try {
                        this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                    }
                    catch (UserNotFoundException e) {
                        // TODO: Something is quite wrong if we see this.
                        Log.error("Failed updating roster item");
                    }
                    legacymap.remove(jid);
                }
                else {
                    //Log.debug("ROSTERSYNC: We did not find, removing " + jid.toString());
                    // This person is apparantly no longer in the legacy roster.
                    try {
                        this.removeFromRoster(userjid, jid);
                    }
                    catch (UserNotFoundException e) {
                        // TODO: Something is quite wrong if we see this.
                        Log.error("Failed removing roster item");
                    }
                }
            }

            // Ok, we should now have only new items from the legacy roster
            for (TransportBuddy buddy : legacymap.values()) {
                //Log.debug("ROSTERSYNC: We have new, adding " + buddy.getName());
                try {
                    this.addOrUpdateRosterItem(userjid, buddy.getName(), buddy.getNickname(), buddy.getGroup());
                }
                catch (UserNotFoundException e) {
                    // TODO: Something is quite wrong if we see this.
                    Log.error("Failed adding new roster item");
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Could not find roster for " + userjid.toString());
        }
    }

    /**
     * Adds a registration with this transport.
     *
     * @param jid JID of user to add registration to.
     * @param username Legacy username of registration.
     * @param password Legacy password of registration.
     * @throws UserNotFoundException if registration or roster not found.
     */
    public void addNewRegistration(JID jid, String username, String password) throws UserNotFoundException {
        Collection<Registration> registrations = registrationManager.getRegistrations(jid, this.transportType);
        Boolean foundReg = false;
        for (Registration registration : registrations) {
            if (!registration.getUsername().equals(username)) {
                registrationManager.deleteRegistration(registration);
            }
            else {
                registration.setPassword(password);
                foundReg = true;
            }
        }

        if (!foundReg) {
            registrationManager.createRegistration(jid, this.transportType, username, password);
        }


        // Clean up any leftover roster items from other transports.
        try {
            cleanUpRoster(jid, true);
        }
        catch (UserNotFoundException ee) {
            throw new UserNotFoundException("Unable to find roster.");
        }
            
        try {
            addOrUpdateRosterItem(jid, this.getJID(), this.getDescription(), "Transports");
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("User not registered with server.");
        }

    }

    /**
     * Removes a registration from this transport.
     *
     * @param jid JID of user to add registration to.
     * @throws UserNotFoundException if registration or roster not found.
     */
    public void deleteRegistration(JID jid) throws UserNotFoundException {
        Collection<Registration> registrations = registrationManager.getRegistrations(jid, this.transportType);
        // For now, we're going to have to just nuke all of these.  Sorry.
        for (Registration reg : registrations) {
            registrationManager.deleteRegistration(reg);
        }

        // Clean up the user's contact list.
        try {
            cleanUpRoster(jid, false);
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }

    /**
     * Cleans a roster of entries related to this transport.
     *
     * This function will run through the roster of the specified user and clean up any
     * entries that share the domain of this transport.  This is primarily used during registration
     * to clean up leftovers from other transports.
     *
     * @param jid JID of user whose roster we want to clean up.
     * @param leaveDomain If set, we do not touch the roster item associated with the domain itself.
     * @throws UserNotFoundException if the user is not found.
     */
    public void cleanUpRoster(JID jid, Boolean leaveDomain) throws UserNotFoundException {
        try {
            Roster roster = rosterManager.getRoster(jid.getNode());
            for (RosterItem ri : roster.getRosterItems()) {
                if (ri.getJid().getDomain().equals(this.jid.getDomain())) {
                    if (ri.isShared()) {
                        continue;
                    }
                    if (leaveDomain && ri.getJid().getNode() == null) {
                        continue;
                    }
                    try {
                        Log.debug("Cleaning up roster entry " + ri.getJid().toString());
                        roster.deleteRosterItem(ri.getJid(), false);
                    }
                    catch (Exception e) {
                        Log.error("Error removing roster item: " + ri.toString());
                    }
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UserNotFoundException("Unable to find roster.");
        }
    }

    /**
     * Sends a packet through the component manager as the component.
     *
     * @param packet Packet to be sent.
     */
    public void sendPacket(Packet packet) {
        try {
            this.componentManager.sendPacket(this, packet);
        }
        catch (ComponentException e) {
            Log.error("Failed to deliver packet: " + packet.toString());
        }
    }

    /**
     * Intercepts roster additions related to the gateway and flags them as non-persistent.
     *
     * @see org.jivesoftware.wildfire.roster.RosterEventListener#addingContact(org.jivesoftware.wildfire.roster.Roster, org.jivesoftware.wildfire.roster.RosterItem, boolean)
     */
    public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        if (item.getJid().getDomain().equals(this.getJID()) && item.getJid().getNode() != null) {
            return false;
        }
        return persistent;
    }

    /**
     * Handles updates to a roster item that are not normally forwarded to the transport.
     *
     * @see org.jivesoftware.wildfire.roster.RosterEventListener#contactUpdated(org.jivesoftware.wildfire.roster.Roster, org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void contactUpdated(Roster roster, RosterItem item) {
        // TODO: do nothing for now
    }

    /**
     * Handles additions to a roster.  We don't really care because we hear about these via subscribes.
     *
     * @see org.jivesoftware.wildfire.roster.RosterEventListener#contactAdded(org.jivesoftware.wildfire.roster.Roster, org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void contactAdded(Roster roster, RosterItem item) {
        // Don't care
        // TODO: Evaluate how we -could- use this.. like what if roster is edited not via xmpp client?
    }

    /**
     * Handles deletions from a roster.  We don't really care because we hear about these via unsubscribes.
     *
     * @see org.jivesoftware.wildfire.roster.RosterEventListener#contactDeleted(org.jivesoftware.wildfire.roster.Roster, org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void contactDeleted(Roster roster, RosterItem item) {
        // Don't care
        // TODO: Evaluate how we -could- use this.. like what if roster is edited not via xmpp client?
    }

    /**
     * Handles notifications of a roster being loaded.  Not sure we care.
     *
     * @see org.jivesoftware.wildfire.roster.RosterEventListener#rosterLoaded(org.jivesoftware.wildfire.roster.Roster)
     */
    public void rosterLoaded(Roster roster) {
        // Don't care
        // TODO: Evaluate if we could use this.
    }

    /**
     * Will handle logging in to the legacy service.
     *
     * @param registration Registration used for log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     * @return A session instance for the new login.
     */
    public abstract TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority);

    /**
     * Will handle logging out of the legacy service.
     *
     * @param session TransportSession to be logged out.
     */
    public abstract void registrationLoggedOut(TransportSession session);

}
