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

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.messenger.user.*;
import org.jivesoftware.util.Log;

import javax.naming.directory.*;
import javax.naming.NamingEnumeration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * LDAP implementation of the UserProvider interface. All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker
 */
public class LdapUserProvider implements UserProvider {

    private LdapManager manager;

    public LdapUserProvider() {
        manager = LdapManager.getInstance();
    }

    public User loadUser(String username) throws UserNotFoundException {
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                manager.getUsernameField(), manager.getNameField(),
                manager.getEmailField()
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            String name = null;
            String email = null;
            Attribute nameField = attrs.get(manager.getNameField());
            if (nameField != null) {
                name = (String)nameField.get();
            }
            Attribute emailField = attrs.get(manager.getEmailField());
            if (emailField != null) {
                email = (String)emailField.get();
            }
            return new User(username, name, email, new Date(), new Date());
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
        }
    }

    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        throw new UnsupportedOperationException();
    }

    public void deleteUser(String username) {
        throw new UnsupportedOperationException();
    }

    public int getUserCount() {
        int count = 0;
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = "(" + manager.getUsernameField() + "=*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);
            while (answer.hasMoreElements()) {
                count++;
                answer.nextElement();
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
        }
        return count;
    }

    public Collection<User> getUsers() {
        List<String> usernames = new ArrayList<String>();
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = "(" + manager.getUsernameField() + "=*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);

            while (answer.hasMoreElements()) {
                // Get the next userID.
                usernames.add(
                    (String)((SearchResult)answer.next()).getAttributes().get(
                    manager.getUsernameField()).get()
                );
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
        }
        return new UserCollection((String[])usernames.toArray(new String[usernames.size()]));
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>();
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[] { manager.getUsernameField() });
            String filter = "(" + manager.getUsernameField() + "=*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);
            for (int i = 0; i < startIndex; i++) {
                answer.next();
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next userID.
                    usernames.add(
                        (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getUsernameField()).get()
                    );
                }
                else {
                    break;
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { ctx.close(); }
            catch (Exception ignored) { }
        }
        return new UserCollection((String[])usernames.toArray(new String[usernames.size()]));
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setName(String username, String name) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }
}