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

package org.jivesoftware.messenger;

import org.dom4j.Element;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.jivesoftware.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the registration and delegation of Components. The ComponentManager
 * is responsible for managing registration and delegation of {@link Component Components},
 * as well as offering a facade around basic server functionallity such as sending and
 * receiving of packets.<p>
 *
 * This component manager will be an internal service whose JID will be component.[domain]. So the
 * component manager will be able to send packets to other internal or external components and also
 * receive packets from other components or even from trusted clients (e.g. ad-hoc commands).
 *
 * @author Derek DeMoro
 */
public class InternalComponentManager implements ComponentManager, RoutableChannelHandler {

    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<JID, JID>();

    private static InternalComponentManager instance;
    /**
     * XMPP address of this internal service. The address is of the form: component.[domain]
     */
    private JID serviceAddress;
    /**
     * Holds the domain of the server. We are using an iv since we use this value many times
     * in many methods.
     */
    private String serverDomain;

    static {
        instance = new InternalComponentManager();
        ComponentManagerFactory.setComponentManager(instance);
    }

    public static InternalComponentManager getInstance() {
        return instance;
    }

    private InternalComponentManager() {
        XMPPServer server = XMPPServer.getInstance();
        serverDomain = server.getServerInfo().getName();
        // Set the address of this internal service. component.[domain]
        serviceAddress = new JID(null, "component." + serverDomain, null);
        // Add a route to this service
        server.getRoutingTable().addRoute(getAddress(), this);
    }

    public void addComponent(String subdomain, Component component) {
        components.put(subdomain, component);

        JID componentJID = new JID(subdomain + "." + serverDomain);

        // Add the route to the new service provided by the component
        XMPPServer.getInstance().getRoutingTable().addRoute(componentJID,
                new RoutableComponent(componentJID, component));

        // Check for potential interested users.
        checkPresences();
        // Send a disco#info request to the new component. If the component provides information
        // then it will be added to the list of discoverable server items.
        checkDiscoSupport(component, componentJID);
    }

    public void removeComponent(String subdomain) {
        components.remove(subdomain);

        JID componentJID = new JID(subdomain + "." + serverDomain);

        // Remove the route for the service provided by the component
        if (XMPPServer.getInstance().getRoutingTable() != null) {
            XMPPServer.getInstance().getRoutingTable().removeRoute(componentJID);
        }

        // Remove the disco item from the server for the component that is being removed
        if (XMPPServer.getInstance().getIQDiscoItemsHandler() != null) {
            XMPPServer.getInstance().getIQDiscoItemsHandler().removeComponentItem(componentJID.toBareJID());
        }
    }

    public void sendPacket(Component component, Packet packet) {
        PacketRouter router;
        router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    public String getProperty(String name) {
        return JiveGlobals.getProperty(name);
    }

    public void setProperty(String name, String value) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isExternalMode() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public org.xmpp.component.Log getLog() {
        return new  org.xmpp.component.Log() {
            public void error(String msg) {
                Log.error(msg);
            }

            public void error(String msg, Throwable throwable) {
                Log.error(msg, throwable);
            }

            public void error(Throwable throwable) {
                Log.error(throwable);
            }

            public void warn(String msg) {
                Log.warn(msg);
            }

            public void warn(String msg, Throwable throwable) {
                Log.warn(msg, throwable);
            }

            public void warn(Throwable throwable) {
                Log.warn(throwable);
            }

            public void info(String msg) {
                Log.info(msg);
            }

            public void info(String msg, Throwable throwable) {
                Log.info(msg, throwable);
            }

            public void info(Throwable throwable) {
                Log.info(throwable);
            }

            public void debug(String msg) {
                Log.debug(msg);
            }

            public void debug(String msg, Throwable throwable) {
                Log.debug(msg, throwable);
            }

            public void debug(Throwable throwable) {
                Log.debug(throwable);
            }
        };
    }

    /**
     * Retrieves the <code>Component</code> which is mapped
     * to the specified JID.
     *
     * @param jid the jid mapped to the component.
     * @return the component with the specified id.
     */
    public Component getComponent(String jid) {
        jid = new JID(jid).toBareJID();
        if (components.containsKey(jid)) {
            return components.get(jid);
        }
        else {
            String serverName = new JID(jid).getDomain();
            int index = serverName.indexOf(".");
            if (index != -1) {
                String serviceName = serverName.substring(0, index);
                jid = serviceName;
            }
        }
        return components.get(jid);
    }

    /**
     * Registers Probeers who have not yet been serviced.
     *
     * @param prober the jid probing.
     * @param probee the presence being probed.
     */
    public void addPresenceRequest(JID prober, JID probee) {
        presenceMap.put(prober, probee);
    }

    private void checkPresences() {
        for (JID prober : presenceMap.keySet()) {
            JID probee = presenceMap.get(prober);

            Component component = getComponent(probee.toBareJID());
            if (component != null) {
                Presence presence = new Presence();
                presence.setFrom(prober);
                presence.setTo(probee);
                component.processPacket(presence);

                // No reason to hold onto prober reference.
                presenceMap.remove(prober);
            }
        }
    }

    /**
     *  Send a disco#info request to the new component. If the component provides information
     *  then it will be added to the list of discoverable server items.
     *
     * @param component the new component that was added to this manager.
     * @param componentJID the XMPP address of the new component.
     */
    private void checkDiscoSupport(Component component, JID componentJID) {
        // Build a disco#info request that will be sent to the component
        IQ iq = new IQ(IQ.Type.get);
        iq.setFrom(getAddress());
        iq.setTo(componentJID);
        iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
        // Send the disco#info request to the component. The reply (if any) will be processed in
        // #process(Packet)
        sendPacket(component, iq);
    }

    public JID getAddress() {
        return serviceAddress;
    }

    /**
     * Processes packets that were sent to this service. Currently only packets that were sent from
     * registered components are being processed. In the future, we may also process packet of
     * trusted clients. Trusted clients may be able to execute ad-hoc commands such as adding or
     * removing components.
     *
     * @param packet the packet to process.
     */
    public void process(Packet packet) throws UnauthorizedException, PacketException {
        Component component = getComponent(packet.getFrom().getDomain());
        // Only process packets that were sent by registered components
        if (component != null) {
            if (packet instanceof IQ && IQ.Type.result == ((IQ) packet).getType()) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                    // Add a disco item to the server for the component that supports disco
                    XMPPServer.getInstance().getIQDiscoItemsHandler().addComponentItem(packet.getFrom()
                            .toBareJID(),
                            childElement.element("identity").attributeValue("name"));
                }
            }
        }
    }

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    public static class RoutableComponent implements RoutableChannelHandler {

        private JID jid;
        private Component component;

        public RoutableComponent(JID jid, Component component) {
            this.jid = jid;
            this.component = component;
        }

        public JID getAddress() {
            return jid;
        }

        public void process(Packet packet) throws UnauthorizedException, PacketException {
            component.processPacket(packet);
        }
    }
}