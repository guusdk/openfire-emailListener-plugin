/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.IQHandlerInfo;
import org.jivesoftware.messenger.PacketException;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.spi.IQImpl;
import java.util.ArrayList;
import java.util.Iterator;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * Implements the TYPE_IQ jabber:iq:version protocol (version info). Allows
 * Jabber entities to query each other's application versions.  The server
 * will respond with its current version info.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to itself.
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
 * @todo Verify responding to an iq 'get'
 */
public class IQVersionHandler extends IQHandler implements ServerFeaturesProvider {

    private static Element bodyElement;
    private static Element versionElement;
    private IQHandlerInfo info;

    public IQVersionHandler() {
        super("XMPP Server Version Handler");
        info = new IQHandlerInfo("query", "jabber:iq:version", IQImpl.class);
        if (bodyElement == null) {
            bodyElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:version"));
            bodyElement.addElement("name").setText("Jive Messenger");
            bodyElement.addElement("os").setText("JDK 1.4.x");
            versionElement = bodyElement.addElement("version");
        }
    }

    public IQ handleIQ(IQ packet) throws PacketException {
        // Could cache this information for every server we see
        versionElement.setText(localServer.getServerInfo().getVersion().getVersionString());
        IQ result = null;
        result = packet.createResult(bodyElement);
        return result;
    }

    public XMPPServer localServer;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:version");
        return features.iterator();
    }
}
