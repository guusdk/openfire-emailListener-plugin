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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.RoutableChannelHandler;
import org.jivesoftware.wildfire.RoutingTable;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.disco.DiscoInfoProvider;
import org.jivesoftware.wildfire.disco.DiscoItemsProvider;
import org.jivesoftware.wildfire.disco.DiscoServerItem;
import org.jivesoftware.wildfire.disco.ServerItemsProvider;
import org.jivesoftware.wildfire.forms.DataForm;
import org.jivesoftware.wildfire.forms.spi.XDataFormImpl;
import org.jivesoftware.wildfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.wildfire.pubsub.models.AccessModel;
import org.jivesoftware.wildfire.pubsub.models.PublisherModel;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Module that implements JEP-60: Publish-Subscribe. By default node collections and
 * instant nodes are supported.
 *
 * @author Matt Tucker
 */
public class PubSubModule extends BasicModule implements ServerItemsProvider, DiscoInfoProvider,
        DiscoItemsProvider, RoutableChannelHandler, PubSubService {

    /**
     * the chat service's hostname
     */
    private String serviceName = null;

    /**
     * Collection node that acts as the root node of the entire node hierarchy.
     */
    private CollectionNode rootCollectionNode = null;

    /**
     * Nodes managed by this manager, table: key nodeID (String); value Node
     */
    private Map<String, Node> nodes = new ConcurrentHashMap<String, Node>();
    /**
     * Returns the permission policy for creating nodes. A true value means that not anyone can
     * create a node, only the JIDs listed in <code>allowedToCreate</code> are allowed to create
     * nodes.
     */
    private boolean nodeCreationRestricted = false;

    /**
     * Bare jids of users that are allowed to create nodes. An empty list means that anyone can
     * create nodes.
     */
    private Collection<String> allowedToCreate = new CopyOnWriteArrayList<String>();

    /**
     * Bare jids of users that are system administrators of the PubSub service. A sysadmin
     * has the same permissions as a node owner.
     */
    private Collection<String> sysadmins = new CopyOnWriteArrayList<String>();

    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;

    private RoutingTable routingTable = null;

    /**
     * Default configuration to use for newly created leaf nodes.
     */
    private DefaultNodeConfiguration leafDefaultConfiguration;
    /**
     * Default configuration to use for newly created collection nodes.
     */
    private DefaultNodeConfiguration collectionDefaultConfiguration;

    /**
     * Private component that actually performs the pubsub work.
     */
    private PubSubEngine engine = null;

    /**
     * Keep a registry of the presence's show value of users that subscribed to a node of
     * the pubsub service and for which the node only delivers notifications for online users
     * or node subscriptions deliver events based on the user presence show value. Offline
     * users will not have an entry in the map. Note: Key-> bare JID and Value-> Map whose key
     * is full JID of connected resource and value is show value of the last received presence.
     */
    private Map<String, Map<String, String>> barePresences =
            new ConcurrentHashMap<String, Map<String, String>>();

    public PubSubModule() {
        super("Publish Subscribe Service");
    }

    public void process(Packet packet) {
        // TODO Remove this method when moving PubSub as a component and removing module code
        // The MUC service will receive all the packets whose domain matches the domain of the MUC
        // service. This means that, for instance, a disco request should be responded by the
        // service itself instead of relying on the server to handle the request.
        try {
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (!engine.process((IQ) packet)) {
                    process((IQ) packet);
                }
            }
            else if (packet instanceof Presence) {
                engine.process((Presence) packet);
            }
            else {
                engine.process((Message) packet);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            if (packet instanceof IQ) {
                // Send internal server error
                IQ reply = IQ.createResultIQ((IQ) packet);
                reply.setError(PacketError.Condition.internal_server_error);
                send(reply);
            }
        }
    }

    private void process(IQ iq) {
        // Ignore IQs of type ERROR
        if (IQ.Type.error == iq.getType()) {
            return;
        }
        Element childElement = iq.getChildElement();
        String namespace = null;

        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }
        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            try {
                // TODO PubSub should have an IQDiscoInfoHandler of its own when PubSub becomes
                // a component
                IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
                router.route(reply);
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            try {
                // TODO PubSub should have an IQDiscoItemsHandler of its own when PubSub becomes
                // a component
                IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
                router.route(reply);
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        }
        else {
            //  TODO Handle unknown namespace
        }
    }

    public String getServiceID() {
        return "pubsub";
    }

    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        if (isNodeCreationRestricted() && !isServiceAdmin(creator)) {
            // The user is not allowed to create nodes
            return false;
        }
        // TODO Check that the user is not an anonymous user
        return true;
    }

    public boolean isServiceAdmin(JID user) {
        return sysadmins.contains(user.toBareJID()) || allowedToCreate.contains(user.toBareJID());
    }

    public boolean isInstantNodeSupported() {
        return true;
    }

    public boolean isCollectionNodesSupported() {
        return true;
    }

    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
    }

    public DefaultNodeConfiguration getDefaultNodeConfiguration(boolean leafType) {
        if (leafType) {
            return leafDefaultConfiguration;
        }
        return collectionDefaultConfiguration;
    }

    public Collection<String> getShowPresences(JID subscriber) {
        Map<String, String> fullPresences = barePresences.get(subscriber.toBareJID());
        if (fullPresences == null) {
            // User is offline so return empty list
            return Collections.emptyList();
        }
        if (subscriber.getResource() == null) {
            // Subscriber used bared JID so return show value of all connected resources
            return fullPresences.values();
        }
        else {
            // Look for the show value using the full JID
            String show = fullPresences.get(subscriber.toString());
            if (show == null) {
                // User at the specified resource is offline so return empty list
                return Collections.emptyList();
            }
            // User is connected at specified resource so answer list with presence show value
            return Arrays.asList(show);
        }
    }

    public void presenceSubscriptionNotRequired(Node node, JID user) {
        // TODO Implement this
    }

    public void presenceSubscriptionRequired(Node node, JID user) {
        Map<String, String> fullPresences = barePresences.get(user.toString());
        if (fullPresences == null || fullPresences.isEmpty()) {
            Presence subscription = new Presence(Presence.Type.subscribe);
            subscription.setTo(user);
            subscription.setFrom(getAddress());
            send(subscription);
        }
    }

    public PubSubEngine getPubSubEngine() {
        return engine;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getName();
    }

    public JID getAddress() {
        // TODO Cache this JID for performance?
        return new JID(null, getServiceDomain(), null);
    }

    public Collection<String> getUsersAllowedToCreate() {
        return allowedToCreate;
    }

    public Collection<String> getSysadmins() {
        return sysadmins;
    }

    public void addSysadmin(String userJID) {
        sysadmins.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.sysadmin.jid", fromArray(jids));
    }

    public void removeSysadmin(String userJID) {
        sysadmins.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.sysadmin.jid", fromArray(jids));
    }

    public boolean isNodeCreationRestricted() {
        return nodeCreationRestricted;
    }

    public void setNodeCreationRestricted(boolean nodeCreationRestricted) {
        this.nodeCreationRestricted = nodeCreationRestricted;
        JiveGlobals.setProperty("xmpp.pubsub.create.anyone", Boolean.toString(nodeCreationRestricted));
    }

    public void addUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create nodes.
        allowedToCreate.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.create.jid", fromArray(jids));
    }

    public void removeUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create nodes.
        allowedToCreate.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.create.jid", fromArray(jids));
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        serviceName = JiveGlobals.getProperty("xmpp.pubsub.service");
        if (serviceName == null) {
            serviceName = "pubsub";
        }
        // Load the list of JIDs that are sysadmins of the PubSub service
        String property = JiveGlobals.getProperty("xmpp.pubsub.sysadmin.jid");
        String[] jids;
        if (property != null) {
            jids = property.split(",");
            for (int i = 0; i < jids.length; i++) {
                sysadmins.add(jids[i].trim().toLowerCase());
            }
        }
        nodeCreationRestricted =
                Boolean.parseBoolean(JiveGlobals.getProperty("xmpp.pubsub.create.anyone", "false"));
        // Load the list of JIDs that are allowed to create nodes
        property = JiveGlobals.getProperty("xmpp.pubsub.create.jid");
        if (property != null) {
            jids = property.split(",");
            for (int i = 0; i < jids.length; i++) {
                allowedToCreate.add(jids[i].trim().toLowerCase());
            }
        }

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        engine = new PubSubEngine(this, server.getPacketRouter());

        // Load default configuration for leaf nodes
        leafDefaultConfiguration = PubSubPersistenceManager.loadDefaultConfiguration(this, true);
        if (leafDefaultConfiguration == null) {
            // Create and save default configuration for leaf nodes;
            leafDefaultConfiguration = new DefaultNodeConfiguration(true);
            leafDefaultConfiguration.setAccessModel(AccessModel.open);
            leafDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            leafDefaultConfiguration.setDeliverPayloads(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.deliverPayloads", false));
            leafDefaultConfiguration.setLanguage(
                    JiveGlobals.getProperty("xmpp.pubsub.default.language", "English"));
            leafDefaultConfiguration.setMaxPayloadSize(
                    JiveGlobals.getIntProperty("xmpp.pubsub.default.language", 5120));
            leafDefaultConfiguration.setNotifyConfigChanges(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.notify.configChanges", true));
            leafDefaultConfiguration.setNotifyDelete(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.notify.delete", true));
            leafDefaultConfiguration.setNotifyRetract(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.notify.retract", true));
            leafDefaultConfiguration.setPersistPublishedItems(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.persistItems", false));
            leafDefaultConfiguration.setMaxPublishedItems(
                    JiveGlobals.getIntProperty("xmpp.pubsub.default.maxPublishedItems", -1));
            leafDefaultConfiguration.setPresenceBasedDelivery(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.presenceBasedDelivery", false));
            leafDefaultConfiguration.setSendItemSubscribe(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.sendItemSubscribe", true));
            leafDefaultConfiguration.setSubscriptionEnabled(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.subscriptionEnabled", true));
            leafDefaultConfiguration.setReplyPolicy(null);
            PubSubPersistenceManager.createDefaultConfiguration(this, leafDefaultConfiguration);
        }
        // Load default configuration for collection nodes
        collectionDefaultConfiguration =
                PubSubPersistenceManager.loadDefaultConfiguration(this, false);
        if (collectionDefaultConfiguration == null ) {
            // Create and save default configuration for collection nodes;
            collectionDefaultConfiguration = new DefaultNodeConfiguration(false);
            collectionDefaultConfiguration.setAccessModel(AccessModel.open);
            collectionDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            collectionDefaultConfiguration.setDeliverPayloads(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.deliverPayloads", false));
            collectionDefaultConfiguration.setLanguage(
                    JiveGlobals.getProperty("xmpp.pubsub.default.language", "English"));
            collectionDefaultConfiguration.setNotifyConfigChanges(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.notify.configChanges", true));
            collectionDefaultConfiguration.setNotifyDelete(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.notify.delete", true));
            collectionDefaultConfiguration.setNotifyRetract(
                    JiveGlobals.getBooleanProperty("xmpp.pubsub.default.notify.retract", true));
            collectionDefaultConfiguration.setPresenceBasedDelivery(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.presenceBasedDelivery", false));
            collectionDefaultConfiguration.setSubscriptionEnabled(JiveGlobals.getBooleanProperty(
                    "xmpp.pubsub.default.subscriptionEnabled", true));
            leafDefaultConfiguration.setReplyPolicy(null);
            leafDefaultConfiguration
                    .setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy.all);
            leafDefaultConfiguration.setMaxLeafNodes(
                    JiveGlobals.getIntProperty("xmpp.pubsub.default.maxLeafNodes", -1));
            PubSubPersistenceManager
                    .createDefaultConfiguration(this, collectionDefaultConfiguration);
        }

        // Load nodes to memory
        PubSubPersistenceManager.loadNodes(this);
        // Ensure that we have a root collection node
        String rootNodeID = JiveGlobals.getProperty("xmpp.pubsub.root.nodeID", "");
        if (nodes.isEmpty()) {
            // Create root collection node
            String creator = JiveGlobals.getProperty("xmpp.pubsub.root.creator");
            JID creatorJID = creator != null ? new JID(creator) : server.getAdmins().iterator().next();
            rootCollectionNode = new CollectionNode(this, null, rootNodeID, creatorJID);
            // Add the creator as the node owner
            rootCollectionNode.addOwner(creatorJID);
            // Save new root node
            rootCollectionNode.saveToDB();
        }
        else {
            rootCollectionNode = (CollectionNode) getNode(rootNodeID);
        }
    }

    public void start() {
        super.start();
        // Add the route to this service
        routingTable.addRoute(getAddress(), this);
        // TODO Probe presences of users that this service has subscribed to
        ArrayList<String> params = new ArrayList<String>();
        params.clear();
        params.add(getServiceDomain());
        Log.info(LocaleUtils.getLocalizedString("startup.starting.pubsub", params));
    }

    public void stop() {
        super.stop();
        // Remove the route to this service
        routingTable.removeRoute(getAddress());
        // Stop the pubsub engine. This will gives us the chance to
        // save queued items to the database.
        engine.shutdown();
    }

    public Iterator<DiscoServerItem> getItems() {
        ArrayList<DiscoServerItem> items = new ArrayList<DiscoServerItem>();

        items.add(new DiscoServerItem() {
            public String getJID() {
                return getServiceDomain();
            }

            public String getName() {
                return "Publish-Subscribe service";
            }

            public String getAction() {
                return null;
            }

            public String getNode() {
                return null;
            }

            public DiscoInfoProvider getDiscoInfoProvider() {
                return PubSubModule.this;
            }

            public DiscoItemsProvider getDiscoItemsProvider() {
                return PubSubModule.this;
            }
        });
        return items.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        ArrayList<Element> identities = new ArrayList<Element>();
        if (name == null && node == null) {
            // Answer the identity of the PubSub service
            Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "pubsub");
            identity.addAttribute("name", "Publish-Subscribe service");
            identity.addAttribute("type", "service");

            identities.add(identity);
        }
        else if (name == null && node != null) {
            // Answer the identity of a given node
            Node pubNode = getNode(node);
            if (node != null && canDiscoverNode(pubNode)) {
                Element identity = DocumentHelper.createElement("identity");
                identity.addAttribute("category", "pubsub");
                identity.addAttribute("type", pubNode.isCollectionNode() ? "collection" : "leaf");

                identities.add(identity);
            }
        }
        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        ArrayList<String> features = new ArrayList<String>();
        if (name == null && node == null) {
            // Answer the features of the PubSub service
            features.add("http://jabber.org/protocol/pubsub");
            if (isCollectionNodesSupported()) {
                // Collection nodes are supported
                features.add("http://jabber.org/protocol/pubsub#collections");
            }
            // Configuration of node options is supported
            features.add("http://jabber.org/protocol/pubsub#config-node");
            // Simultaneous creation and configuration of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#create-and-configure");
            // Creation of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#create-nodes");
            // Deletion of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#delete-nodes");
            // TODO Retrieval of pending subscription approvals is supported
            //features.add("http://jabber.org/protocol/pubsub#get-pending");
            if (isInstantNodeSupported()) {
                // Creation of instant nodes is supported
                features.add("http://jabber.org/protocol/pubsub#instant-nodes");
            }
            // Publishers may specify item identifiers
            features.add("http://jabber.org/protocol/pubsub#item-ids");
            // TODO Time-based subscriptions are supported (clean up thread missing, rest is supported)
            //features.add("http://jabber.org/protocol/pubsub#leased-subscription");
            // Node meta-data is supported
            features.add("http://jabber.org/protocol/pubsub#meta-data");
            // Node owners may modify affiliations
            features.add("http://jabber.org/protocol/pubsub#modify-affiliations");
            // A single entity may subscribe to a node multiple times
            features.add("http://jabber.org/protocol/pubsub#multi-subscribe");
            // The outcast affiliation is supported
            features.add("http://jabber.org/protocol/pubsub#outcast-affiliation");
            // Persistent items are supported
            features.add("http://jabber.org/protocol/pubsub#persistent-items");
            // Presence-based delivery of event notifications is supported
            features.add("http://jabber.org/protocol/pubsub#presence-notifications");
            // Publishing items is supported (note: not valid for collection nodes)
            features.add("http://jabber.org/protocol/pubsub#publish");
            // The publisher affiliation is supported
            features.add("http://jabber.org/protocol/pubsub#publisher-affiliation");
            // Purging of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#purge-nodes");
            // Item retraction is supported
            features.add("http://jabber.org/protocol/pubsub#retract-items");
            // Retrieval of current affiliations is supported
            features.add("http://jabber.org/protocol/pubsub#retrieve-affiliations");
            // Item retrieval is supported
            features.add("http://jabber.org/protocol/pubsub#retrieve-items");
            // Subscribing and unsubscribing are supported
            features.add("http://jabber.org/protocol/pubsub#subscribe");
            // Configuration of subscription options is supported
            features.add("http://jabber.org/protocol/pubsub#subscription-options");
            // Default access model for nodes created on the service
            String modelName = getDefaultNodeConfiguration(true).getAccessModel().getName();
            features.add("http://jabber.org/protocol/pubsub#default_access_model_" + modelName);

        }
        else if (name == null && node != null) {
            // Answer the features of a given node
            Node pubNode = getNode(node);
            if (node != null && canDiscoverNode(pubNode)) {
                // Answer the features of the PubSub service
                features.add("http://jabber.org/protocol/pubsub");
            }
        }
        return features.iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        if (name == null && node != null) {
            // Answer the extended info of a given node
            Node pubNode = getNode(node);
            if (node != null && canDiscoverNode(pubNode)) {
                // Get the metadata data form
                org.xmpp.forms.DataForm metadataForm = pubNode.getMetadataForm();

                // Convert Whack data form into old data form format (will go away someday)
                XDataFormImpl dataForm = new XDataFormImpl(DataForm.TYPE_RESULT);
                for (org.xmpp.forms.FormField formField : metadataForm.getFields()) {
                    XFormFieldImpl field = new XFormFieldImpl(formField.getVariable());
                    field.setLabel(formField.getLabel());
                    for (String value : formField.getValues()) {
                        field.addValue(value);
                    }
                    dataForm.addField(field);
                }

                return dataForm;
            }
        }
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        if (name == null && node == null) {
            // We always have info about the Pubsub service
            return true;
        }
        else if (name == null && node != null) {
            // We only have info if the node exists
            return hasNode(node);
        }
        return false;
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID) {
        List<Element> answer = new ArrayList<Element>();
        String serviceDomain = getServiceDomain();
        if (name == null && node == null) {
            Element item;
            // Answer all first level nodes
            for (Node pubNode : rootCollectionNode.getNodes()) {
                if (canDiscoverNode(pubNode)) {
                    item = DocumentHelper.createElement("item");
                    item.addAttribute("jid", serviceDomain);
                    item.addAttribute("node", pubNode.getNodeID());
                    item.addAttribute("name", pubNode.getName());
                    answer.add(item);
                }
            }
        }
        else if (name == null && node != null) {
            Node pubNode = getNode(node);
            if (pubNode != null && canDiscoverNode(pubNode)) {
                if (pubNode.isCollectionNode()) {
                    Element item;
                    // Answer all nested nodes as items
                    for (Node nestedNode : pubNode.getNodes()) {
                        if (canDiscoverNode(nestedNode)) {
                            item = DocumentHelper.createElement("item");
                            item.addAttribute("jid", serviceDomain);
                            item.addAttribute("node", nestedNode.getNodeID());
                            item.addAttribute("name", nestedNode.getName());
                            answer.add(item);
                        }
                    }
                }
                else {
                    // This is a leaf node so answer the published items which exist on the service
                    Element item;
                    for (PublishedItem publishedItem : pubNode.getPublishedItems()) {
                        item = DocumentHelper.createElement("item");
                        item.addAttribute("jid", serviceDomain);
                        item.addAttribute("name", publishedItem.getID());
                        answer.add(item);
                    }
                }
            }
            else {
                // Answer null to indicate that specified item was not found
                return null;
            }
        }
        return answer.iterator();
    }

    public void broadcast(Node node, Message message, Collection<JID> jids) {
        // TODO Possibly use a thread pool for sending packets (based on the jids size)
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(
                    node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
            router.route(message);
        }
    }

    public void send(Packet packet) {
        router.route(packet);
    }

    public void sendNotification(Node node, Message message, JID jid) {
        message.setFrom(getAddress());
        message.setTo(jid);
        message.setID(
                node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
        router.route(message);
    }

    public Node getNode(String nodeID) {
        return nodes.get(nodeID);
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    private boolean hasNode(String nodeID) {
        return getNode(nodeID) != null;
    }

    public void addNode(Node node) {
        nodes.put(node.getNodeID(), node);
    }

    public void removeNode(String nodeID) {
        nodes.remove(nodeID);
    }

    private boolean canDiscoverNode(Node pubNode) {
        return true;
    }

    /**
     * Converts an array to a comma-delimitted String.
     *
     * @param array the array.
     * @return a comma delimtted String of the array values.
     */
    private static String fromArray(String [] array) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(",");
            }
        }
        return buf.toString();
    }
}
