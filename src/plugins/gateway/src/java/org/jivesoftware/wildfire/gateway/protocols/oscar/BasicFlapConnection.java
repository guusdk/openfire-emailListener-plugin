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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.*;

import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;
import net.kano.joscar.ByteBlock;
import net.kano.joscar.OscarTools;
import net.kano.joscar.BinaryTools;
import net.kano.joscar.flap.FlapCommand;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snac.SnacRequest;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.flapcmd.LoginFlapCmd;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.rvcmd.DefaultRvCommandFactory;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.icbm.RecvImIcbm;
import net.kano.joscar.snaccmd.icbm.InstantMessage;
import net.kano.joscar.snaccmd.buddy.BuddyStatusCmd;
import net.kano.joscar.snaccmd.buddy.BuddyOfflineCmd;
import net.kano.joscar.rv.*;
import net.kano.joscar.ratelim.RateLimitingQueueMgr;

/**
 * Handles incoming FLAP packets.
 *
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public abstract class BasicFlapConnection extends BaseFlapConnection {
    protected final ByteBlock cookie;
    protected boolean sentClientReady = false;

    public Map<String,FullUserInfo> buddystore = new HashMap<String, FullUserInfo>();

    protected int[] snacFamilies = null;
    protected Collection<SnacFamilyInfo> snacFamilyInfos;
    protected RateLimitingQueueMgr rateMgr = new RateLimitingQueueMgr();
    protected RvProcessor rvProcessor = new RvProcessor(sp);
    protected RvProcessorListener rvListener = new RvProcessorListener() {
        public void handleNewSession(NewRvSessionEvent event) {
            event.getSession().addListener(rvSessionListener);
        }
    };

    protected RvSessionListener rvSessionListener = new RvSessionListener() {
        public void handleRv(RecvRvEvent event) {
//            RvCommand cmd = event.getRvCommand();
//
//            RvSession session = event.getRvSession();
//            SnacCommand snaccmd = event.getSnacCommand();
//            if (!(snaccmd instanceof RecvRvIcbm)) return;
//            RecvRvIcbm icbm = (RecvRvIcbm) snaccmd;
            //Log.debug("got rendezvous on session <" + session + ">");
            //Log.debug("- command: " + cmd);
        }
        public void handleSnacResponse(RvSnacResponseEvent event) {
            //Log.debug("got SNAC response for <"
            //        + event.getRvSession() + ">: "
            //        + event.getSnacCommand());
        }
    };

    { // init
        sp.setSnacQueueManager(rateMgr);
        rvProcessor.registerRvCmdFactory(new DefaultRvCommandFactory());
        rvProcessor.addListener(rvListener);
    }

    public BasicFlapConnection(String host, int port, OSCARSession mainSession, ByteBlock cookie) {
        super(host, port, mainSession);
        this.cookie = cookie;
    }

    public BasicFlapConnection(InetAddress ip, int port, OSCARSession mainSession,
            ByteBlock cookie) {
        super(ip, port, mainSession);
        this.cookie = cookie;
    }

    protected DateFormat dateFormat
            = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT);

    protected void handleFlapPacket(FlapPacketEvent e) {
        FlapCommand cmd = e.getFlapCommand();

        if (cmd instanceof LoginFlapCmd) {
            getFlapProcessor().sendFlap(new LoginFlapCmd(cookie));
        } else {
            //Log.debug("got FLAP command on channel 0x"
            //        + Integer.toHexString(e.getFlapPacket().getChannel())
            //        + ": " + cmd);
        }
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
//        SnacPacket packet = e.getSnacPacket();
        //Log.debug("got snac packet type "
        //        + Integer.toHexString(packet.getFamily()) + "/"
        //        + Integer.toHexString(packet.getCommand()) + ": "
        //        + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();
        if (cmd instanceof ServerReadyCmd) {
            ServerReadyCmd src = (ServerReadyCmd) cmd;

            setSnacFamilies(src.getSnacFamilies());

            Collection<SnacFamilyInfo> familyInfos = SnacFamilyInfoFactory.getDefaultFamilyInfos(src.getSnacFamilies());

            setSnacFamilyInfos(familyInfos);

            oscarSession.registerSnacFamilies(this);

            request(new ClientVersionsCmd(familyInfos));
            request(new RateInfoRequest());

        }
        else if (cmd instanceof RecvImIcbm) {
            RecvImIcbm icbm = (RecvImIcbm) cmd;

            String sn = icbm.getSenderInfo().getScreenname();
            InstantMessage message = icbm.getMessage();
            String msg = OscarTools.stripHtml(message.getMessage());

            Message m = new Message();
            m.setTo(oscarSession.getJIDWithHighestPriority());
            m.setBody(msg);
            m.setType(Message.Type.chat);
            m.setFrom(this.oscarSession.getTransport().convertIDToJID(sn));
            oscarSession.getTransport().sendPacket(m);
        }
        else if (cmd instanceof WarningNotification) {
            WarningNotification wn = (WarningNotification) cmd;
            MiniUserInfo warner = wn.getWarner();
            if (warner == null) {
                Message m = new Message();
                m.setTo(oscarSession.getJIDWithHighestPriority());
                m.setBody("You have received an anonymous AIM warning.  Your warning level is now "+wn.getNewLevel()+"%.");
                m.setType(Message.Type.headline);
                m.setFrom(this.oscarSession.getTransport().getJID());
                oscarSession.getTransport().sendPacket(m);
            }
            else {
                Log.debug("*** " + warner.getScreenname()
                        + " warned you up to " + wn.getNewLevel() + "%");
                Message m = new Message();
                m.setTo(oscarSession.getJIDWithHighestPriority());
                m.setBody("You have received an AIM warning from "+warner.getScreenname()+".  Your warning level is now "+wn.getNewLevel()+"%.");
                m.setType(Message.Type.headline);
                m.setFrom(this.oscarSession.getTransport().getJID());
                oscarSession.getTransport().sendPacket(m);

            }
        }
        else if (cmd instanceof BuddyStatusCmd) {
            BuddyStatusCmd bsc = (BuddyStatusCmd)cmd;
            FullUserInfo info = bsc.getUserInfo();
            buddystore.put(info.getScreenname(), info);
            Presence p = new Presence();
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(info.getScreenname()));

            if (info.getAwayStatus()) {
                p.setShow(Presence.Show.away);
            }

            List<ExtraInfoBlock> extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray(
);
                        String msg;
                        try {
                            msg = new String(msgBytes, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e1) {
                            continue;
                        }
                        if (msg.length() > 0) {
                            p.setStatus(msg);
                        }
                    }
                }
            }
            oscarSession.getTransport().sendPacket(p);
        }
        else if (cmd instanceof BuddyOfflineCmd) {
            BuddyOfflineCmd boc = (BuddyOfflineCmd)cmd;
            buddystore.remove(boc.getScreenname());
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(boc.getScreenname()));
            oscarSession.getTransport().sendPacket(p);
        }
        else if (cmd instanceof RateChange) {
//            RateChange rc = (RateChange) cmd;

            //Log.debug("rate change: current avg is "
            //        + rc.getRateInfo().getCurrentAvg());
        }
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
//        SnacPacket packet = e.getSnacPacket();
        //Log.debug("got snac response type "
        //        + Integer.toHexString(packet.getFamily()) + "/"
        //        + Integer.toHexString(packet.getCommand()) + ": "
        //        + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            RateInfoCmd ric = (RateInfoCmd) cmd;

            List <RateClassInfo> rateClasses = ric.getRateClassInfos();

            int[] classes = new int[rateClasses.size()];
            for (int i = 0; i < rateClasses.size(); i++) {
                classes[i] = rateClasses.get(i).getRateClass();
            }

            request(new RateAck(classes));
        }
    }

    public int[] getSnacFamilies() { return snacFamilies; }

    protected void setSnacFamilies(int[] families) {
        this.snacFamilies = families.clone();
        Arrays.sort(snacFamilies);
    }

    protected void setSnacFamilyInfos(Collection<SnacFamilyInfo> infos) {
        snacFamilyInfos = infos;
    }

    protected boolean supportsFamily(int family) {
        return Arrays.binarySearch(snacFamilies, family) >= 0;
    }

    protected void clientReady() {
        if (!sentClientReady) {
            sentClientReady = true;
            request(new ClientReadyCmd(snacFamilyInfos));
        }
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd) {
        return dispatchRequest(cmd, null);
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        dispatchRequest(req);
        return req;
    }

    protected void dispatchRequest(SnacRequest req) {
        oscarSession.handleRequest(req);
    }

    protected SnacRequest request(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);

        handleReq(req);

        return req;
    }

    private void handleReq(SnacRequest request) {
        int family = request.getCommand().getFamily();
        if (snacFamilies == null || supportsFamily(family)) {
            // this connection supports this snac, so we'll send it here
            sendRequest(request);
        } else {
            oscarSession.handleRequest(request);
        }
    }

    /**
     * Retrieves and sends last known status.
     *
     * This retrieves the last known status of the user and sends it on
     * to the JID associated with this session.  Meant for probe packets.
     *
     * @param sn Screen name to check on.
     */
    public void getAndSendStatus(String sn) {
        if (buddystore.containsKey(sn)) {
            FullUserInfo info = buddystore.get(sn);
            buddystore.put(info.getScreenname(), info);
            Presence p = new Presence();
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(info.getScreenname()));

            if (info.getAwayStatus()) {
                p.setShow(Presence.Show.away);
            }

            List<ExtraInfoBlock> extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray(
);
                        String msg;
                        try {
                            msg = new String(msgBytes, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e1) {
                            continue;
                        }
                        if (msg.length() > 0) {
                            p.setStatus(msg);
                        }
                    }
                }
            }
            oscarSession.getTransport().sendPacket(p);
        }
        else {
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(sn));
            oscarSession.getTransport().sendPacket(p);
        }
    }

    /**
     * Retrieves and sends last known status for all buddies.
     *
     * This retrieves all known statuses and sends each one of them to the specified JID.
     * This is typically used when a new resource comes online.
     *
     * @param jid JID (with resource) to send the list to.
     */
    public void getAndSendAllStatuses(JID jid) {
        for (FullUserInfo info : buddystore.values()) {
            buddystore.put(info.getScreenname(), info);
            Presence p = new Presence();
            p.setTo(oscarSession.getJID());
            p.setFrom(oscarSession.getTransport().convertIDToJID(info.getScreenname()));

            if (info.getAwayStatus()) {
                p.setShow(Presence.Show.away);
            }

            List<ExtraInfoBlock> extraInfo = info.getExtraInfoBlocks();
            if (extraInfo != null) {
                for (ExtraInfoBlock i : extraInfo) {
                    ExtraInfoData data = i.getExtraData();

                    if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
                        ByteBlock msgBlock = data.getData();
                        int len = BinaryTools.getUShort(msgBlock, 0);
                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray(
);
                        String msg;
                        try {
                            msg = new String(msgBytes, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e1) {
                            continue;
                        }
                        if (msg.length() > 0) {
                            p.setStatus(msg);
                        }
                    }
                }
            }
            oscarSession.getTransport().sendPacket(p);
        }

    }

}
