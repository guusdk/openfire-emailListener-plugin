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

package org.jivesoftware.messenger.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * This ChannelHandler writes packet data to connections.
 *
 * @author Iain Shigeoka
 * @see PacketRouter
 */
public class SocketPacketWriteHandler implements ChannelHandler {

    private SessionManager sessionManager;
    private OfflineMessageStrategy messageStrategy;

    public SocketPacketWriteHandler(SessionManager sessionManager, OfflineMessageStrategy messageStrategy) {
        this.sessionManager = sessionManager;
        this.messageStrategy = messageStrategy;
    }

     public void process(Packet packet) throws UnauthorizedException, PacketException {
        try {
            JID recipient = packet.getTo();
            Session senderSession = sessionManager.getSession(packet.getFrom());
            if (recipient == null || (recipient.getNode() == null && recipient.getResource() == null)) {
                if (senderSession != null && !senderSession.getConnection().isClosed()) {
                    senderSession.getConnection().deliver(packet);
                }
                else {
                    dropPacket(packet);
                }
            }
            else {
                Session session = sessionManager.getBestRoute(recipient);
                if (session == null) {
                    if (packet instanceof Message) {
                        messageStrategy.storeOffline((Message)packet);
                    }
                    else if (packet instanceof Presence) {
                        // presence packets are dropped silently
                        //dropPacket(packet);
                    }
                    else {
                        // IQ packets are logged but dropped
                        dropPacket(packet);
                    }
                }
                else {
                    try {
                        session.getConnection().deliver(packet);
                    }
                    catch (Exception e) {
                        // do nothing
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.deliver") + "\n" + packet.toString(), e);
        }
    }

    /**
     * Drop the packet.
     *
     * @param packet The packet being dropped
     */
    private void dropPacket(Packet packet) {
        Log.warn(LocaleUtils.getLocalizedString("admin.error.routing") + "\n" + packet.toString());
    }
}
