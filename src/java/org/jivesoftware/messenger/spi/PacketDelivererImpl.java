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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.net.SocketPacketWriteHandler;
import javax.xml.stream.XMLStreamException;

/**
 * In-memory implementation of the packet deliverer service
 *
 * @author Iain Shigeoka
 */
public class PacketDelivererImpl extends BasicModule implements PacketDeliverer {

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    protected ChannelHandler deliverHandler;

    public OfflineMessageStrategy messageStrategy;
    public SessionManager sessionManager;

    public PacketDelivererImpl() {
        super("Packet Delivery");
    }

    public void deliver(XMPPPacket packet) throws UnauthorizedException, PacketException, XMLStreamException {
        if (packet == null) {
            throw new PacketException("Packet was null");
        }
        if (deliverHandler == null) {
            throw new XMLStreamException("Could not send packet - no route" + packet.toString());
        }
        deliverHandler.process(packet);
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        trackInfo.getTrackerClasses().put(OfflineMessageStrategy.class, "messageStrategy");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (sessionManager != null && messageStrategy != null) {
            deliverHandler = new SocketPacketWriteHandler(sessionManager, messageStrategy);
        }
    }

    public void serviceRemoved(Object service) {
        if (sessionManager == null || messageStrategy == null) {
            deliverHandler = null;
        }
    }
}
