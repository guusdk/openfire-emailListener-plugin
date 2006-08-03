/**
 * $RCSfile$
 * $Revision: 3191 $
 * $Date: 2005-12-12 13:41:22 -0300 (Mon, 12 Dec 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.ldap;

import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupNotFoundException;
import org.jivesoftware.wildfire.group.GroupProvider;
import org.jivesoftware.wildfire.group.GroupCollection;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.Control;
import javax.naming.ldap.SortControl;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is treated as read-only so any set
 * operations will result in an exception.
 *
 * @author Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider implements GroupProvider {

    private LdapManager manager;
    private UserManager userManager;
    private int groupCount;
    private long expiresStamp;
    private String[] standardAttributes;

    /**
     * Constructor of the LdapGroupProvider class. Gets an LdapManager instance from the LdapManager class.
     */
    public LdapGroupProvider() {
        manager = LdapManager.getInstance();
        userManager = UserManager.getInstance();
        groupCount = -1;
        expiresStamp = System.currentTimeMillis();
        standardAttributes = new String[3];
        standardAttributes[0] = manager.getGroupNameField();
        standardAttributes[1] = manager.getGroupDescriptionField();
        standardAttributes[2] = manager.getGroupMemberField();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the name of the group to create.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Group getGroup(String group) throws GroupNotFoundException {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        String searchFilter = "(&" + filter + "(" +
                manager.getGroupNameField() + "=" + group + "))";
        Collection<Group> groups;
        try {
            groups = populateGroups(searchForGroups(searchFilter, standardAttributes));
        }
        catch (Exception e) {
            Log.error("Error populating groups from LDAP", e);
            throw new GroupNotFoundException("Error populating groups from LDAP", e);
        }
        if (groups.size() > 1) {
            // If multiple groups found, throw exception.
            throw new GroupNotFoundException("Too many groups with name " + group + " were found.");
        }
        else if (groups.isEmpty()) {
            throw new GroupNotFoundException("Group with name " + group + " not found.");
        }
        else {
            return groups.iterator().next();
        }
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String name, String description)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public int getGroupCount() {
        // Cache group count for 5 minutes.
        if (groupCount != -1 && System.currentTimeMillis() < expiresStamp) {
            return groupCount;
        }
        int count = 0;

        if (manager.isDebugEnabled()) {
            Log.debug("Trying to get the number of groups in the system.");
        }

        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        String returningAttributes[] = {manager.getGroupNameField()};
        try {
            NamingEnumeration<SearchResult> answer = searchForGroups(searchFilter, returningAttributes);
            for (; answer.hasMoreElements(); count++) {
                try {
                    answer.next();
                }
                catch (Exception e) {
                    // Ignore.
                }
            }

            this.groupCount = count;
            this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE * 5;
        }
        catch (Exception ex) {
            Log.error("Error searching for groups in LDAP", ex);
        }
        return count;
    }

    public Collection<Group> getGroups() {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        try {
            return populateGroups(searchForGroups(filter, standardAttributes));
        }
        catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Collection<Group> getGroups(Set<String> groupNames) {
        if (groupNames.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<Group> groups = new ArrayList<Group>(groupNames.size());

        String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        // Instead of loading all groups at once which may not work for super big collections
        // of group names, we are going to make many queries and load by 10 groups at onces
        Collection<String> searchFilters = new ArrayList<String>(groupNames.size());
        List<String> names = new ArrayList<String>(groupNames);
        int i = 0;
        int range = 10;
        do {
            List<String> subset = names.subList(i, Math.min(i + range, groupNames.size()));

            if (subset.size() == 1) {
                String searchFilter = "(&" + filter + "(" +
                        manager.getGroupNameField() + "=" + subset.get(0) + "))";
                searchFilters.add(searchFilter);
            }
            else {
                StringBuilder sb = new StringBuilder(300);
                sb.append("(&").append(filter).append("(|");
                for (String groupName : subset) {
                    sb.append("(").append(manager.getGroupNameField()).append("=");
                    sb.append(groupName).append(")");
                }
                sb.append("))");
                searchFilters.add(sb.toString());
            }
            // Increment counter to get next range
            i = i + range;
        }
        while (groupNames.size() > i);

        // Perform all required queries to load all requested groups
        for (String searchFilter : searchFilters) {
            try {
                groups.addAll(populateGroups(searchForGroups(searchFilter, standardAttributes)));
            }
            catch (Exception e) {
                Log.error("Error populating groups from LDAP", e);
                return Collections.emptyList();
            }
        }
        return new ArrayList<Group>(groups);
    }

    public Collection<Group> getGroups(int startIndex, int numResults) {
        // Get an enumeration of all groups in the system
        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        NamingEnumeration<SearchResult> answer;
        try {
            answer = searchForGroups(searchFilter, standardAttributes);
        }
        catch (Exception e) {
            Log.error("Error searching for groups in LDAP", e);
            return Collections.emptyList();
        }

        // Place all groups that are wanted into an enumeration
        Vector<SearchResult> v = new Vector<SearchResult>();
        for (int i = 1; answer.hasMoreElements() && i <= (startIndex + numResults); i++) {
            try {
                SearchResult sr = answer.next();
                if (i >= startIndex) {
                    v.add(sr);
                }
            }
            catch (Exception e) {
                // Ignore.
            }
        }

        try {
            return populateGroups(v.elements());
        }
        catch (NamingException e) {
            Log.error("Error populating groups recieved from LDAP", e);
            return Collections.emptyList();
        }
    }

    public Collection<Group> getGroups(JID user) {
        XMPPServer server = XMPPServer.getInstance();
        String username;
        if (!manager.isPosixMode()) {
            // Check if the user exists (only if user is a local user)
            if (!server.isLocal(user)) {
                return Collections.emptyList();
            }
            username = JID.unescapeNode(user.getNode());
            try {
                username = manager.findUserDN(username) + "," + manager.getBaseDN();
            }
            catch (Exception e) {
                Log.error("Could not find user in LDAP " + username);
                return Collections.emptyList();
            }
        }
        else {
            username = server.isLocal(user) ? JID.unescapeNode(user.getNode()) : user.toString();
        }

        String filter = MessageFormat.format(manager.getGroupSearchFilter(), username);
        try {
            return populateGroups(searchForGroups(filter, standardAttributes));
        }
        catch (Exception e) {
            Log.error("Error populating groups recieved from LDAP", e);
            return Collections.emptyList();
        }
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param groupName name of a group.
     * @param user the JID of the user to add
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void addMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param groupName the naame of a group.
     * @param user the JID of the user with new privileges
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param groupName the name of a group.
     * @param user the JID of the user to delete.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, JID user) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true because LDAP groups are read-only.
     *
     * @return true because all LDAP functions are read-only.
     */
    public boolean isReadOnly() {
        return true;
    }

    public Collection<Group> search(String query) {
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // Make the query be a wildcard search by default. So, if the user searches for
        // "Test", make the search be "Test*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(manager.getGroupNameField()).append("=").append(query).append(")");
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            while (answer.hasMoreElements()) {
                // Get the next group.
                String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getGroupNameField()).get();
                // Escape group name and add to results.
                groupNames.add(JID.escapeNode(groupName));
            }
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public Collection<Group> search(String query, int startIndex, int numResults) {
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // Make the query be a wildcard search by default. So, if the user searches for
        // "Test", make the search be "Test*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(manager.getGroupNameField()).append("=").append(query).append(")");

            // TODO: used paged results is supported by LDAP server.
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            for (int i=0; i < startIndex; i++) {
                if (answer.hasMoreElements()) {
                    answer.next();
                }
                else {
                    return Collections.emptyList();
                }
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next group.
                    String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getGroupNameField()).get();
                    // Escape group name and add to results.
                    groupNames.add(JID.escapeNode(groupName));
                }
                else {
                    break;
                }
            }
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return new GroupCollection(groupNames.toArray(new String[groupNames.size()]));
    }

    public boolean isSearchSupported() {
        return true;
    }

    /**
     * An auxilary method used to perform LDAP queries based on a provided LDAP search filter.
     *
     * @param searchFilter LDAP search filter used to query.
     * @return an enumeration of SearchResult.
     */
    private NamingEnumeration<SearchResult> searchForGroups(String searchFilter,
            String[] returningAttributes) throws NamingException, IOException
    {
        if (manager.isDebugEnabled()) {
            Log.debug("Trying to find all groups in the system.");
        }
        LdapContext ctx = null;
        NamingEnumeration<SearchResult> answer;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);
            if (manager.isDebugEnabled()) {
                Log.debug("Starting LDAP search...");
                Log.debug("Using groupSearchFilter: " + searchFilter);
            }

            // Search for the dn based on the groupname.
            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(returningAttributes);
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            answer = ctx.search("", searchFilter, searchControls);

            if (manager.isDebugEnabled()) {
                Log.debug("... search finished");
            }

            return answer;
        }
        finally {
            if (ctx != null) {
                try {
                    ctx.close();
                }
                catch (Exception ex) { /* do nothing */ }
            }
        }
    }

    /**
     * An auxilary method used to populate LDAP groups based on a provided LDAP search result.
     *
     * @param answer LDAP search result.
     * @return a collection of groups.
     */
    private Collection<Group> populateGroups(Enumeration<SearchResult> answer) throws NamingException {
        if (manager.isDebugEnabled()) {
            Log.debug("Starting to populate groups with users.");
        }
        DirContext ctx = null;
        try {
            TreeMap<String, Group> groups = new TreeMap<String, Group>();

            ctx = manager.getContext();

            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(new String[]{manager.getUsernameField()});
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }

            String userSearchFilter = MessageFormat.format(manager.getSearchFilter(), "*");
            XMPPServer server = XMPPServer.getInstance();
            String serverName = server.getServerInfo().getName();
            // Build 3 groups.
            // group 1: uid=
            // group 2: rest of the text until first comma
            // group 3: rest of the text
            Pattern pattern =
                    Pattern.compile("(?i)(^" + manager.getUsernameField() + "=)([^,]+)(.+)");

            while (answer.hasMoreElements()) {
                String name = "";
                try {
                    Attributes a = answer.nextElement().getAttributes();
                    String description;
                    try {
                        name = ((String)((a.get(manager.getGroupNameField())).get()));
                        description =
                                ((String)((a.get(manager.getGroupDescriptionField())).get()));
                    }
                    catch (Exception e) {
                        description = "";
                    }
                    TreeSet<JID> members = new TreeSet<JID>();
                    Attribute member = a.get(manager.getGroupMemberField());
                    NamingEnumeration ne = member.getAll();
                    while (ne.hasMore()) {
                        String username = (String) ne.next();
                        if (!manager.isPosixMode()) {   //userName is full dn if not posix
                            try {
                                // LdapName will not generate spaces around an '='
                                // (according to the docs)
                                Matcher matcher = pattern.matcher(username);
                                if (matcher.matches() && matcher.groupCount() == 3) {
                                    // The username is in the DN, no additional search needed
                                    username = matcher.group(2);
                                }
                                else {
                                    // We have to do a new search to find the username field

                                    // Get the CN using LDAP
                                    LdapName ldapname = new LdapName(username);
                                    String ldapcn = ldapname.get(ldapname.size() - 1);
                                    String combinedFilter =
                                            "(&(" + ldapcn + ")" + userSearchFilter + ")";
                                    NamingEnumeration usrAnswer =
                                            ctx.search("", combinedFilter, searchControls);
                                    if (usrAnswer.hasMoreElements()) {
                                        username = (String) ((SearchResult) usrAnswer.next())
                                                .getAttributes().get(
                                                manager.getUsernameField()).get();
                                    }
                                    else {
                                        throw new UserNotFoundException();
                                    }
                                }
                            }
                            catch (Exception e) {
                                if (manager.isDebugEnabled()) {
                                    Log.debug("Error populating user with DN: " + username, e);
                                }
                            }
                        }
                        // A search filter may have been defined in the LdapUserProvider.
                        // Therefore, we have to try to load each user we found to see if
                        // it passes the filter.
                        try {
                            JID userJID;
                            int position = username.indexOf("@" + serverName);
                            // Create JID of local user if JID does not match a component's JID
                            if (position == -1) {
                                // In order to lookup a username from the manager, the username
                                // must be a properly escaped JID node.
                                String escapedUsername = JID.escapeNode(username);
                                if (!escapedUsername.equals(username)) {
                                    // Check if escaped username is valid
                                    userManager.getUser(escapedUsername);
                                }
                                // No exception, so the user must exist. Add the user as a group
                                // member using the escaped username.
                                userJID = server.createJID(escapedUsername, null);
                            }
                            else {
                                // This is a JID of a component or node of a server's component
                                String node = username.substring(0, position);
                                String escapedUsername = JID.escapeNode(node);
                                userJID = new JID(escapedUsername + "@" + serverName);
                            }
                            members.add(userJID);
                        }
                        catch (UserNotFoundException e) {
                            if (manager.isDebugEnabled()) {
                                Log.debug("User not found: " + username);
                            }
                        }
                    }
                    if (manager.isDebugEnabled()) {
                        Log.debug("Adding group \"" + name + "\" with " + members.size() +
                                " members.");
                    }
                    Group g = new Group(name, description, members, new ArrayList<JID>());
                    groups.put(name, g);
                }
                catch (Exception e) {
                    if (manager.isDebugEnabled()) {
                        Log.debug("Error while populating group, " + name + ".", e);
                    }
                }
            }
            if (manager.isDebugEnabled()) {
                Log.debug("Finished populating group(s) with users.");
            }

            return groups.values();
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception e) {
                // Ignore.
            }
        }
    }
}