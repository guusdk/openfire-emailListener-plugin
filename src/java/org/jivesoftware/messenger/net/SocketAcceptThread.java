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
package org.jivesoftware.messenger.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.ConnectionManager;
import org.jivesoftware.messenger.JiveGlobals;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Implements a network front end with a dedicated thread reading
 * each incoming socket.
 *
 * @author Iain Shigeoka
 */
public class SocketAcceptThread extends Thread {

    /**
     * The default Jabber socket.
     */
    public static final int DEFAULT_PORT = 5222;

    /**
     * The port for this server socket.
     */
    private int port;

    /**
     * Interface to bind to.
     */
    private InetAddress bindInterface;

    /**
     * True while this thread should continue running.
     */
    private boolean notTerminated = true;

    /**
     * socket that listens for connections.
     */
    ServerSocket serverSocket;

    private ConnectionManager connManager;

    public SocketAcceptThread(ConnectionManager connManager) {
        super("SAT accept");
        this.connManager = connManager;
        port = SocketAcceptThread.DEFAULT_PORT;
        String portName = JiveGlobals.getJiveProperty("xmpp.socket.plain.port");
        if (portName != null) {
            port = Integer.parseInt(portName);
        }
        String interfaceName = JiveGlobals.getJiveProperty("xmpp.socket.plain.interface");
        bindInterface = null;
        if (interfaceName != null) {
            try {
                if (interfaceName.trim().length() > 0) {
                    bindInterface = InetAddress.getByName(interfaceName);
                }
            }
            catch (UnknownHostException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Retrieve the port this server socket is bound to.
     *
     * @return the port the socket is bound to.
     */
    public int getPort() {
        return port;
    }

    /**
     * Unblock the thread and force it to terminate.
     */
    public void shutdown() {
        notTerminated = false;

        try {
            ServerSocket sSock = serverSocket;
            serverSocket = null;
            if (sSock != null) {
                sSock.close();
            }
        }
        catch (IOException e) {
            // we don't care, no matter what, the socket should be dead
        }

    }

    /**
     * About as simple as it gets.  The thread spins around an accept
     * call getting sockets and handing them to the SocketManager.
     */
    public void run() {
        try {
            serverSocket = new ServerSocket(port, -1, bindInterface);
            while (notTerminated) {
                try {
                    Socket sock = serverSocket.accept();
                    if (sock != null) {
                        Log.info("Connect " + sock.toString());
                        connManager.addSocket(sock, false);
                    }
                }
                catch (IOException ie) {
                    if (notTerminated) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error.accept"),
                                ie);
                    }
                }
                catch (Exception e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error.accept"), e);
                }
            }
        }
        catch (IOException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
        }
        try {
            ServerSocket sSock = serverSocket;
            serverSocket = null;
            if (sSock != null) {
                sSock.close();
            }
        }
        catch (IOException e) {
            // we don't care, no matter what, the socket should be dead
        }
    }
}
