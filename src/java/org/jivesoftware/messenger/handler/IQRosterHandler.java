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

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.messenger.user.Roster;
import org.jivesoftware.messenger.user.spi.IQRosterItemImpl;
import org.xmpp.packet.*;
import org.dom4j.Element;
import org.xmlpull.v1.XmlPullParserException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Implements the TYPE_IQ jabber:iq:roster protocol. Clients
 * use this protocol to retrieve, update, and rosterMonitor roster
 * entries (buddy lists). The server manages the basics of
 * roster subscriptions and roster updates based on presence
 * and iq:roster packets, while the client maintains the user
 * interface aspects of rosters such as organizing roster
 * entries into groups.
 * <p/>
 * A 'get' query retrieves a snapshot of the roster.
 * A 'set' query updates the roster (typically with new group info).
 * The server sends 'set' updates asynchronously when roster
 * entries change status.
 * <p/>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQRosterHandler extends IQHandler implements ServerFeaturesProvider {

    private IQHandlerInfo info;

    public IQRosterHandler() {
        super("XMPP Roster Handler");
        info = new IQHandlerInfo("query", "jabber:iq:roster");
    }

    /**
     * Handles all roster queries.
     * There are two major types of queries:
     * <ul>
     * <li>Roster remove - A forced removal of items from a roster. Roster
     * removals are the only roster queries allowed to
     * directly affect the roster from another user.</li>
     * <li>Roster management - A local user looking up or updating their
     * roster.</li>
     * </ul>
     *
     * @param packet The update packet
     * @return The reply or null if no reply
     */
    public synchronized IQ handleIQ(IQ packet) throws
            UnauthorizedException, PacketException {
        try {
            IQ returnPacket = null;
            IQRoster roster = (IQRoster)packet;

            JID recipientJID = packet.getTo();

            // The packet is bound for the server and must be roster management
            if (recipientJID == null || recipientJID.getNode() == null) {
                returnPacket = manageRoster(roster);
            }
            else { // The packet must be a roster removal from a foreign domain user
                removeRosterItem(roster);
            }
            return returnPacket;
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return null;
    }

    /**
     * Remove a roster item. At this stage, this is recipient who has received
     * a roster update. We must check that it is a removal, and if so, remove
     * the roster item based on the sender's id rather than what is in the item
     * listing itself.
     *
     * @param packet The packet suspected of containing a roster removal
     */
    private void removeRosterItem(IQRoster packet) throws UnauthorizedException, XmlPullParserException {
        JID recipientJID = packet.getTo();
        JID senderJID = packet.getFrom();
        try {
            Iterator itemIter = packet.getRosterItems();
            while (itemIter.hasNext()) {
                RosterItem packetItem = (RosterItem)itemIter.next();
                if (packetItem.getSubStatus() == RosterItem.SUB_REMOVE) {
                    Roster roster = userManager.getUser(recipientJID.getNode()).getRoster();
                    RosterItem item = roster.getRosterItem(senderJID);
                    roster.deleteRosterItem(senderJID);
                    item.setSubStatus(RosterItem.SUB_REMOVE);
                    item.setSubStatus(RosterItem.SUB_NONE);

                    Packet itemPacket = packet.createCopy();
                    sessionManager.userBroadcast(recipientJID.getNode(), itemPacket);
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }
    }

    /**
     * The packet is a typical 'set' or 'get' update targeted at the server.
     * Notice that the set could be a roster removal in which case we have to
     * generate a local roster removal update as well as a new roster removal
     * to send to the the roster item's owner.
     *
     * @param packet The packet that triggered this update
     * @return Either a response to the roster update or null if the packet is corrupt and the session was closed down
     */
    private IQ manageRoster(IQRoster packet) throws UnauthorizedException, UserAlreadyExistsException, XmlPullParserException {

        IQ returnPacket = null;
        Session session = null;
        try {
            SessionManager.getInstance().getSession(packet.getFrom());
        }
        catch (Exception e) {
            IQ error = IQ.createResultIQ(packet);
            error.setError(PacketError.Condition.internal_server_error);
            return error;
        }

        IQ.Type type = packet.getType();

        try {
            User sessionUser = userManager.getUser(session.getUsername());
            CachedRoster cachedRoster = (CachedRoster)sessionUser.getRoster();
            if (IQ.Type.get == type) {
                returnPacket = cachedRoster.getReset();
                returnPacket.setType(IQ.Type.result);
                returnPacket.setTo(session.getAddress());
                returnPacket.setID(packet.getID());
                // Force delivery of the response because we need to trigger
                // a presence probe from all contacts
                deliverer.deliver(returnPacket);
                returnPacket = null;
            }
            else if (IQ.Type.set == type) {

                Iterator itemIter = packet.getRosterItems();
                while (itemIter.hasNext()) {
                    RosterItem item = (RosterItem)itemIter.next();
                    if (item.getSubStatus() == RosterItem.SUB_REMOVE) {
                        removeItem(cachedRoster, packet.getFrom(), item);
                    }
                    else {
                        if (cachedRoster.isRosterItem(item.getJid())) {
                            // existing item
                            CachedRosterItem cachedItem = (CachedRosterItem)cachedRoster.getRosterItem(item.getJid());
                            cachedItem.setAsCopyOf(item);
                            cachedRoster.updateRosterItem(cachedItem);
                        }
                        else {
                            // new item
                            cachedRoster.createRosterItem(item);
                        }
                    }
                }
                returnPacket = IQ.createResultIQ(packet);
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }

        return returnPacket;

    }

    /**
     * Remove the roster item from the sender's roster (and possibly the recipient's).
     * Actual roster removal is done in the removeItem(Roster,RosterItem) method.
     *
     * @param roster The sender's roster.
     * @param sender The JID of the sender of the removal request
     * @param item   The removal item element
     */
    private void removeItem(Roster roster, JID sender, RosterItem item)
            throws UnauthorizedException
    {

        JID recipient = item.getJid();
        // Remove recipient from the sender's roster
        roster.deleteRosterItem(item.getJid());
        // Forward set packet to the subscriber
        if (localServer.isLocal(recipient)) { // Recipient is local so let's handle it here
            try {
                CachedRoster recipientRoster = userManager.getUser(recipient.getNode()).getRoster();
                recipientRoster.deleteRosterItem(sender);
            }
            catch (UserNotFoundException e) {
            }
        }
        else { // Recipient is remote so we just forward the packet to them

            Packet removePacket = createRemoveForward(sender, recipient);
            router.route(removePacket);
        }
    }

    /**
     * Creates a forwarded removal packet.
     *
     * @param from The sender address to use
     * @param to   The recipient address to use
     * @return The forwarded packet generated
     */
    private Packet createRemoveForward(JID from, JID to) throws UnauthorizedException {

        IQ response = new IQ();
        response.setFrom(from);
        response.setTo(to);
        response.setType(IQ.Type.set);

        Element query = response.setChildElement("query", "jabber:iq:roster");

        IQRosterItem responseItem = new IQRosterItemImpl(from);
        responseItem.setSubStatus(RosterItem.SUB_REMOVE);
        query.add(responseItem.asXMLElement());

        return response;
    }

    public UserManager userManager;
    public XMPPServer localServer;
    private SessionManager sessionManager = SessionManager.getInstance();
    public PresenceManager presenceManager;
    public PacketRouter router;
    public RoutingTable routingTable;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(PresenceManager.class, "presenceManager");
        trackInfo.getTrackerClasses().put(PacketRouter.class, "router");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:roster");
        return features.iterator();
    }
}