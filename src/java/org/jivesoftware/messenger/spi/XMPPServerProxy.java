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

import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.XMPPServerInfo;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Standard security wrapper for the XMPPServer class. This class also
 * allows a simple mechanism for wrapping all server resources using
 * a single security and resource control context without having to
 * impose proxies for each resource.
 *
 * @author Iain Shigeoka
 */
public class XMPPServerProxy implements XMPPServer {

    private XMPPServer server;
    private AuthToken authToken;
    private Permissions permissions;


    /**
     * <p>Create a new server proxy.</p>
     *
     * @param server     The server to proxy
     * @param auth       The auth for the server
     * @param permission The permissions for the server
     */
    public XMPPServerProxy(XMPPServer server, AuthToken auth, Permissions permission) {
        this.server = server;
        this.authToken = auth;
        this.permissions = permission;
    }

    public XMPPServerInfo getServerInfo() {
        return server.getServerInfo();
    }

    public boolean isLocal(XMPPAddress jid) {
        return server.isLocal(jid);
    }

    public XMPPAddress createJID(String username, String resource) {
        return server.createJID(username, resource);
    }

    public Session getSession() throws UnauthorizedException {
        return server.getSession();
    }

    public String getName() {
        return server.getName();
    }

    public void initialize(Container container) {
        server.initialize(container);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public void destroy() {
        server.destroy();
    }

}
