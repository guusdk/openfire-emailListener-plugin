/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.irc;

import org.jivesoftware.wildfire.gateway.*;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

/***
 * IRC Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class IRCTransport extends BaseTransport {

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyUsername()
     */
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.irc.username", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyPassword()
     */
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.irc.password", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyNickname()
     */
    public String getTerminologyNickname() {
        return LocaleUtils.getLocalizedString("gateway.irc.nickname", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyRegistration()
     */
    public String getTerminologyRegistration() {
        return LocaleUtils.getLocalizedString("gateway.irc.registration", "gateway");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isPasswordRequired()
     */
    public Boolean isPasswordRequired() { return false; }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isNicknameRequired()
     */
    public Boolean isNicknameRequired() { return true; }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isUsernameValid(String)
     */
    public Boolean isUsernameValid(String username) {
        return username.matches("\\w+");
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getOptionsConfig(org.jivesoftware.wildfire.gateway.TransportType)
     * @param type The transport type to distinguish if needed.
     * @return XML document describing the options interface.
     */
    public static Element getOptionsConfig(TransportType type) {
        Element optConfig = DocumentHelper.createElement("optionconfig");
        Element leftPanel = optConfig.addElement("leftpanel");
        Element rightPanel = optConfig.addElement("rightpanel");
        rightPanel.addElement("item")
                  .addAttribute("type", "text")
                  .addAttribute("sysprop", "plugin.gateway.irc.connecthost")
                  .addAttribute("desc", "Host");
        rightPanel.addElement("item")
                  .addAttribute("type", "text")
                  .addAttribute("sysprop", "plugin.gateway.irc.connectport")
                  .addAttribute("desc", "Port");
        return optConfig;
    }

    /**
     * Handles creating a IRC session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        Log.debug("Logging in to IRC gateway.");
        TransportSession session = new IRCSession(registration, jid, this, priority);
        this.getSessionManager().startThread(session);
        ((IRCSession)session).logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a IRC session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        Log.debug("Logging out of IRC gateway.");
        ((IRCSession)session).logOut();
        session.sessionDone();
        // Just in case.
        session.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    /**
     * Converts a jabber status to an IRC away message (or not).
     *
     * @param jabStatus Jabber presence type.
     * @param verboseStatus Verbose status information.
     * @return IRC status string.
     */
    public String convertJabStatusToIRC(PresenceType jabStatus, String verboseStatus) {
        if (jabStatus == PresenceType.available) {
            return null;
        }
        else if (jabStatus == PresenceType.away) {
            return verboseStatus.equals("") ? "Away" : "Away: "+verboseStatus;
        }
        else if (jabStatus == PresenceType.xa) {
            return verboseStatus.equals("") ? "Extended Away" : "Extended Away: "+verboseStatus;
        }
        else if (jabStatus == PresenceType.dnd) {
            return verboseStatus.equals("") ? "Do Not Disturb" : "Do Not Disturb: "+verboseStatus;
        }
        else if (jabStatus == PresenceType.chat) {
            return null;
        }
        else if (jabStatus == PresenceType.unavailable) {
            // This should never show up.
            return null;
        }
        else {
            return null;
        }
    }

}
