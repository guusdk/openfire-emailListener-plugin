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

import org.xmpp.packet.JID;
import org.dom4j.Element;

import java.util.Date;

/**
 * A published item to a node. Once an item was published to a node, node subscribers will be
 * notified of the new published item. The item publisher may be allowed to delete published
 * items. After a published item was deleted node subscribers will get an event notification.<p>
 *
 * Published items may be persisted to the database depending on the node configuration.
 * Actually, even when the node is configured to not persist items the last published
 * item is going to be persisted to the database. The reason for this is that the node
 * may need to send the last published item to new subscribers.
 *
 * @author Matt Tucker
 */
public class PublishedItem {

    /**
     * JID of the entity that published the item to the node.
     */
    private JID publisher;
    /**
     * The node where the item was published.
     */
    private LeafNode node;
    /**
     * ID that uniquely identifies the published item in the node.
     */
    private String id;
    /**
     * The datetime when the items was published.
     */
    private Date creationDate;
    /**
     * The payload included when publishing the item.
     */
    private Element payload;
    /**
     * XML representation of the payload. This is actually a cache that avoids
     * doing Element#asXML.
     */
    private String payloadXML;

    PublishedItem(LeafNode node, JID publisher, String id, Date creationDate) {
        this.node = node;
        this.publisher = publisher;
        this.id = id;
        this.creationDate = creationDate;
    }

    /**
     * Returns the {@link LeafNode} where this item was published.
     *
     * @return the leaf node where this item was published.
     */
    public LeafNode getNode() {
        return node;
    }

    /**
     * Returns the ID that uniquely identifies the published item in the node.
     *
     * @return the ID that uniquely identifies the published item in the node.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns the JID of the entity that published the item to the node.
     *
     * @return the JID of the entity that published the item to the node.
     */
    public JID getPublisher() {
        return publisher;
    }

    /**
     * Returns the datetime when the items was published.
     *
     * @return the datetime when the items was published.
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Returns the payload included when publishing the item. A published item may or may not
     * have a payload. Transient nodes that are configured to not broadcast payloads may allow
     * published items to have no payload.
     *
     * @return the payload included when publishing the item or <tt>null</tt> if none was found.
     */
    public Element getPayload() {
        return payload;
    }

    /**
     * Sets the payload included when publishing the item. A published item may or may not
     * have a payload. Transient nodes that are configured to not broadcast payloads may allow
     * published items to have no payload.
     *
     * @param payload the payload included when publishing the item or <tt>null</tt>
     *        if none was found.
     */
    void setPayload(Element payload) {
        this.payload = payload;
        // Update XML representation of the payload
        if (payload == null) {
            payloadXML = null;
        }
        else {
            payloadXML = payload.asXML();
        }
    }

    /**
     * Returns true if payload contains the specified keyword. If the item has no payload
     * or keyword is <tt>null</tt> then return true.
     *
     * @param keyword the keyword to look for in the payload.
     * @return true if payload contains the specified keyword.
     */
    boolean containsKeyword(String keyword) {
        if (payloadXML == null || keyword == null) {
            return true;
        }
        return payloadXML.contains(keyword);
    }
}
