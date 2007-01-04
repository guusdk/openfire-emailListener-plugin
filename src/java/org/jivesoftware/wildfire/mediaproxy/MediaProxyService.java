package org.jivesoftware.wildfire.mediaproxy;

import org.dom4j.Attribute;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.disco.DiscoInfoProvider;
import org.jivesoftware.wildfire.disco.DiscoItemsProvider;
import org.jivesoftware.wildfire.disco.DiscoServerItem;
import org.jivesoftware.wildfire.disco.ServerItemsProvider;
import org.jivesoftware.wildfire.forms.spi.XDataFormImpl;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * RTP Bridge Media Proxy service plugin.
 * Provides especial relayed Jingle transport candidates to be used for media transmission and receiving of UDP packets.
 * Especialy used for behind NAT users to ensure connectivity between parties.
 *
 * @author Thiago Camargo
 */
public class MediaProxyService extends BasicModule implements ServerItemsProvider, RoutableChannelHandler, DiscoInfoProvider, DiscoItemsProvider {

    private String serviceName;
    private RoutingTable routingTable;
    private PacketRouter router;

    private MediaProxy mediaProxy = null;
    private String name = "rtpbridge";
    private boolean enabled = false;

    public static final String NAMESPACE = "http://www.jivesoftware.com/protocol/rtpbridge";

    /**
     * Constructs a new RTPBridgePlugin plugin.
     */
    public MediaProxyService() {
        super("Media Proxy Service");
    }

    /**
     * Load config using JiveGlobals
     */
    private void loadRTPProxyConfig() {
        try {
            long keepAliveDelay =
                    Long.valueOf(JiveGlobals.getProperty("plugin.rtpbridge.keepalivedelay"));
            mediaProxy.setKeepAliveDelay(keepAliveDelay);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            int minPort = Integer.valueOf(JiveGlobals.getProperty("plugin.rtpbridge.minport"));
            mediaProxy.setMinPort(minPort);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            int maxPort = Integer.valueOf(JiveGlobals.getProperty("plugin.rtpbridge.maxport"));
            mediaProxy.setMaxPort(maxPort);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            boolean enabled = Boolean.valueOf(JiveGlobals.getProperty("plugin.rtpbridge.enabled"));
            this.enabled = enabled;
        }
        catch (NumberFormatException e) {
            this.enabled = false;
        }
    }

    public void destroy() {
        super.destroy();
        // Unregister component.
        try {
            mediaProxy.stopProxy();
        }
        catch (Exception e) {
            Log.error(e);
        }
        mediaProxy = null;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        String hostname = JiveGlobals.getProperty("xmpp.domain",
                JiveGlobals.getProperty("network.interface", "localhost"));
        mediaProxy = new MediaProxy(hostname);
        serviceName = JiveGlobals.getProperty("plugin.rtpbridge.serviceName", name);
        serviceName = serviceName == null ? name : serviceName.equals("") ? name : serviceName;

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        loadRTPProxyConfig();
    }

    public void start() {
        if (isEnabled()) {
            startProxy();
        } else {
            XMPPServer.getInstance().getIQDiscoItemsHandler().removeServerItemsProvider(this);
        }
    }

    public void startProxy() {
        routingTable.addRoute(getAddress(), this);
        XMPPServer server = XMPPServer.getInstance();
        server.getIQDiscoItemsHandler().addServerItemsProvider(this);
    }

    public void stop() {
        super.stop();
        mediaProxy.stopProxy();
        XMPPServer.getInstance().getIQDiscoItemsHandler()
                .removeComponentItem(getAddress().toString());
        routingTable.removeRoute(getAddress());
    }

    // Component Interface

    public String getName() {
        // Get the name from the plugin.xml file.
        return serviceName;
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID) {
        // A proxy server has no items
        return new ArrayList<Element>().iterator();
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        // Check if user is allowed to send packet to this service
        Log.debug("TEST");
        if (packet instanceof IQ) {
            // Handle disco packets
            IQ iq = (IQ) packet;
            // Ignore IQs of type ERROR or RESULT
            if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
                return;
            }
            processIQ(iq);
        }
    }

    private void processIQ(IQ iq) {
        IQ reply = IQ.createResultIQ(iq);
        Element childElement = iq.getChildElement();
        String namespace = childElement.getNamespaceURI();
        Element childElementCopy = iq.getChildElement().createCopy();
        reply.setChildElement(childElementCopy);
        Log.debug("RECEIVED:" + iq.toXML());

        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            try {
                reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
                router.route(reply);
                return;
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        } else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            try {
                // a component
                reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
                router.route(reply);
                return;
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        } else if (NAMESPACE.equals(namespace) && enabled) {

            Element c = childElementCopy.element("candidate");

            if (c != null) {

                childElementCopy.remove(c);
                Element candidate = childElementCopy.addElement("candidate ");
                ProxyCandidate proxyCandidate = mediaProxy.addSmartAgent(
                        childElementCopy.attribute("sid").getValue() + "-" + iq.getFrom(),
                        iq.getFrom().toString());
                Log.debug(childElementCopy.attribute("sid").getValue() + "-" + iq.getFrom());
                proxyCandidate.start();
                candidate.addAttribute("name", "voicechannel");
                candidate.addAttribute("ip", mediaProxy.getPublicIP());
                candidate.addAttribute("porta", String.valueOf(proxyCandidate.getLocalPortA()));
                candidate.addAttribute("portb", String.valueOf(proxyCandidate.getLocalPortB()));
                candidate.addAttribute("pass", proxyCandidate.getPass());

            } else {

                c = childElementCopy.element("relay");

                if (c != null) {

                    Session session = mediaProxy.getAgent(
                            childElementCopy.attribute("sid").getValue() + "-" + iq.getFrom());

                    Log.debug(
                            childElementCopy.attribute("sid").getValue() + "-" + iq.getFrom());

                    if (session != null) {

                        Attribute pass = c.attribute("pass");

                        if (pass != null && pass.getValue().trim().equals(session.getPass().trim())) {

                            Log.debug("RIGHT PASS");

                            Attribute portA = c.attribute("porta");
                            Attribute portB = c.attribute("portb");
                            Attribute hostA = c.attribute("hosta");
                            Attribute hostB = c.attribute("hostb");

                            try {
                                if (hostA != null) {
                                    if (portA != null) {
                                        for (int i = 0; i < 2; i++) {
                                            session.sendFromPortA(hostB.getValue(),
                                                    Integer.parseInt(portB.getValue()));
                                        }
                                    }
                                }

                            }
                            catch (Exception e) {
                                Log.error(e);
                            }

                            //System.out.println(session.getLocalPortA() + "->" + session.getPortA());
                            //System.out.println(session.getLocalPortB() + "->" + session.getPortB());

                            //componentManager.getLog().debug(session.getLocalPortA() + "->" + session.getPortA());
                            //componentManager.getLog().debug(session.getLocalPortB() + "->" + session.getPortB());

                        } else {
                            Log.debug("WRONG PASS " + session.getPass() + " " + pass.getValue());
                            reply.setError(PacketError.Condition.forbidden);
                        }

                    }
                    childElementCopy.remove(c);
                }

            }

        } else {
            // Answer an error since the server can't handle the requested namespace
            reply.setError(PacketError.Condition.service_unavailable);
        }

        try {
            Log.debug("RETURNED:" + reply.toXML());
            router.route(reply);
        }

        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     *
     * @return the file transfer server domain (service name + host name).
     */
    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getName();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null);
    }

    public Iterator<DiscoServerItem> getItems() {
        List<DiscoServerItem> items = new ArrayList<DiscoServerItem>();
        if (!isEnabled()) {
            return items.iterator();
        }

        items.add(new DiscoServerItem() {
            public String getJID() {
                return getServiceDomain();
            }

            public String getName() {
                return "Media Proxy Service";
            }

            public String getAction() {
                return null;
            }

            public String getNode() {
                return null;
            }

            public DiscoInfoProvider getDiscoInfoProvider() {
                return MediaProxyService.this;
            }

            public DiscoItemsProvider getDiscoItemsProvider() {
                return MediaProxyService.this;
            }
        });
        return items.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        List<Element> identities = new ArrayList<Element>();
        // Answer the identity of the proxy
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "proxy");
        identity.addAttribute("name", "Media Proxy Service");
        identity.addAttribute("type", "rtpbridge");

        identities.add(identity);

        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        return Arrays.asList(NAMESPACE,
                "http://jabber.org/protocol/disco#info").iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        return true;
    }

    /**
     * Return the list of active Agents
     *
     * @return
     */
    public List<Session> getAgents() {
        return mediaProxy.getAgents();
    }

    /**
     * Set the keep alive delay of the mediaproxy agents.
     * When an agent stay more then this delay, the agent is destroyed.
     *
     * @param delay time in millis
     */
    public void setKeepAliveDelay(long delay) {
        mediaProxy.setKeepAliveDelay(delay);
    }

    /**
     * Get the keep alive delay of the mediaproxy agents.
     * When an agent stay more then this delay, the agent is destroyed.
     *
     * @return delay time in millis
     */
    public long getKeepAliveDelay() {
        return mediaProxy.getKeepAliveDelay();
    }

    /**
     * Set Minimal port value to listen from incoming packets.
     *
     * @param minPort
     */
    public void setMinPort(int minPort) {
        mediaProxy.setMinPort(minPort);
    }

    /**
     * Set Maximum port value to listen from incoming packets.
     *
     * @param maxPort
     */
    public void setMaxPort(int maxPort) {
        mediaProxy.setMaxPort(maxPort);
    }

    /**
     * Get Minimal port value to listen from incoming packets.
     *
     * @return minPort
     */
    public int getMinPort() {
        return mediaProxy.getMinPort();
    }

    /**
     * Get Maximum port value to listen from incoming packets.
     *
     * @return maxPort
     */
    public int getMaxPort() {
        return mediaProxy.getMaxPort();
    }

    /**
     * Get if the service is enabled.
     *
     * @return enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the service enable status.
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (isEnabled()) {
            startProxy();
        } else {
            stop();
        }
    }

    /**
     * Stops every running agents
     */
    public void stopAgents() {
        mediaProxy.stopProxy();
    }
}
