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

package org.jivesoftware.messenger.muc;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.TimeZone;

import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.MetaDataFragment;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.spi.MessageImpl;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * Represent the data model for one <code>MUCRoom</code> history. Including chat transcript,
 * joining and leaving times.
 * 
 * @author Gaston Dombiak
 */
public final class MUCRoomHistory {

    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");
    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    }

    private MUCRoom room;

    private HistoryStrategy historyStrategy;

    private boolean isNonAnonymousRoom;

    public MUCRoomHistory(MUCRoom mucRoom, HistoryStrategy historyStrategy) {
        this.room = mucRoom;
        this.isNonAnonymousRoom = mucRoom.canAnyoneDiscoverJID();
        this.historyStrategy = historyStrategy;
    }

    public void addMessage(Message packet) {
        // Don't keep messages whose sender is the room itself (thus address without resource)
        // unless the message is changing the room's subject
        if ((packet.getSender().getResourcePrep() == null
                || packet.getSender().getResourcePrep().length() == 0) &&
                packet.getSubject() == null) {
            return;
        }
        Message packetToAdd = (Message) packet.createDeepCopy();
        // Clean the originating session of this message. We will need to deliver this message even
        // after the user that sent it has logged off. Otherwise, it won't be delivered since
        // messenger expects senders of messages to be authenticated when delivering their messages.
        packetToAdd.setOriginatingSession(null);

        // TODO Analyze concurrency (on the LinkList) when adding many messages simultaneously

        // Check if the room has changed its configuration
        if (isNonAnonymousRoom != room.canAnyoneDiscoverJID()) {
            isNonAnonymousRoom = room.canAnyoneDiscoverJID();
            // Update the "from" attribute of the delay information in the history
            Message message;
            MetaDataFragment frag;
            // TODO Make this update in a separate thread
            for (Iterator it = getMessageHistory(); it.hasNext();) {
                message = (Message) it.next();
                frag = (MetaDataFragment) message.getFragment("x", "jabber:x:delay");
                if (room.canAnyoneDiscoverJID()) {
                    // Set the Full JID as the "from" attribute
                    try {
                        MUCRole role = room.getOccupant(message.getSender().getResourcePrep());
                        frag.setProperty("x:from", role.getChatUser().getAddress().toStringPrep());
                    }
                    catch (UserNotFoundException e) {
                    }
                }
                else {
                    // Set the Room JID as the "from" attribute
                    frag.setProperty("x:from", message.getSender().toStringPrep());
                }
            }

        }

        // Add the delay information to the message
        MetaDataFragment delayInformation = new MetaDataFragment("jabber:x:delay", "x");
        Date current = new Date();
        delayInformation.setProperty("x:stamp", UTC_FORMAT.format(current));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            try {
                MUCRole role = room.getOccupant(packet.getSender().getResourcePrep());
                delayInformation.setProperty("x:from", role.getChatUser().getAddress()
                        .toStringPrep());
            }
            catch (UserNotFoundException e) {
            }
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.setProperty("x:from", packet.getSender().toStringPrep());
        }
        packetToAdd.addFragment(delayInformation);
        historyStrategy.addMessage(packetToAdd);
    }

    public Iterator getMessageHistory() {
        return historyStrategy.getMessageHistory();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list
     * iterator will be positioned at the end of the history so senders of this message must
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator getReverseMessageHistory() {
        return historyStrategy.getReverseMessageHistory();
    }

    /**
     * Creates a new message and adds it to the history. The new message will be created based on
     * the provided information. This information will likely come from the database when loading
     * the room history from the database.
     *
     * @param senderJID the sender's JID of the message to add to the history.
     * @param nickname the sender's nickname of the message to add to the history.
     * @param sentDate the date when the message was sent to the room.
     * @param subject the subject included in the message.
     * @param body the body of the message.
     */
    public void addOldMessage(String senderJID, String nickname, Date sentDate, String subject,
            String body) {
        Message packetToAdd = new MessageImpl();
        packetToAdd.setType(Message.GROUP_CHAT);
        packetToAdd.setSubject(subject);
        packetToAdd.setBody(body);
        // Set the sender of the message
        if (nickname != null && nickname.trim().length() > 0) {
            XMPPAddress roomJID = room.getRole().getRoleAddress();
            // Recreate the sender address based on the nickname and room's JID
            packetToAdd.setSender(new XMPPAddress(roomJID.getNamePrep(), roomJID.getHostPrep(),
                    nickname));
        }
        else {
            // Set the room as the sender of the message
            packetToAdd.setSender(room.getRole().getRoleAddress());
        }

        // Add the delay information to the message
        MetaDataFragment delayInformation = new MetaDataFragment("jabber:x:delay", "x");
        delayInformation.setProperty("x:stamp", UTC_FORMAT.format(sentDate));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            delayInformation.setProperty("x:from", senderJID);
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.setProperty("x:from", room.getRole().getRoleAddress().toStringPrep());
        }
        packetToAdd.addFragment(delayInformation);
        historyStrategy.addMessage(packetToAdd);
    }

    /**
     * Returns true if there is a message within the history of the room that has changed the
     * room's subject.
     *
     * @return true if there is a message within the history of the room that has changed the
     *         room's subject.
     */
    public boolean hasChangedSubject() {
        return historyStrategy.hasChangedSubject();
    }
}