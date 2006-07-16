/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 *
 * Heavily inspired by joscardemo of the Joust Project: http://joust.kano.net/
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.util.Log;

import net.kano.joscar.flap.*;
import net.kano.joscar.flapcmd.*;
import net.kano.joscar.net.*;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.*;

import java.net.InetAddress;

public abstract class BaseFlapConnection extends ClientFlapConn {
    protected ClientSnacProcessor sp;
    OSCARSession oscarSession;

    public BaseFlapConnection(OSCARSession mainSession) {
        initBaseFlapConnection();
        oscarSession = mainSession;
    }

    public BaseFlapConnection(String host, int port, OSCARSession mainSession) {
        super(host, port); // Hand off to ClientFlapConn
        initBaseFlapConnection();
        oscarSession = mainSession;
    }

    public BaseFlapConnection(InetAddress ip, int port, OSCARSession mainSession) {
        super(ip, port); // Hand off to ClientFlapConn
        initBaseFlapConnection();
        oscarSession = mainSession;
    }

    private void initBaseFlapConnection() {
        FlapProcessor fp = getFlapProcessor();
        sp = new ClientSnacProcessor(fp);
        fp.setFlapCmdFactory(new DefaultFlapCmdFactory());

        sp.addPreprocessor(new FamilyVersionPreprocessor());
        sp.getCmdFactoryMgr().setDefaultFactoryList(new DefaultClientFactoryList());

        addConnListener(new ClientConnListener() {
            public void stateChanged(ClientConnEvent e) {
                handleStateChange(e);
            }
        });
        getFlapProcessor().addPacketListener(new FlapPacketListener() {
            public void handleFlapPacket(FlapPacketEvent e) {
                BaseFlapConnection.this.handleFlapPacket(e);
            }
        });
        getFlapProcessor().addExceptionHandler(new FlapExceptionHandler() {
            public void handleException(FlapExceptionEvent event) {
                Log.error(event.getType() + " FLAP ERROR: "
                        + event.getException().getMessage());
                // How do do this right?
                //Log.error(event.getException().printStackTrace());
            }
        });
        sp.addPacketListener(new SnacPacketListener() {
            public void handleSnacPacket(SnacPacketEvent e) {
                BaseFlapConnection.this.handleSnacPacket(e);
            }
        });
    }

    protected SnacRequestListener genericReqListener = new SnacRequestAdapter() {
        public void handleResponse(SnacResponseEvent e) {
            handleSnacResponse(e);
        }
    };

    public SnacRequestListener getGenericReqListener() {
        return genericReqListener;
    }

    public ClientSnacProcessor getSnacProcessor() {
        return sp;
    }

    public OSCARSession getMainSession() { return oscarSession; }

    void sendRequest(SnacRequest req) {
        if (!req.hasListeners()) req.addListener(genericReqListener);
        sp.sendSnac(req);
    }

    SnacRequest request(SnacCommand cmd) {
        return request(cmd, null);
    }

    SnacRequest request(SnacCommand cmd, SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        sendRequest(req);
        return req;
    }

    protected abstract void handleStateChange(ClientConnEvent e);
    protected abstract void handleFlapPacket(FlapPacketEvent e);
    protected abstract void handleSnacPacket(SnacPacketEvent e);
    protected abstract void handleSnacResponse(SnacResponseEvent e);
}
