/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.group.GroupManager;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Implements the TYPE_IQ jabber:iq:register protocol (plain only). Clients
 * use this protocol to register a user account with the server.
 * A 'get' query runs a register probe to obtain the fields needed
 * for registration. Return the registration form.
 * A 'set' query attempts to create a new user account
 * with information given in the registration form.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>

 * <h2>Compatibility</h2>
 * The current behavior is designed to emulate jabberd1.4. However
 * this behavior differs significantly from JEP-0078 (non-SASL registration).
 * In particular, authentication (IQ-Auth) must return an error when a user
 * request is made to an account that doesn't exist to trigger auto-registration
 * (JEP-0078 explicitly recommends against this practice to prevent hackers
 * from probing for legitimate accounts).
 *
 * @author Iain Shigeoka
 */
public class IQRegisterHandler extends IQHandler implements ServerFeaturesProvider {

    private static boolean enabled;
    private static Element probeResult;

    private UserManager userManager;
    private RosterManager rosterManager;
    private SessionManager sessionManager;

    private IQHandlerInfo info;
    private Map delegates = new HashMap();

    /**
     * <p>Basic constructor does nothing.</p>
     */
    public IQRegisterHandler() {
        super("XMPP Registration Handler");
        info = new IQHandlerInfo("query", "jabber:iq:register");
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();
        sessionManager = server.getSessionManager();

        if (probeResult == null) {
            // Create the basic element of the probeResult which contains the basic registration
            // information (e.g. username, passoword and email)
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));
            probeResult.addElement("username");
            probeResult.addElement("password");
            probeResult.addElement("email");
            probeResult.addElement("name");

            // Create the registration form to include in the probeResult. The form will include
            // the basic information plus name and visibility of name and email.
            // TODO Future versions could allow plugin modules to add new fields to the form 
            XDataFormImpl registrationForm = new XDataFormImpl(DataForm.TYPE_FORM);
            registrationForm.setTitle("XMPP Client Registration");
            registrationForm.addInstruction("Please provide the following information");

            XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
            field.setType(FormField.TYPE_HIDDEN);
            field.addValue("jabber:iq:register");
            registrationForm.addField(field);

            field = new XFormFieldImpl("username");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Username");
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("name");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Full name");
            registrationForm.addField(field);

            field = new XFormFieldImpl("email");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel("Email");
            registrationForm.addField(field);

            field = new XFormFieldImpl("password");
            field.setType(FormField.TYPE_TEXT_PRIVATE);
            field.setLabel("Password");
            field.setRequired(true);
            registrationForm.addField(field);

            // Add the registration form to the probe result.
            probeResult.add(registrationForm.asXMLElement());
        }
        // See if in-band registration should be enabled (default is true).
        enabled = JiveGlobals.getBooleanProperty("register.inband", true);
    }

    public IQ handleIQ(IQ packet) throws PacketException, UnauthorizedException {
        // Look for a delegate for this packet
        IQHandler delegate = getDelegate(packet.getTo());
        // We assume that the registration packet was meant to the server if delegate is
        // null
        if (delegate != null) {
            // Pass the packet to the real packet handler
            return delegate.handleIQ(packet); 
        }

        ClientSession session = sessionManager.getSession(packet.getFrom());
        IQ reply = null;
        // If inband registration is not allowed, return an error.
        if (!enabled) {
            reply = IQ.createResultIQ(packet);
            reply.setError(PacketError.Condition.forbidden);
        }
        else if (IQ.Type.get.equals(packet.getType())) {
            reply = IQ.createResultIQ(packet);
            if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                try {
                    User user = userManager.getUser(session.getUsername());
                    Element currentRegistration = probeResult.createCopy();
                    currentRegistration.addElement("registered");
                    currentRegistration.element("username").setText(user.getUsername());
                    currentRegistration.element("password").setText("");
                    currentRegistration.element("email").setText(user.getEmail());
                    currentRegistration.element("name").setText(user.getName());

                    Element form = currentRegistration.element(QName.get("x", "jabber:x:data"));
                    Iterator fields = form.elementIterator("field");
                    Element field;
                    while (fields.hasNext()) {
                        field = (Element) fields.next();
                        if ("username".equals(field.attributeValue("var"))) {
                            field.addElement("value").addText(user.getUsername());
                        }
                        else if ("name".equals(field.attributeValue("var"))) {
                            field.addElement("value").addText(user.getName());
                        }
                        else if ("email".equals(field.attributeValue("var"))) {
                            field.addElement("value").addText(user.getEmail());
                        }
                    }
                    reply.setChildElement(currentRegistration);
                }
                catch (UserNotFoundException e) {
                    reply.setChildElement(probeResult.createCopy());
                }
            }
            else {
                // This is a workaround. Since we don't want to have an incorrect TO attribute
                // value we need to clean up the TO attribute. The TO attribute will contain an
                // incorrect value since we are setting a fake JID until the user actually
                // authenticates with the server.
                reply.setTo((JID) null);
                reply.setChildElement(probeResult.createCopy());
            }
        }
        else if (IQ.Type.set.equals(packet.getType())) {
            try {
                Element iqElement = packet.getChildElement();
                if (iqElement.element("remove") != null) {
                    if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                        User user = userManager.getUser(session.getUsername());
                        // Delete the user
                        userManager.deleteUser(user);
                        // Delete the roster of the user
                        rosterManager.deleteRoster(session.getAddress());
                        // Delete the user from all the Groups
                        GroupManager.getInstance().deleteUser(user);

                        reply = IQ.createResultIQ(packet);
                        session.getConnection().deliver(reply);
                        // Close the user's connection
                        session.getConnection().close();
                        // The reply has been sent so clean up the variable
                        reply = null;
                    }
                    else {
                        throw new UnauthorizedException();
                    }
                }
                else {
                    String username = null;
                    String password = null;
                    String email = null;
                    String name = null;
                    User newUser = null;
                    XDataFormImpl registrationForm = null;
                    FormField field;

                    Element formElement = iqElement.element("x");
                    // Check if a form was used to provide the registration info
                    if (formElement != null) {
                        // Get the sent form
                        registrationForm = new XDataFormImpl();
                        registrationForm.parse(formElement);
                        // Get the username sent in the form
                        Iterator<String> values = registrationForm.getField("username").getValues();
                        username = (values.hasNext() ? values.next() : " ");
                        // Get the password sent in the form
                        field = registrationForm.getField("password");
                        if (field != null) {
                            values = field.getValues();
                            password = (values.hasNext() ? values.next() : " ");
                        }
                        // Get the email sent in the form
                        field = registrationForm.getField("email");
                        if (field != null) {
                            values = field.getValues();
                            email = (values.hasNext() ? values.next() : " ");
                        }
                        // Get the name sent in the form
                        field = registrationForm.getField("name");
                        if (field != null) {
                            values = field.getValues();
                            name = (values.hasNext() ? values.next() : " ");
                        }
                    }
                    else {
                        // Get the registration info from the query elements
                        username = iqElement.elementText("username");
                        password = iqElement.elementText("password");
                        email = iqElement.elementText("email");
                        name = iqElement.elementText("name");
                    }
                    if (email == null || "".equals(email)) {
                        email = " ";
                    }

                    // Inform the entity of failed registration if some required information was
                    // not provided
                    if (password == null || password.trim().length() == 0) {
                        reply = IQ.createResultIQ(packet);
                        reply.setError(PacketError.Condition.not_acceptable);
                        return reply;
                    }

                    if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                        User user = userManager.getUser(session.getUsername());
                        if (user != null) {
                            if (user.getUsername().equalsIgnoreCase(username)) {
                                user.setPassword(password);
                                user.setEmail(email);
                                newUser = user;
                            }
                            else {
                                // An admin can create new accounts when logged in.
                                newUser = userManager.createUser(username, password, null, email);
                            }
                        }
                        else {
                            throw new UnauthorizedException();
                        }
                    }
                    else {
                        newUser = userManager.createUser(username, password, null, email);
                    }
                    // Set and save the extra user info (e.g. full name, etc.)
                    if (newUser != null && name != null) {
                        newUser.setName(name);
                    }

                    reply = IQ.createResultIQ(packet);
                }
            }
            catch (UserAlreadyExistsException e) {
                reply = IQ.createResultIQ(packet);
                reply.setError(PacketError.Condition.conflict);
            }
            catch (UserNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setError(PacketError.Condition.bad_request);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (reply != null) {
            // why is this done here instead of letting the iq handler do it?
            session.getConnection().deliver(reply);
        }
        return null;
    }

    public boolean isInbandRegEnabled() {
        return enabled;
    }

    public void setInbandRegEnabled(boolean allowed) {
        enabled = allowed;
        JiveGlobals.setProperty("register.inband", enabled ? "true" : "false");
    }
    
    private IQHandler getDelegate(JID recipientJID) {
        if (recipientJID == null) {
            return null;
        }
        return (IQHandler) delegates.get(recipientJID.getDomain());
    }
    
    public void addDelegate(String serviceName, IQHandler delegate) {
        // TODO As long as we only add delegates during server startup there is no need to make
        // the iv "delegates" thread-safe. In the future, when we implement the component JEP we 
        // must remove all this idea of the delegates since IQRouter will directly send the IQ 
        // packet to the correct IQHandler. In that time, this class will only be an
        // IQHandler that creates or removes user accounts and not a generic IQRegisterHandler.  
        delegates.put(serviceName, delegate);
    }

    public void removeDelegate(String serviceName) {
        delegates.remove(serviceName);
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:register");
        return features.iterator();
    }
}
