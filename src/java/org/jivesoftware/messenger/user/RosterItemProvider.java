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

import java.util.Iterator;

/**
 * <p>Defines the provider methods required for creating, reading, updating and deleting roster items.</p>
 * <p/>
 * <p>Rosters are another user resource accessed via the user or chatbot's long ID. A user/chatbot may have
 * zero or more roster items and each roster item may have zero or more groups. Each roster item is
 * additionaly keyed on a XMPP jid. In most cases, the entire roster will be read in from memory and manipulated
 * or sent to the user. However some operations will need to retrive specific roster items rather than the
 * entire roster.</p>
 *
 * @author Iain Shigeoka
 */
public interface RosterItemProvider {
    /**
     * <p>Creates a new roster item for the given user (optional operation).</p>
     * <p/>
     * <p><b>Important!</b> The item passed as a parameter to this method is strictly a convenience for passing all
     * of the data needed for a new roster item. The roster item returned from the method will be cached by Messenger.
     * In some cases, the roster item passed in will be passed back out. However, if an implementation may
     * return RosterItems as a separate class (for example, a RosterItem that directly accesses the backend
     * storage, or one that is an object in an object database).
     * <p/>
     * <p>If you don't want roster items edited through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item the settings for the roster item to create
     * @return The created roster item
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    CachedRosterItem createItem(String username, RosterItem item) throws UserAlreadyExistsException, UnsupportedOperationException;

    /**
     * <p>Update the roster item in storage with the information contained in the given item (optional operation).</p>
     * <p/>
     * <p>If you don't want roster items edited through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item   The roster item to update
     * @throws UserNotFoundException         If no entry could be found to update
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void updateItem(String username, CachedRosterItem item) throws UserNotFoundException, UnsupportedOperationException;

    /**
     * <p>Delete the roster item with the given itemJID for the user (optional operation).</p>
     * <p/>
     * <p>If you don't want roster items deleted through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username the long ID of the user/chatbot that owns the roster item
     * @param rosterItemID The roster item to delete
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void deleteItem(String username, long rosterItemID) throws UnsupportedOperationException;

    /**
     * Returns an iterator on the usernames whose roster includes the specified JID.
     *
     * @param jid the jid that the rosters should include.
     * @return an iterator on the usernames whose roster includes the specified JID.
     */
    Iterator<String> getUsernames(String jid);

    /**
     * <p>Obtain a count of the number of roster items available for the given user.</p>
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return The number of roster items available for the user
     */
    int getItemCount(String username);

    /**
     * <p>Retrieve an iterator of RosterItems for the given user.</p>
     * <p/>
     * <p>This method will commonly be called when a user logs in. The data will be cached
     * in memory when possible. However, some rosters may be very large so items may need
     * to be retrieved from the provider more frequently than usual for provider data.
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return An iterator of all RosterItems owned by the user
     */
    Iterator getItems(String username);
}
