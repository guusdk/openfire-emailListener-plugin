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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.JID;

import java.util.Iterator;
import java.util.List;

/**
 * <p>A roster is a list of users that the user wishes to know if they are online.</p>
 * <p>Rosters are similar to buddy groups in popular IM clients. The Roster interface is
 * a generic representation of the roster data. There are two primary implementations
 * of the Roster in Messenger, the CachedRoster representing a cached, persistently stored
 * Roster attached to a user/chatbot account, and an Roster containing a roster as XML
 * data (usually as it enters and exists Messenger over an XMPP c2s or s2s connection).
 * It is an important distinction as changes to CachedRosters will be saved to disk while
 * IQRosters are transient data objects.</p>
 *
 * @author Iain Shigeoka
 *
 * @see CachedRoster
 */
public interface Roster {

    /**
     * Returns true if the specified user is a member of the roster, false otherwise.
     *
     * @param user the user object to check.
     * @return true if the specified user is a member of the roster, false otherwise.
     */
    public boolean isRosterItem(JID user);

    /**
     * Returns an iterator of users in this roster.
     *
     * @return an iterator of users in this roster.
     */
    public Iterator<RosterItem> getRosterItems() throws UnauthorizedException;

    /**
     * Returns the total number of users in the roster.
     *
     * @return the number of online users in the roster.
     */
    public int getTotalRosterItemCount() throws UnauthorizedException;

    /**
     * Gets a user from the roster. If the roster item does not exist, an empty one is created.
     * The new roster item is not stored in the roster until it is added using
     * addRosterItem().
     *
     * @param user the XMPPAddress for the roster item to retrieve
     * @return The roster item associated with the user XMPPAddress
     * @throws UnauthorizedException If not the user or an administrator
     */
    public RosterItem getRosterItem(JID user) throws UnauthorizedException, UserNotFoundException;

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user address
     * as an existing item.
     *
     * @param user the item to add to the roster.
     * @throws UnauthorizedException if not the item or an administrator.
     */
    public RosterItem createRosterItem(JID user) throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user address
     * as an existing item.
     *
     * @param user     the item to add to the roster.
     * @param nickname The nickname for the roster entry (can be null)
     * @param groups   The list of groups to assign this roster item to (can be null)
     * @throws UnauthorizedException      if not the item or an administrator.
     * @throws UserAlreadyExistsException If a roster item already exists for the given user
     */
    public RosterItem createRosterItem(JID user, String nickname, List<String> groups)
            throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Create a new item to the roster based as a copy of the given item.
     * Roster items may not be created that contain the same user address
     * as an existing item in the roster.
     *
     * @param item the item to copy and add to the roster.
     * @throws UnauthorizedException      if not the item or an administrator.
     * @throws UserAlreadyExistsException If a roster item already exists for the given user
     */
    public void createRosterItem(org.xmpp.packet.Roster.Item item)
            throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Update an item that is already in the roster.
     *
     * @param item the item to update in the roster.
     * @throws UnauthorizedException if not the user or an administrator.
     * @throws UserNotFoundException If the roster item for the given user doesn't already exist
     */
    public void updateRosterItem(RosterItem item) throws UnauthorizedException, UserNotFoundException;

    /**
     * Remove a user from the roster.
     *
     * @param user the user to remove from the roster.
     * @return The roster item being removed or null if none existed
     * @throws UnauthorizedException if not the user or an administrator.
     */
    public RosterItem deleteRosterItem(JID user) throws UnauthorizedException;
}