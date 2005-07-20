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

package org.jivesoftware.messenger.vcard;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.StringReader;

/**
 * Default implementation of the VCardProvider interface, which reads and writes data
 * from the <tt>jiveVCard</tt> database table.
 *
 * @author Gaston Dombiak
 */
public class DefaultVCardProvider implements VCardProvider {

    private static final String LOAD_PROPERTIES =
        "SELECT value FROM jiveVCard WHERE username=?";
    private static final String DELETE_PROPERTIES =
        "DELETE FROM jiveVCard WHERE username=?";
    private static final String UPDATE_PROPERTIES =
        "UPDATE jiveVCard SET value=? WHERE username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveVCard (username, value) VALUES (?, ?)";

    private SAXReader saxReader = new SAXReader();


    public Element loadVCard(String username) {
        synchronized (username.intern()) {
            Element vCardElement = null;
            java.sql.Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    vCardElement =
                            saxReader.read(new StringReader(rs.getString(1))).getRootElement();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            finally {
                try { if (pstmt != null) { pstmt.close(); } }
                catch (Exception e) { Log.error(e); }
                try { if (con != null) { con.close(); } }
                catch (Exception e) { Log.error(e); }
            }
            return vCardElement;
        }
    }

    public void createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        if (loadVCard(username) != null) {
            // The user already has a vCard
            throw new AlreadyExistsException("Username " + username + " already has a vCard");
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, vCardElement.asXML());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void updateVCard(String username, Element vCardElement) throws NotFoundException {
        if (loadVCard(username) == null) {
            // The user already has a vCard
            throw new NotFoundException("Username " + username + " does not have a vCard");
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTIES);
            pstmt.setString(1, vCardElement.asXML());
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void deleteVCard(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    public boolean isReadOnly() {
        return false;
    }
}
