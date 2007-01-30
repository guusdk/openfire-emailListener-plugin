/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.http;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.wildfire.Connection;
import org.jivesoftware.wildfire.StreamID;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.net.SASLAuthentication;
import org.jivesoftware.wildfire.net.VirtualConnection;
import org.jivesoftware.wildfire.session.ClientSession;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A session represents a serious of interactions with an XMPP client sending packets using the HTTP
 * Binding protocol specified in <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 * A session can have several client connections open simultaneously while awaiting packets bound
 * for the client from the server.
 *
 * @author Alexander Wenckus
 */
public class HttpSession extends ClientSession {
    private int wait;
    private int hold = 0;
    private String language;
    private final List<HttpConnection> connectionQueue = new LinkedList<HttpConnection>();
    private final List<Deliverable> pendingElements = new ArrayList<Deliverable>();
    private final List<Deliverable> sentElements = new ArrayList<Deliverable>();
    private boolean isSecure;
    private int maxPollingInterval;
    private long lastPoll = -1;
    private Set<SessionListener> listeners = new CopyOnWriteArraySet<SessionListener>();
    private boolean isClosed;
    private int inactivityTimeout;
    private long lastActivity;
    private long lastRequestID;
    private int maxRequests;

    public HttpSession(String serverName, InetAddress address, StreamID streamID, long rid) {
        super(serverName, null, streamID);
        conn = new HttpVirtualConnection(address);
        this.lastActivity = System.currentTimeMillis();
        this.lastRequestID = rid;
    }

    /**
     * Returns the stream features which are available for this session.
     *
     * @return the stream features which are available for this session.
     */
    public Collection<Element> getAvailableStreamFeaturesElements() {
        List<Element> elements = new ArrayList<Element>();

        Element sasl = SASLAuthentication.getSASLMechanismsElement(this);
        if (sasl != null) {
            elements.add(sasl);
        }

        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            Element compression = DocumentHelper.createElement(new QName("compression",
                    new Namespace("", "http://jabber.org/features/compress")));
            Element method = compression.addElement("method");
            method.setText("zlib");

            elements.add(compression);
        }
        Element bind = DocumentHelper.createElement(new QName("bind",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind")));
        elements.add(bind);

        Element session = DocumentHelper.createElement(new QName("session",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
        elements.add(session);
        return elements;
    }

    public String getAvailableStreamFeatures() {
        StringBuilder sb = new StringBuilder(200);
        for (Element element : getAvailableStreamFeaturesElements()) {
            sb.append(element.asXML());
        }
        return sb.toString();
    }

    /**
     * Closes the session. After a session has been closed it will no longer accept new connections
     * on the session ID.
     */
    public synchronized void close() {
        if (isClosed) {
            return;
        }
        conn.close();
    }

    /**
     * Returns true if this session has been closed and no longer activley accepting connections.
     *
     * @return true if this session has been closed and no longer activley accepting connections.
     */
    public synchronized boolean isClosed() {
        return isClosed;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @param wait the longest time it is permissible to wait for a response.
     */
    public void setWait(int wait) {
        this.wait = wait;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @return the longest time it is permissible to wait for a response.
     */
    public int getWait() {
        return wait;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @param hold the maximum number of simultaneous waiting requests.
     */
    public void setHold(int hold) {
        this.hold = hold;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @return the maximum number of simultaneous waiting requests
     */
    public int getHold() {
        return hold;
    }

    /**
     * Sets the language this session is using.
     *
     * @param language the language this session is using.
     */
    public void setLanaguage(String language) {
        this.language = language;
    }

    /**
     * Returns the language this session is using.
     *
     * @return the language this session is using.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @param maxPollingInterval time in seconds a client needs to wait before sending polls to the
     * server, a negative <i>int</i> indicates that there is no limit.
     */
    public void setMaxPollingInterval(int maxPollingInterval) {
        this.maxPollingInterval = maxPollingInterval;
    }

    /**
     * Returns the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @return the max interval within which a client can send polling requests. If more than one
     *         request occurs in the interval the session will be terminated.
     */
    public int getMaxPollingInterval() {
        return this.maxPollingInterval;
    }

    /**
     * The max number of requests it is permissable for this session to have open at any one time.
     *
     * @param maxRequests The max number of requests it is permissable for this session to have open
     * at any one time.
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * Returns the max number of requests it is permissable for this session to have open at any one
     * time.
     *
     * @return the max number of requests it is permissable for this session to have open at any one
     *         time.
     */
    public int getMaxRequests() {
        return this.maxRequests;
    }

    /**
     * Returns true if all connections on this session should be secured, and false if they should
     * not.
     *
     * @return true if all connections on this session should be secured, and false if they should
     *         not.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Adds a {@link org.jivesoftware.wildfire.http.SessionListener} to this session. The listener
     * will be notified of changes to the session.
     *
     * @param listener the listener which is being added to the session.
     */
    public void addSessionCloseListener(SessionListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a {@link org.jivesoftware.wildfire.http.SessionListener} from this session. The
     * listener will no longer be updated when an event occurs on the session.
     *
     * @param listener the session listener that is to be removed.
     */
    public void removeSessionCloseListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sets the time, in seconds, after which this session will be considered inactive and be be
     * terminated.
     *
     * @param inactivityTimeout the time, in seconds, after which this session will be considered
     * inactive and be terminated.
     */
    public void setInactivityTimeout(int inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    /**
     * Returns the time, in seconds, after which this session will be considered inactive and
     * terminated.
     *
     * @return the time, in seconds, after which this session will be considered inactive and
     *         terminated.
     */
    public int getInactivityTimeout() {
        return inactivityTimeout;
    }

    /**
     * Returns the number of connections currently awaiting a response on this session.
     *
     * @return the number of connections currently awaiting a response on this session.
     */
    public int getConnectionCount() {
        return connectionQueue.size();
    }

    /**
     * Returns the time in milliseconds since the epoch that this session was last active. Activity
     * is a request was either made or responded to. If the session is currently active, meaning
     * there are connections awaiting a response, the current time is returned.
     *
     * @return the time in milliseconds since the epoch that this session was last active.
     */
    public synchronized long getLastActivity() {
        if (connectionQueue.size() <= 0) {
            return lastActivity;
        }
        // The session is currently active, return the current time.
        else {
            return System.currentTimeMillis();
        }
    }

    public String getResponse(long requestID) {
        for (HttpConnection connection : connectionQueue) {
            if (connection.getRequestId() == requestID) {
                String response;
                try {
                    response = connection.getResponse();
                }
                catch (HttpBindTimeoutException e) {
                    response = createEmptyBody();
                }
                connectionQueue.remove(connection);
                fireConnectionClosed(connection);
                return response;
            }
        }
        throw new InternalError("Could not locate connection: " + requestID);
    }

    /**
     * Sets whether the initial request on the session was secure.
     *
     * @param isSecure true if the initial request was secure and false if it wasn't.
     */
    protected void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    /**
     * Creates a new connection on this session. If a response is currently available for this
     * session the connection is responded to immediately, otherwise it is queued awaiting a
     * response.
     *
     * @param rid the request id related to the connection.
     * @param isPoll true if the request was a poll, no packets were sent along with the request.
     * @param isSecure true if the connection was secured using HTTPS.
     * @return the created {@link org.jivesoftware.wildfire.http.HttpConnection} which represents
     * the connection.
     * @throws HttpConnectionClosedException if the connection was closed before a response could
     * be delivered.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     * protocol.
     */
    HttpConnection createConnection(long rid, boolean isPoll, boolean isSecure)
            throws HttpConnectionClosedException, HttpBindException {
        HttpConnection connection = new HttpConnection(rid, isSecure);
        if (rid <= lastRequestID) {
            Deliverable deliverable = retrieveDeliverable(rid);
            if (deliverable == null) {
                Log.warn("Deliverable unavailable for " + rid);
                throw new HttpBindException("Unexpected RID Error", true, 404);
            }
            connection.deliverBody(deliverable.getDeliverable());
            return connection;
        }
        else if (rid > (lastRequestID + hold + 1)) {
            // TODO handle the case of greater RID which basically has it wait
            Log.warn("Request " + rid + " > " + (lastRequestID + hold + 1) + ", ending session.");
            throw new HttpBindException("Unexpected RID Error", true, 404);
        }

        addConnection(connection, isPoll);
        return connection;
    }

    private Deliverable retrieveDeliverable(long rid) throws HttpBindException {
        for (Deliverable delivered : sentElements) {
            if (delivered.getRequestID() == rid) {
                return delivered;
            }
        }
        return null;
    }

    private void addConnection(HttpConnection connection, boolean isPoll) throws HttpBindException,
            HttpConnectionClosedException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        if (isPoll) {
            checkPollingInterval();
        }

        if (isSecure && !connection.isSecure()) {
            throw new HttpBindException("Session was started from secure connection, all " +
                    "connections on this session must be secured.", false, 403);
        }

        connection.setSession(this);
        // We aren't supposed to hold connections open or we already have some packets waiting
        // to be sent to the client.
        if (hold <= 0 || pendingElements.size() > 0) {
            String deliverable = createDeliverable(pendingElements);
            try {
                fireConnectionOpened(connection);
                deliver(connection, deliverable);
                fireConnectionClosed(connection);
                pendingElements.clear();
            }
            catch (HttpConnectionClosedException he) {
                throw he;
            }
        }
        else {
            // With this connection we need to check if we will have too many connections open,
            // closing any extras.
            while (connectionQueue.size() >= hold) {
                HttpConnection toClose = connectionQueue.remove(0);
                toClose.close();
                fireConnectionClosed(toClose);
            }
            connectionQueue.add(connection);
            fireConnectionOpened(connection);
        }
        lastRequestID = connection.getRequestId();
    }

    private void deliver(HttpConnection connection, String deliverable)
            throws HttpConnectionClosedException {
        connection.deliverBody(deliverable);

        Deliverable delivered = new Deliverable(deliverable);
        delivered.setRequestID(connection.getRequestId());
        while (sentElements.size() > hold) {
            sentElements.remove(0);
        }

        sentElements.add(delivered);
    }

    private void fireConnectionOpened(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionOpened(this, connection);
        }
    }

    private void checkPollingInterval() throws HttpBindException {
        long time = System.currentTimeMillis();
        if (((time - lastPoll) / 1000) < maxPollingInterval) {
            throw new HttpBindException("Too frequent polling minimum interval is "
                    + maxPollingInterval + ", current interval " + ((time - lastPoll) / 1000),
                    true, 403);
        }
        lastPoll = time;
    }

    private synchronized void deliver(String text) {
        deliver(new Deliverable(text));
    }

    private synchronized void deliver(Packet stanza) {
        deliver(new Deliverable(stanza));
    }

    private void deliver(Deliverable stanza) {
        String deliverable = createDeliverable(Arrays.asList(stanza));
        boolean delivered = false;
        for (HttpConnection connection : connectionQueue) {
            try {
                deliver(connection, deliverable);
                delivered = true;
                break;
            }
            catch (HttpConnectionClosedException e) {
                /* Connection was closed, try the next one */
            }
        }

        if (!delivered) {
            pendingElements.add(stanza);
        }
    }

    private void fireConnectionClosed(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionClosed(this, connection);
        }
    }

    private String createDeliverable(Collection<Deliverable> elements) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body xmlns='" + "http://jabber.org/protocol/httpbind" + "'>");
        for (Deliverable child : elements) {
            builder.append(child.getDeliverable());
        }
        builder.append("</body>");
        return builder.toString();
    }

    private synchronized void closeConnection() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        if (pendingElements.size() > 0) {
            failDelivery();
        }

        for (HttpConnection toClose : connectionQueue) {
            toClose.close();
            fireConnectionClosed(toClose);
        }

        for (SessionListener listener : listeners) {
            listener.sessionClosed(this);
        }
        this.listeners.clear();
    }

    private void failDelivery() {
        for (Deliverable deliverable : pendingElements) {
            Packet packet = deliverable.packet;
            if (packet != null && packet instanceof Message) {
                XMPPServer.getInstance().getOfflineMessageStrategy()
                        .storeOffline((Message) packet);
            }
        }
        pendingElements.clear();
    }

    private static String createEmptyBody() {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        return body.asXML();
    }

    /**
     * A virtual server connection relates to a http session which its self can relate to many http
     * connections.
     */
    public static class HttpVirtualConnection extends VirtualConnection {

        private InetAddress address;

        public HttpVirtualConnection(InetAddress address) {
            this.address = address;
        }

        public void closeVirtualConnection() {
            ((HttpSession) session).closeConnection();
        }

        public InetAddress getInetAddress() {
            return address;
        }

        public void systemShutdown() {
            ((HttpSession) session).closeConnection();
        }

        public void deliver(Packet packet) throws UnauthorizedException {
            ((HttpSession) session).deliver(packet);
        }

        public void deliverRawText(String text) {
            ((HttpSession) session).deliver(text);
        }
    }

    private class Deliverable implements Comparable<Deliverable> {
        private final String text;
        private final Packet packet;
        private long requestID;

        public Deliverable(String text) {
            this.text = text;
            this.packet = null;
        }

        public Deliverable(Packet element) {
            this.text = null;
            this.packet = element.createCopy();
        }

        public String getDeliverable() {
            if (text == null) {
                return packet.toXML();
            }
            else {
                return text;
            }
        }

        public void setRequestID(long requestID) {
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        public int compareTo(Deliverable o) {
            return (int) (o.getRequestID() - requestID);
        }
    }

    /**
     * A queue of all current connections. Connections are dealt with in order of their request
     * IDs.
     */
    private class ConnectionQueue {

        private final HttpConnection[] connections;

        private final String[] requestIds;

        private int pointer = 0;

        public ConnectionQueue(int size) {
            this.connections = new HttpConnection[size];
            this.requestIds = new String[size];
        }


    }
}
