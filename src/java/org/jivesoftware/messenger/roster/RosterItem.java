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

package org.jivesoftware.messenger.roster;

import org.jivesoftware.util.IntEnum;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.CacheSizes;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * <p>Represents a single roster item for a User's Roster.</p>
 * <p>The server doesn't need to know anything about roster groups so they are
 * not stored with easy retrieval or manipulation in mind. The important data
 * elements of a roster item (beyond the jid adddress of the roster entry) includes:</p>
 * <p/>
 * <ul>
 * <li>nick   - A nickname for the user when used in this roster</li>
 * <li>sub    - A subscription type: to, from, none, both</li>
 * <li>ask    - An optional subscription ask status: subscribe, unsubscribe</li>
 * <li>groups - A list of groups to organize roster entries under (e.g. friends, co-workers, etc)</li>
 * </ul>
 *
 * @author Gaston Dombiak
 */
public class RosterItem implements Cacheable {

    public static class SubType extends IntEnum {
        protected SubType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static SubType getTypeFromInt(int value) {
            return (SubType)getEnumFromInt(SubType.class, value);
        }
    }

    public static class AskType extends IntEnum {
        protected AskType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static AskType getTypeFromInt(int value) {
            return (AskType)getEnumFromInt(AskType.class, value);
        }
    }

    public static class RecvType extends IntEnum {
        protected RecvType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static RecvType getTypeFromInt(int value) {
            return (RecvType)getEnumFromInt(RecvType.class, value);
        }
    }

    /**
     * <p>Indicates the roster item should be removed.</p>
     */
    public static final SubType SUB_REMOVE = new SubType("remove", -1);
    /**
     * <p>No subscription is established.</p>
     */
    public static final SubType SUB_NONE = new SubType("none", 0);
    /**
     * <p>The roster owner has a subscription to the roster item's presence.</p>
     */
    public static final SubType SUB_TO = new SubType("to", 1);
    /**
     * <p>The roster item has a subscription to the roster owner's presence.</p>
     */
    public static final SubType SUB_FROM = new SubType("from", 2);
    /**
     * <p>The roster item and owner have a mutual subscription.</p>
     */
    public static final SubType SUB_BOTH = new SubType("both", 3);

    /**
     * <p>The roster item has no pending subscripton requests.</p>
     */
    public static final AskType ASK_NONE = new AskType("", -1);
    /**
     * <p>The roster item has been asked for permission to subscribe to their presence
     * but no response has been received.</p>
     */
    public static final AskType ASK_SUBSCRIBE = new AskType("subscribe", 0);
    /**
     * <p>The roster owner has asked to the roster item to unsubscribe from it's
     * presence but has not received confirmation.</p>
     */
    public static final AskType ASK_UNSUBSCRIBE = new AskType("unsubscribe", 1);

    /**
     * <p>There are no subscriptions that have been received but not presented to the user.</p>
     */
    public static final RecvType RECV_NONE = new RecvType("", -1);
    /**
     * <p>The server has received a subscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_SUBSCRIBE = new RecvType("sub", 1);
    /**
     * <p>The server has received an unsubscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_UNSUBSCRIBE = new RecvType("unsub", 2);

    protected RecvType recvStatus;
    protected JID jid;
    protected String nickname;
    protected List<String> groups;
    protected SubType subStatus;
    protected AskType askStatus;
    private long rosterID;

    public RosterItem(long id,
                                JID jid,
                                SubType subStatus,
                                AskType askStatus,
                                RecvType recvStatus,
                                String nickname,
                                List<String> groups) {
        this(jid, subStatus, askStatus, recvStatus, nickname, groups);
        this.rosterID = id;
    }

    public RosterItem(JID jid,
                           SubType subStatus,
                           AskType askStatus,
                           RecvType recvStatus,
                           String nickname,
                           List<String> groups) {
        this.jid = jid;
        this.subStatus = subStatus;
        this.askStatus = askStatus;
        this.recvStatus = recvStatus;
        this.nickname = nickname;
        this.groups = new LinkedList<String>();
        if (groups != null) {
            Iterator<String> groupItr = groups.iterator();
            while (groupItr.hasNext()) {
                this.groups.add(groupItr.next());
            }
        }
    }

    /**
     * <p>Create a roster item from the data in another one.</p>
     *
     * @param item
     */
    public RosterItem(org.xmpp.packet.Roster.Item item) {
        this(item.getJID(),
                getSubType(item),
                getAskStatus(item),
                RosterItem.RECV_NONE,
                item.getName(),
                new LinkedList<String>(item.getGroups()));
    }

    private static RosterItem.AskType getAskStatus(org.xmpp.packet.Roster.Item item) {
        if (item.getAsk() == org.xmpp.packet.Roster.Ask.subscribe) {
            return RosterItem.ASK_SUBSCRIBE;
        }
        else if (item.getAsk() == org.xmpp.packet.Roster.Ask.unsubscribe) {
            return RosterItem.ASK_UNSUBSCRIBE;
        }
        else {
            return RosterItem.ASK_NONE;
        }
    }

    private static RosterItem.SubType getSubType(org.xmpp.packet.Roster.Item item) {
        if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.to) {
            return RosterItem.SUB_TO;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.from) {
            return RosterItem.SUB_FROM;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.both) {
            return RosterItem.SUB_BOTH;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
            return RosterItem.SUB_REMOVE;
        }
        else {
            return RosterItem.SUB_NONE;
        }
    }

    /**
     * <p>Obtain the current subscription status of the item.</p>
     *
     * @return The subscription status of the item
     */
    public SubType getSubStatus() {
        return subStatus;
    }

    /**
     * <p>Set the current subscription status of the item.</p>
     *
     * @param subStatus The subscription status of the item
     */
    public void setSubStatus(SubType subStatus) {
        this.subStatus = subStatus;
    }

    /**
     * <p>Obtain the current ask status of the item.</p>
     *
     * @return The ask status of the item
     */
    public AskType getAskStatus() {
        return askStatus;
    }

    /**
     * <p>Set the current ask status of the item.</p>
     *
     * @param askStatus The ask status of the item
     */
    public void setAskStatus(AskType askStatus) {
        this.askStatus = askStatus;
    }

    /**
     * <p>Obtain the current recv status of the item.</p>
     *
     * @return The recv status of the item
     */
    public RecvType getRecvStatus() {
        return recvStatus;
    }

    /**
     * <p>Set the current recv status of the item.</p>
     *
     * @param recvStatus The recv status of the item
     */
    public void setRecvStatus(RecvType recvStatus) {
        this.recvStatus = recvStatus;
    }

    /**
     * <p>Obtain the address of the item.</p>
     *
     * @return The address of the item
     */
    public JID getJid() {
        return jid;
    }

    /**
     * <p>Obtain the current nickname for the item.</p>
     *
     * @return The subscription status of the item
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * <p>Set the current nickname for the item.</p>
     *
     * @param nickname The subscription status of the item
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * <p>Obtain the groups for the item.</p>
     *
     * @return The subscription status of the item
     */
    public List<String> getGroups() {
        return groups;
    }

    /**
     * <p>Set the current groups for the item.</p>
     *
     * @param groups The subscription status of the item
     */
    public void setGroups(List<String> groups) {
        if (groups == null) {
            this.groups = new LinkedList<String>();
        }
        else {
            this.groups = groups;
        }
    }

    /**
     * <p>Obtain the roster ID associated with this particular roster item.</p>
     * <p/>
     * <p>Databases can use the roster ID as the key in locating roster items.</p>
     *
     * @return The roster ID
     */
    public long getID() {
        return rosterID;
    }

    public void setID(long rosterID) {
        this.rosterID = rosterID;
    }

    /**
     * <p>Update the cached item as a copy of the given item.</p>
     * <p/>
     * <p>A convenience for getting the item and setting each attribute.</p>
     *
     * @param item The item who's settings will be copied into the cached copy
     */
    public void setAsCopyOf(org.xmpp.packet.Roster.Item item) {
        setNickname(item.getName());
        setGroups(new LinkedList<String>(item.getGroups()));
    }

    public int getCachedSize() {
        int size = jid.toBareJID().length();
        size += CacheSizes.sizeOfString(nickname);
        size += CacheSizes.sizeOfCollection(groups);
        size += CacheSizes.sizeOfInt(); // subStatus
        size += CacheSizes.sizeOfInt(); // askStatus
        size += CacheSizes.sizeOfLong(); // id
        return size;
    }
}