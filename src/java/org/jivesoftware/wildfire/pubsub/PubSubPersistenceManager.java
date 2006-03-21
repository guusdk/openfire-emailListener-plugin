/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.pubsub;

import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.wildfire.pubsub.models.AccessModel;
import org.jivesoftware.wildfire.pubsub.models.PublisherModel;
import org.xmpp.packet.JID;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A manager responsible for ensuring node persistence.
 *
 * @author Matt Tucker
 */
public class PubSubPersistenceManager {

    private static final String LOAD_NODES =
            "SELECT nodeID, leaf, creationDate, modificationDate, parent, deliverPayloads, " +
            "maxPayloadSize, persistItems, maxItems, notifyConfigChanges, notifyDelete, " +
            "notifyRetract, presenceBased, sendItemSubscribe, publisherModel, " +
            "subscriptionEnabled, configSubscription, contacts, rosterGroups, accessModel, " +
            "payloadType, bodyXSLT, dataformXSLT, creator, description, language, name, " +
            "replyPolicy, replyRooms, replyTo, associationPolicy, associationTrusted, " +
            "maxLeafNodes FROM pubsubNode WHERE serviceID=? ORDER BY nodeID";
    private static final String UPDATE_NODE =
            "UPDATE pubsubNode SET modificationDate=?, parent=?, deliverPayloads=?, " +
            "maxPayloadSize=?, persistItems=?, maxItems=?, " +
            "notifyConfigChanges=?, notifyDelete=?, notifyRetract=?, presenceBased=?, " +
            "sendItemSubscribe=?, publisherModel=?, subscriptionEnabled=?, configSubscription=?, " +
            "contacts=?, rosterGroups=?, accessModel=?, payloadType=?, bodyXSLT=?, " +
            "dataformXSLT=?, description=?, language=?, name=?, replyPolicy=?, replyRooms=?, " +
            "replyTo=?, associationPolicy=?, associationTrusted=?, maxLeafNodes=? " +
            "WHERE serviceID=? AND nodeID=?";
    private static final String ADD_NODE =
            "INSERT INTO pubsubNode (serviceID, nodeID, leaf, creationDate, modificationDate, " +
            "parent, deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
            "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
            "sendItemSubscribe, publisherModel, subscriptionEnabled, configSubscription, " +
            "accessModel, contacts, rosterGroups, payloadType, bodyXSLT, dataformXSLT, " +
            "creator, description, language, name, replyPolicy, replyRooms, replyTo, " +
            "associationPolicy, associationTrusted, maxLeafNodes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String DELETE_NODE =
            "DELETE FROM pubsubNode WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_AFFILIATIONS =
            "SELECT nodeID,jid,affiliation FROM pubsubAffiliation WHERE serviceID=? " +
            "ORDER BY nodeID";
    private static final String ADD_AFFILIATION =
            "INSERT INTO pubsubAffiliation (serviceID,nodeID,jid,affiliation) VALUES (?,?,?,?)";
    private static final String UPDATE_AFFILIATION =
            "UPDATE pubsubAffiliation SET affiliation=? WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATION =
            "DELETE FROM pubsubAffiliation WHERE serviceID=? AND nodeID=? AND jid=?";
    private static final String DELETE_AFFILIATIONS =
            "DELETE FROM pubsubAffiliation WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_SUBSCRIPTIONS =
            "SELECT nodeID, id, jid, owner, state, confPending, deliver, digest, " +
            "digest_frequency, expire, includeBody, showValues, subscriptionType, " +
            "subscriptionDepth, keyword FROM pubsubSubscription WHERE serviceID=? ORDER BY nodeID";
    private static final String ADD_SUBSCRIPTION =
            "INSERT INTO pubsubSubscription (serviceID, nodeID, id, jid, owner, state, " +
            "confPending, deliver, digest, digest_frequency, expire, includeBody, showValues, " +
            "subscriptionType, subscriptionDepth, keyword) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SUBSCRIPTION =
            "UPDATE pubsubSubscription SET owner=?, state=?, confPending=? deliver=?, digest=?, " +
            "digest_frequency=?, expire=?, includeBody=?, showValues=?, subscriptionType=?, " +
            "subscriptionDepth=?, keyword=? WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_SUBSCRIPTION =
            "DELETE FROM pubsubSubscription WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_SUBSCRIPTIONS =
            "DELETE FROM pubsubSubscription WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_ITEMS =
            "SELECT id,jid,creationDate,payload FROM pubsubItem " +
            "WHERE serviceID=? AND nodeID=? ORDER BY creationDate";
    private static final String ADD_ITEM =
            "INSERT INTO pubsubItem (serviceID,nodeID,id,jid,creationDate,payload) " +
            "VALUES (?,?,?,?,?,?)";
    private static final String DELETE_ITEM =
            "DELETE FROM pubsubItem WHERE serviceID=? AND nodeID=? AND id=?";
    private static final String DELETE_ITEMS =
            "DELETE FROM pubsubItem WHERE serviceID=? AND nodeID=?";

    private static final String LOAD_DEFAULT_CONF =
            "SELECT deliverPayloads, maxPayloadSize, persistItems, maxItems, " +
            "notifyConfigChanges, notifyDelete, notifyRetract, presenceBased, " +
            "sendItemSubscribe, publisherModel, subscriptionEnabled, accessModel, language, " +
            "replyPolicy, associationPolicy, maxLeafNodes " +
            "FROM pubsubDefaultConf WHERE serviceID=? AND leaf=?";
    private static final String UPDATE_DEFAULT_CONF =
            "UPDATE pubsubDefaultConf SET deliverPayloads=?, maxPayloadSize=?, persistItems=?, " +
            "maxItems=?, notifyConfigChanges=?, notifyDelete=?, notifyRetract=?, " +
            "presenceBased=?, sendItemSubscribe=?, publisherModel=?, subscriptionEnabled=?, " +
            "accessModel=?, language=? replyPolicy=?, associationPolicy=?, maxLeafNodes=? " +
            "WHERE serviceID=? AND leaf=?";
    private static final String ADD_DEFAULT_CONF =
            "INSERT INTO pubsubDefaultConf (serviceID, leaf, deliverPayloads, maxPayloadSize, " +
            "persistItems, maxItems, notifyConfigChanges, notifyDelete, notifyRetract, " +
            "presenceBased, sendItemSubscribe, publisherModel, subscriptionEnabled, " +
            "accessModel, language, replyPolicy, associationPolicy, maxLeafNodes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private static BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<SAXReader>();

    static {
        // Initialize the pool of sax readers
        for (int i=0; i<50; i++) {
            xmlReaders.add(new SAXReader());
        }
    }

    /**
     * Creates and stores the node configuration in the database.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The newly created node.
     */
    public static void createNode(PubSubService service, Node node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_NODE);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.setInt(3, (node.isCollectionNode() ? 0 : 1));
            pstmt.setString(4, StringUtils.dateToMillis(node.getCreationDate()));
            pstmt.setString(5, StringUtils.dateToMillis(node.getModificationDate()));
            pstmt.setString(6, node.getParent() != null ? node.getParent().getNodeID() : null);
            pstmt.setInt(7, (node.isPayloadDelivered() ? 1 : 0));
            if (!node.isCollectionNode()) {
                pstmt.setInt(8, ((LeafNode) node).getMaxPayloadSize());
                pstmt.setInt(9, (((LeafNode) node).isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(10, ((LeafNode) node).getMaxPublishedItems());
            }
            else {
                pstmt.setInt(8, 0);
                pstmt.setInt(9, 0);
                pstmt.setInt(10, 0);
            }
            pstmt.setInt(11, (node.isNotifiedOfConfigChanges() ? 1 : 0));
            pstmt.setInt(12, (node.isNotifiedOfDelete() ? 1 : 0));
            pstmt.setInt(13, (node.isNotifiedOfRetract() ? 1 : 0));
            pstmt.setInt(14, (node.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(15, (node.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(16, node.getPublisherModel().getName());
            pstmt.setInt(17, (node.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setInt(18, (node.isSubscriptionConfigurationRequired() ? 1 : 0));
            pstmt.setString(19, node.getAccessModel().getName());
            pstmt.setString(20, encodeJIDs(node.getContacts()));
            pstmt.setString(21, encodeGroups(node.getRosterGroupsAllowed()));
            pstmt.setString(22, node.getPayloadType());
            pstmt.setString(23, node.getBodyXSLT());
            pstmt.setString(24, node.getDataformXSLT());
            pstmt.setString(25, node.getCreator().toString());
            pstmt.setString(26, node.getDescription());
            pstmt.setString(27, node.getLanguage());
            pstmt.setString(28, node.getName());
            if (node.getReplyPolicy() != null) {
                pstmt.setString(29, node.getReplyPolicy().name());
            }
            else {
                pstmt.setString(29, null);
            }
            pstmt.setString(30, encodeJIDs(node.getReplyRooms()));
            pstmt.setString(31, encodeJIDs(node.getReplyTo()));
            if (node.isCollectionNode()) {
                pstmt.setString(32, ((CollectionNode)node).getAssociationPolicy().name());
                pstmt.setString(33, encodeJIDs(((CollectionNode)node).getAssociationTrusted()));
                pstmt.setInt(34, ((CollectionNode)node).getMaxLeafNodes());
            }
            else {
                pstmt.setString(32, null);
                pstmt.setString(33, null);
                pstmt.setInt(34, 0);
            }
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Updates the node configuration in the database.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The updated node.
     */
    public static void updateNode(PubSubService service, Node node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_NODE);
            pstmt.setString(1, StringUtils.dateToMillis(node.getModificationDate()));
            pstmt.setString(2, node.getParent() != null ? node.getParent().getNodeID() : null);
            pstmt.setInt(3, (node.isPayloadDelivered() ? 1 : 0));
            if (!node.isCollectionNode()) {
                pstmt.setInt(4, ((LeafNode) node).getMaxPayloadSize());
                pstmt.setInt(5, (((LeafNode) node).isPersistPublishedItems() ? 1 : 0));
                pstmt.setInt(6, ((LeafNode) node).getMaxPublishedItems());
            }
            else {
                pstmt.setInt(4, 0);
                pstmt.setInt(5, 0);
                pstmt.setInt(6, 0);
            }
            pstmt.setInt(7, (node.isNotifiedOfConfigChanges() ? 1 : 0));
            pstmt.setInt(8, (node.isNotifiedOfDelete() ? 1 : 0));
            pstmt.setInt(9, (node.isNotifiedOfRetract() ? 1 : 0));
            pstmt.setInt(10, (node.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(11, (node.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(12, node.getPublisherModel().getName());
            pstmt.setInt(13, (node.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setInt(14, (node.isSubscriptionConfigurationRequired() ? 1 : 0));
            pstmt.setString(15, encodeJIDs(node.getContacts()));
            pstmt.setString(16, encodeGroups(node.getRosterGroupsAllowed()));
            pstmt.setString(17, node.getAccessModel().getName());
            pstmt.setString(18, node.getPayloadType());
            pstmt.setString(19, node.getBodyXSLT());
            pstmt.setString(20, node.getDataformXSLT());
            pstmt.setString(21, node.getDescription());
            pstmt.setString(22, node.getLanguage());
            pstmt.setString(23, node.getName());
            if (node.getReplyPolicy() != null) {
                pstmt.setString(24, node.getReplyPolicy().name());
            }
            else {
                pstmt.setString(24, null);
            }
            pstmt.setString(25, encodeJIDs(node.getReplyRooms()));
            pstmt.setString(26, encodeJIDs(node.getReplyTo()));
            if (node.isCollectionNode()) {
                pstmt.setString(27, ((CollectionNode) node).getAssociationPolicy().name());
                pstmt.setString(28, encodeJIDs(((CollectionNode) node).getAssociationTrusted()));
                pstmt.setInt(29, ((CollectionNode) node).getMaxLeafNodes());
            }
            else {
                pstmt.setString(27, null);
                pstmt.setString(28, null);
                pstmt.setInt(29, 0);
            }
            pstmt.setString(30, service.getServiceID());
            pstmt.setString(31, node.getNodeID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Removes the specified node from the DB.
     *
     * @param service The pubsub service that is hosting the node.
     * @param node The node that is being deleted.
     * @return true If the operation was successful.
     */
    public static boolean removeNode(PubSubService service, Node node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_NODE);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.executeUpdate();
            pstmt.close();

            // Remove published items of the node being deleted
            pstmt = con.prepareStatement(DELETE_ITEMS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.executeUpdate();
            pstmt.close();

            // Remove all affiliates from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATIONS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.executeUpdate();
            pstmt.close();

            // Remove users that were subscribed to the node
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTIONS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
            abortTransaction = true;
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        return !abortTransaction;
    }

    /**
     * Loads all nodes from the database and adds them to the PubSub service.
     *
     * @param service the pubsub service that is hosting the nodes.
     */
    public static void loadNodes(PubSubService service) {
        Connection con = null;
        PreparedStatement pstmt = null;
        Map<String, Node> nodes = new HashMap<String, Node>();
        try {
            con = DbConnectionManager.getConnection();
            // Get all nodes at once (with 1 query)
            pstmt = con.prepareStatement(LOAD_NODES);
            pstmt.setString(1, service.getServiceID());
            ResultSet rs = pstmt.executeQuery();
            // Rebuild all loaded nodes
            while(rs.next()) {
                loadNode(service, nodes, rs);
            }
            rs.close();
            pstmt.close();

            // Get affiliations of all nodes
            pstmt = con.prepareStatement(LOAD_AFFILIATIONS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the correspondiding affiliates
            while(rs.next()) {
                loadAffiliations(nodes, rs);
            }
            rs.close();
            pstmt.close();

            // Get subscriptions to all nodes
            pstmt = con.prepareStatement(LOAD_SUBSCRIPTIONS);
            pstmt.setString(1, service.getServiceID());
            rs = pstmt.executeQuery();
            // Add to each node the correspondiding subscriptions
            while(rs.next()) {
                loadSubscriptions(service, nodes, rs);
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }

        for (Node node : nodes.values()) {
            // Set now that the node is persistent in the database. Note: We need to
            // set this now since otherwise the node's affiliations will be saved to the database
            // "again" while adding them to the node!
            node.setSavedToDB(true);
            // Add the node to the service
            service.addNode(node);
        }
    }

    private static void loadNode(PubSubService service, Map<String, Node> loadedNodes,
            ResultSet rs) {
        Node node = null;
        try {
            String nodeID = rs.getString(1);
            boolean leaf = rs.getInt(2) == 1;
            String parent = rs.getString(5);
            JID creator = new JID(rs.getString(24));
            CollectionNode parentNode = null;
            if (parent != null) {
                // Check if the parent has already been loaded
                parentNode = (CollectionNode) loadedNodes.get(parent);
                if (parentNode == null) {
                    // Parent is not in memory so try to load it
                    Log.warn("Node not loaded due to missing parent. NodeID: " + nodeID);
                    return;
                }
            }

            if (leaf) {
                // Retrieving a leaf node
                node = new LeafNode(service, parentNode, nodeID, creator);
            }
            else {
                // Retrieving a collection node
                node = new CollectionNode(service, parentNode, nodeID, creator);
            }
            node.setCreationDate(new Date(Long.parseLong(rs.getString(3).trim())));
            node.setModificationDate(new Date(Long.parseLong(rs.getString(4).trim())));
            node.setPayloadDelivered(rs.getInt(6) == 1);
            if (leaf) {
                ((LeafNode) node).setMaxPayloadSize(rs.getInt(7));
                ((LeafNode) node).setPersistPublishedItems(rs.getInt(8) == 1);
                ((LeafNode) node).setMaxPublishedItems(rs.getInt(9));
                ((LeafNode) node).setSendItemSubscribe(rs.getInt(14) == 1);
            }
            node.setNotifiedOfConfigChanges(rs.getInt(10) == 1);
            node.setNotifiedOfDelete(rs.getInt(11) == 1);
            node.setNotifiedOfRetract(rs.getInt(12) == 1);
            node.setPresenceBasedDelivery(rs.getInt(13) == 1);
            node.setPublisherModel(PublisherModel.valueOf(rs.getString(15)));
            node.setSubscriptionEnabled(rs.getInt(16) == 1);
            node.setSubscriptionConfigurationRequired(rs.getInt(17) == 1);
            node.setContacts(decodeJIDs(rs.getString(18)));
            node.setRosterGroupsAllowed(decodeGroups(rs.getString(19)));
            node.setAccessModel(AccessModel.valueOf(rs.getString(20)));
            node.setPayloadType(rs.getString(21));
            node.setBodyXSLT(rs.getString(22));
            node.setDataformXSLT(rs.getString(23));
            node.setDescription(rs.getString(25));
            node.setLanguage(rs.getString(26));
            node.setName(rs.getString(27));
            if (rs.getString(28) != null) {
                node.setReplyPolicy(Node.ItemReplyPolicy.valueOf(rs.getString(28)));
            }
            node.setReplyRooms(decodeJIDs(rs.getString(29)));
            node.setReplyTo(decodeJIDs(rs.getString(30)));
            if (!leaf) {
                ((CollectionNode) node).setAssociationPolicy(
                        CollectionNode.LeafNodeAssociationPolicy.valueOf(rs.getString(31)));
                ((CollectionNode) node).setAssociationTrusted(decodeJIDs(rs.getString(32)));
                ((CollectionNode) node).setMaxLeafNodes(rs.getInt(33));
            }

            // Add the load to the list of loaded nodes
            loadedNodes.put(node.getNodeID(), node);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        return;
    }

    private static void loadAffiliations(Map<String, Node> nodes, ResultSet rs) {
        try {
            String nodeID = rs.getString(1);
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("Affiliations found for a non-existent node: " + nodeID);
                return;
            }
            NodeAffiliate affiliate = new NodeAffiliate(node, new JID(rs.getString(2)));
            affiliate.setAffiliation(NodeAffiliate.Affiliation.valueOf(rs.getString(3)));
            node.addAffiliate(affiliate);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
    }

    private static void loadSubscriptions(PubSubService service, Map<String, Node> nodes,
            ResultSet rs) {
        try {
            String nodeID = rs.getString(1);
            Node node = nodes.get(nodeID);
            if (node == null) {
                Log.warn("Subscription found for a non-existent node: " + nodeID);
                return;
            }
            String subID = rs.getString(2);
            JID subscriber = new JID(rs.getString(3));
            JID owner = new JID(rs.getString(4));
            NodeSubscription.State state = NodeSubscription.State.valueOf(rs.getString(5));
            NodeSubscription subscription =
                    new NodeSubscription(service, node, owner, subscriber, state, subID);
            subscription.setConfigurationPending(rs.getInt(6) == 1);
            subscription.setShouldDeliverNotifications(rs.getInt(7) == 1);
            subscription.setUsingDigest(rs.getInt(8) == 1);
            subscription.setDigestFrequency(rs.getInt(9));
            if (rs.getString(10) != null) {
                subscription.setExpire(new Date(Long.parseLong(rs.getString(10).trim())));
            }
            subscription.setIncludingBody(rs.getInt(11) == 1);
            subscription.setPresenceStates(decodeWithComma(rs.getString(12)));
            subscription.setType(NodeSubscription.Type.valueOf(rs.getString(13)));
            subscription.setDepth(rs.getInt(14));
            subscription.setKeyword(rs.getString(15));
            // Indicate the subscription that is has already been saved to the database
            subscription.setSavedToDB(true);
            node.addSubscription(subscription);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
    }

    /**
     * Update the DB with the new affiliation of the user in the node.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The new affiliation of the user in the node.
     * @param create    True if this is a new affiliate.
     */
    public static void saveAffiliation(PubSubService service, Node node, NodeAffiliate affiliate,
            boolean create) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (create) {
                // Add the user to the generic affiliations table
                pstmt = con.prepareStatement(ADD_AFFILIATION);
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, node.getNodeID());
                pstmt.setString(3, affiliate.getJID().toString());
                pstmt.setString(4, affiliate.getAffiliation().name());
                pstmt.executeUpdate();
            }
            else {
                if (NodeAffiliate.Affiliation.none == affiliate.getAffiliation()) {
                    // Remove the affiliate from the table of node affiliates
                    pstmt = con.prepareStatement(DELETE_AFFILIATION);
                    pstmt.setString(1, service.getServiceID());
                    pstmt.setString(2, node.getNodeID());
                    pstmt.setString(3, affiliate.getJID().toString());
                    pstmt.executeUpdate();
                }
                else {
                    // Update the affiliate's data in the backend store
                    pstmt = con.prepareStatement(UPDATE_AFFILIATION);
                    pstmt.setString(1, affiliate.getAffiliation().name());
                    pstmt.setString(2, service.getServiceID());
                    pstmt.setString(3, node.getNodeID());
                    pstmt.setString(4, affiliate.getJID().toString());
                    pstmt.executeUpdate();
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Removes the affiliation and subsription state of the user from the DB.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The existing affiliation and subsription state of the user in the node.
     */
    public static void removeAffiliation(PubSubService service, Node node,
            NodeAffiliate affiliate) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_AFFILIATION);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.setString(3, affiliate.getJID().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Updates the DB with the new subsription of the user to the node.
     *
     * @param service   The pubsub service that is hosting the node.
     * @param node      The node where the user has subscribed to.
     * @param subscription The new subscription of the user to the node.
     * @param create    True if this is a new affiliate.
     */
    public static void saveSubscription(PubSubService service, Node node,
            NodeSubscription subscription, boolean create) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (create) {
                // Add the subscription of the user to the database
                pstmt = con.prepareStatement(ADD_SUBSCRIPTION);
                pstmt.setString(1, service.getServiceID());
                pstmt.setString(2, node.getNodeID());
                pstmt.setString(3, subscription.getID());
                pstmt.setString(4, subscription.getJID().toString());
                pstmt.setString(5, subscription.getOwner().toString());
                pstmt.setString(6, subscription.getState().name());
                pstmt.setInt(7, (subscription.isConfigurationPending() ? 1 : 0));
                pstmt.setInt(8, (subscription.shouldDeliverNotifications() ? 1 : 0));
                pstmt.setInt(9, (subscription.isUsingDigest() ? 1 : 0));
                pstmt.setInt(10, subscription.getDigestFrequency());
                Date expireDate = subscription.getExpire();
                if (expireDate == null) {
                    pstmt.setString(11, null);
                }
                else {
                    pstmt.setString(11, StringUtils.dateToMillis(expireDate));
                }
                pstmt.setInt(12, (subscription.isIncludingBody() ? 1 : 0));
                pstmt.setString(13, encodeWithComma(subscription.getPresenceStates()));
                pstmt.setString(14, subscription.getType().name());
                pstmt.setInt(15, subscription.getDepth());
                pstmt.setString(16, subscription.getKeyword());
                pstmt.executeUpdate();
                // Indicate the subscription that is has been saved to the database
                subscription.setSavedToDB(true);
            }
            else {
                if (NodeSubscription.State.none == subscription.getState()) {
                    // Remove the subscription of the user from the table
                    pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
                    pstmt.setString(1, service.getServiceID());
                    pstmt.setString(2, node.getNodeID());
                    pstmt.setString(2, subscription.getID());
                    pstmt.executeUpdate();
                }
                else {
                    // Update the subscription of the user in the backend store
                    pstmt = con.prepareStatement(UPDATE_SUBSCRIPTION);
                    pstmt.setString(1, subscription.getOwner().toString());
                    pstmt.setString(2, subscription.getState().name());
                    pstmt.setInt(3, (subscription.isConfigurationPending() ? 1 : 0));
                    pstmt.setInt(4, (subscription.shouldDeliverNotifications() ? 1 : 0));
                    pstmt.setInt(5, (subscription.isUsingDigest() ? 1 : 0));
                    pstmt.setInt(6, subscription.getDigestFrequency());
                    Date expireDate = subscription.getExpire();
                    if (expireDate == null) {
                        pstmt.setString(7, null);
                    }
                    else {
                        pstmt.setString(7, StringUtils.dateToMillis(expireDate));
                    }
                    pstmt.setInt(8, (subscription.isIncludingBody() ? 1 : 0));
                    pstmt.setString(9, encodeWithComma(subscription.getPresenceStates()));
                    pstmt.setString(10, subscription.getType().name());
                    pstmt.setInt(11, subscription.getDepth());
                    pstmt.setString(12, subscription.getKeyword());
                    pstmt.setString(13, service.getServiceID());
                    pstmt.setString(14, node.getNodeID());
                    pstmt.setString(15, subscription.getID());
                    pstmt.executeUpdate();
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Removes the subscription of the user from the DB.
     *
     * @param service     The pubsub service that is hosting the node.
     * @param node        The node where the user was subscribed to.
     * @param subscription The existing subsription of the user to the node.
     */
    public static void removeSubscription(PubSubService service, Node node,
            NodeSubscription subscription) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the affiliate from the table of node affiliates
            pstmt = con.prepareStatement(DELETE_SUBSCRIPTION);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            pstmt.setString(3, subscription.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Loads and adds the published items to the specified node.
     *
     * @param service the pubsub service that is hosting the node.
     * @param node the leaf node to load its published items.
     */
    public static void loadItems(PubSubService service, LeafNode node) {
        Connection con = null;
        PreparedStatement pstmt = null;
        SAXReader xmlReader = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();
            con = DbConnectionManager.getConnection();
            // Get published items of the specified node
            pstmt = con.prepareStatement(LOAD_ITEMS);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, node.getNodeID());
            ResultSet rs = pstmt.executeQuery();
            // Rebuild loaded published items
            while(rs.next()) {
                String itemID = rs.getString(1);
                JID publisher = new JID(rs.getString(2));
                Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
                // Create the item
                PublishedItem item = new PublishedItem(node, publisher, itemID, creationDate);
                // Add the extra fields to the published item
                if (rs.getString(4) != null) {
                    item.setPayload(
                            xmlReader.read(new StringReader(rs.getString(4))).getRootElement());
                }
                // Add the published item to the node
                node.addPublishedItem(item);
            }
            rs.close();
        }
        catch (Exception sqle) {
            Log.error(sqle);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) {
                xmlReaders.add(xmlReader);
            }
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Creates and stores the published item in the database.
     *
     * @param service the pubsub service that is hosting the node.
     * @param item The published item to save.
     * @return true if the item was successfully saved to the database.
     */
    public static boolean createPublishedItem(PubSubService service, PublishedItem item) {
        boolean success = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the published item from the database
            pstmt = con.prepareStatement(ADD_ITEM);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, item.getNode().getNodeID());
            pstmt.setString(3, item.getID());
            pstmt.setString(4, item.getPublisher().toString());
            pstmt.setString(5, StringUtils.dateToMillis(item.getCreationDate()));
            pstmt.setString(6, item.getPayloadXML());
            pstmt.executeUpdate();
            // Set that the item was successfully saved to the database
            success = true;
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
        return success;
    }

    /**
     * Removes the specified published item from the DB.
     *
     * @param service the pubsub service that is hosting the node.
     * @param item The published item to delete.
     * @return true if the item was successfully deleted from the database.
     */
    public static boolean removePublishedItem(PubSubService service, PublishedItem item) {
        boolean success = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the published item from the database
            pstmt = con.prepareStatement(DELETE_ITEM);
            pstmt.setString(1, service.getServiceID());
            pstmt.setString(2, item.getNode().getNodeID());
            pstmt.setString(3, item.getID());
            pstmt.executeUpdate();
            // Set that the item was successfully deleted from the database
            success = true;
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
        return success;
    }

    /**
     * Loads from the database the default node configuration for the specified node type
     * and pubsub service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param isLeafType true if loading default configuration for leaf nodes.
     * @return the loaded default node configuration for the specified node type and service
     *         or <tt>null</tt> if none was found.
     */
    public static DefaultNodeConfiguration loadDefaultConfiguration(PubSubService service,
            boolean isLeafType) {
        Connection con = null;
        PreparedStatement pstmt = null;
        DefaultNodeConfiguration config = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get default node configuration for the specified service
            pstmt = con.prepareStatement(LOAD_DEFAULT_CONF);
            pstmt.setString(1, service.getServiceID());
            pstmt.setInt(2, (isLeafType ? 1 : 0));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                config = new DefaultNodeConfiguration(isLeafType);
                // Rebuild loaded default node configuration
                config.setDeliverPayloads(rs.getInt(1) == 1);
                config.setMaxPayloadSize(rs.getInt(2));
                config.setPersistPublishedItems(rs.getInt(3) == 1);
                config.setMaxPublishedItems(rs.getInt(4));
                config.setNotifyConfigChanges(rs.getInt(5) == 1);
                config.setNotifyDelete(rs.getInt(6) == 1);
                config.setNotifyRetract(rs.getInt(7) == 1);
                config.setPresenceBasedDelivery(rs.getInt(8) == 1);
                config.setSendItemSubscribe(rs.getInt(9) == 1);
                config.setPublisherModel(PublisherModel.valueOf(rs.getString(10)));
                config.setSubscriptionEnabled(rs.getInt(11) == 1);
                config.setAccessModel(AccessModel.valueOf(rs.getString(12)));
                config.setLanguage(rs.getString(13));
                if (rs.getString(14) != null) {
                    config.setReplyPolicy(Node.ItemReplyPolicy.valueOf(rs.getString(14)));
                }
                config.setAssociationPolicy(
                        CollectionNode.LeafNodeAssociationPolicy.valueOf(rs.getString(15)));
                config.setMaxLeafNodes(rs.getInt(16));
            }
            rs.close();
        }
        catch (Exception sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return config;
    }

    /**
     * Creates a new default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to create in the database.
     */
    public static void createDefaultConfiguration(PubSubService service,
            DefaultNodeConfiguration config) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_DEFAULT_CONF);
            pstmt.setString(1, service.getServiceID());
            pstmt.setInt(2, (config.isLeaf() ? 1 : 0));
            pstmt.setInt(3, (config.isDeliverPayloads() ? 1 : 0));
            pstmt.setInt(4, config.getMaxPayloadSize());
            pstmt.setInt(5, (config.isPersistPublishedItems() ? 1 : 0));
            pstmt.setInt(6, config.getMaxPublishedItems());
            pstmt.setInt(7, (config.isNotifyConfigChanges() ? 1 : 0));
            pstmt.setInt(8, (config.isNotifyDelete() ? 1 : 0));
            pstmt.setInt(9, (config.isNotifyRetract() ? 1 : 0));
            pstmt.setInt(10, (config.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(11, (config.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(12, config.getPublisherModel().getName());
            pstmt.setInt(13, (config.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setString(14, config.getAccessModel().getName());
            pstmt.setString(15, config.getLanguage());
            if (config.getReplyPolicy() != null) {
                pstmt.setString(16, config.getReplyPolicy().name());
            }
            else {
                pstmt.setString(16, null);
            }
            pstmt.setString(17, config.getAssociationPolicy().name());
            pstmt.setInt(18, config.getMaxLeafNodes());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /**
     * Updates the default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to update in the database.
     */
    public static void updateDefaultConfiguration(PubSubService service,
            DefaultNodeConfiguration config) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_DEFAULT_CONF);
            pstmt.setInt(1, (config.isDeliverPayloads() ? 1 : 0));
            pstmt.setInt(2, config.getMaxPayloadSize());
            pstmt.setInt(3, (config.isPersistPublishedItems() ? 1 : 0));
            pstmt.setInt(4, config.getMaxPublishedItems());
            pstmt.setInt(5, (config.isNotifyConfigChanges() ? 1 : 0));
            pstmt.setInt(6, (config.isNotifyDelete() ? 1 : 0));
            pstmt.setInt(7, (config.isNotifyRetract() ? 1 : 0));
            pstmt.setInt(8, (config.isPresenceBasedDelivery() ? 1 : 0));
            pstmt.setInt(9, (config.isSendItemSubscribe() ? 1 : 0));
            pstmt.setString(10, config.getPublisherModel().getName());
            pstmt.setInt(11, (config.isSubscriptionEnabled() ? 1 : 0));
            pstmt.setString(12, config.getAccessModel().getName());
            pstmt.setString(13, config.getLanguage());
            if (config.getReplyPolicy() != null) {
                pstmt.setString(14, config.getReplyPolicy().name());
            }
            else {
                pstmt.setString(14, null);
            }
            pstmt.setString(15, config.getAssociationPolicy().name());
            pstmt.setInt(16, config.getMaxLeafNodes());
            pstmt.setString(17, service.getServiceID());
            pstmt.setInt(18, (config.isLeaf() ? 1 : 0));
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {if (pstmt != null) {pstmt.close();}}
            catch (Exception e) {Log.error(e);}
            try {if (con != null) {con.close();}}
            catch (Exception e) {Log.error(e);}
        }
    }

    /*public static Node loadNode(PubSubService service, String nodeID) {
        Connection con = null;
        Node node = null;
        try {
            con = DbConnectionManager.getConnection();
            node = loadNode(service, nodeID, con);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return node;
    }

    private static Node loadNode(PubSubService service, String nodeID, Connection con) {
        Node node = null;
        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(LOAD_NODE);
            pstmt.setString(1, nodeID);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                // No node was found for the specified nodeID so return null
                return null;
            }
            boolean leaf = rs.getInt(1) == 1;
            String parent = rs.getString(4);
            JID creator = new JID(rs.getString(20));
            CollectionNode parentNode = null;
            if (parent != null) {
                // Check if the parent has already been loaded
                parentNode = (CollectionNode) service.getNode(parent);
                if (parentNode == null) {
                    // Parent is not in memory so try to load it
                    synchronized (parent.intern()) {
                        // Check again if parent has not been already loaded (concurrency issues)
                        parentNode = (CollectionNode) service.getNode(parent);
                        if (parentNode == null) {
                            // Parent was never loaded so load it from the database now
                            parentNode = (CollectionNode) loadNode(service, parent, con);
                        }
                    }
                }
            }

            if (leaf) {
                // Retrieving a leaf node
                node = new LeafNode(parentNode, nodeID, creator);
            }
            else {
                // Retrieving a collection node
                node = new CollectionNode(parentNode, nodeID, creator);
            }
            node.setCreationDate(new Date(Long.parseLong(rs.getString(2).trim())));
            node.setModificationDate(new Date(Long.parseLong(rs.getString(3).trim())));
            node.setPayloadDelivered(rs.getInt(5) == 1);
            node.setMaxPayloadSize(rs.getInt(6));
            node.setPersistPublishedItems(rs.getInt(7) == 1);
            node.setMaxPublishedItems(rs.getInt(8));
            node.setNotifiedOfConfigChanges(rs.getInt(9) == 1);
            node.setNotifiedOfDelete(rs.getInt(10) == 1);
            node.setNotifiedOfRetract(rs.getInt(11) == 1);
            node.setPresenceBasedDelivery(rs.getInt(12) == 1);
            node.setSendItemSubscribe(rs.getInt(13) == 1);
            node.setPublisherModel(Node.PublisherModel.valueOf(rs.getString(14)));
            node.setSubscriptionEnabled(rs.getInt(15) == 1);
            node.setAccessModel(Node.AccessModel.valueOf(rs.getString(16)));
            node.setPayloadType(rs.getString(17));
            node.setBodyXSLT(rs.getString(18));
            node.setDataformXSLT(rs.getString(19));
            node.setDescription(rs.getString(21));
            node.setLanguage(rs.getString(22));
            node.setName(rs.getString(23));
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_HISTORY);
            // Recreate the history until two days ago
            long from = System.currentTimeMillis() - (86400000 * 2);
            pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
            pstmt.setLong(2, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                // Recreate the history only for the rooms that have the conversation logging
                // enabled
                if (room.isLogEnabled()) {
                    room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject,
                            body);
                }
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_NODE_AFFILIATIONS);
            pstmt.setString(1, node.getNodeID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                NodeAffiliate affiliate = new NodeAffiliate(new JID(rs.getString(1)));
                affiliate.setAffiliation(NodeAffiliate.Affiliation.valueOf(rs.getString(2)));
                affiliate.setSubscription(NodeAffiliate.State.valueOf(rs.getString(3)));
                node.addAffiliate(affiliate);
            }
            rs.close();

            // Set now that the room's configuration is updated in the database. Note: We need to
            // set this now since otherwise the room's affiliations will be saved to the database
            // "again" while adding them to the room!
            node.setSavedToDB(true);

            // Add the retrieved node to the pubsub service
            service.addChildNode(node);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return node;
    }*/

    private static String encodeJIDs(Collection<JID> jids) {
        StringBuilder sb = new StringBuilder(90);
        for (JID jid : jids) {
            sb.append(jid.toString()).append(",");
        }
        if (!jids.isEmpty()) {
            sb.setLength(sb.length()-1);
        }
        return sb.toString();
    }

    private static Collection<JID> decodeJIDs(String jids) {
        Collection<JID> decodedJIDs = new ArrayList<JID>();
        StringTokenizer tokenizer = new StringTokenizer(jids, ",");
        while (tokenizer.hasMoreTokens()) {
            decodedJIDs.add(new JID(tokenizer.nextToken()));
        }
        return decodedJIDs;
    }

    private static String encodeGroups(Collection<String> groups) {
        StringBuilder sb = new StringBuilder(90);
        for (String group : groups) {
            sb.append(group).append("\u2008");
        }
        if (!groups.isEmpty()) {
            sb.setLength(sb.length()-1);
        }
        return sb.toString();
    }

    private static Collection<String> decodeGroups(String groups) {
        Collection<String> decodedGroups = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(groups, "\u2008");
        while (tokenizer.hasMoreTokens()) {
            decodedGroups.add(tokenizer.nextToken());
        }
        return decodedGroups;
    }

    private static String encodeWithComma(Collection<String> strings) {
        StringBuilder sb = new StringBuilder(90);
        for (String group : strings) {
            sb.append(group).append(",");
        }
        if (!strings.isEmpty()) {
            sb.setLength(sb.length()-1);
        }
        return sb.toString();
    }

    private static Collection<String> decodeWithComma(String strings) {
        Collection<String> decodedStrings = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(strings, ",");
        while (tokenizer.hasMoreTokens()) {
            decodedStrings.add(tokenizer.nextToken());
        }
        return decodedStrings;
    }
}
