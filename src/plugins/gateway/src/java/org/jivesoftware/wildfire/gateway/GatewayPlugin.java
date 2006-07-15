/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.gateway.util.GatewayInstance;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.File;

import java.util.Hashtable;

/**
 * IM Gateway plugin, which provides connectivity to IM networks that don't support
 * the XMPP protocol. 
 *
 * @author Daniel Henninger
 */
public class GatewayPlugin implements Plugin {

    private MutablePicoContainer picoContainer;

    /**
     *  Represents all configured gateway handlers.
     */
    private Hashtable<String,GatewayInstance> gateways; 

    /**
     *  Represents the base component manager.
     */
    private ComponentManager componentManager;

    public GatewayPlugin() {
        picoContainer = new DefaultPicoContainer();

        picoContainer.registerComponentImplementation(RegistrationManager.class);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        picoContainer.start();

        gateways = new Hashtable<String,GatewayInstance>();

        componentManager = ComponentManagerFactory.getComponentManager();

        /* Set up AIM gateway. */
        gateways.put("aim", new GatewayInstance("aim",
                "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARGateway", componentManager));
        maybeStartService("aim");

        /* Set up ICQ gateway. */
        gateways.put("icq", new GatewayInstance("icq",
                "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARGateway", componentManager));
        maybeStartService("icq");

        /* Set up Yahoo gateway. */
        gateways.put("yahoo", new GatewayInstance("yahoo",
                "org.jivesoftware.wildfire.gateway.protocols.yahoo.YahooGateway", componentManager));
        maybeStartService("yahoo");
    }

    public void destroyPlugin() {
        for (GatewayInstance gwInstance : gateways.values()) {
            gwInstance.stopInstance();
        }
        picoContainer.stop();
        picoContainer.dispose();
        picoContainer = null;
    }

    /**
     * Returns the instance of a module registered with the plugin.
     *
     * @param clazz the module class.
     * @return the instance of the module.
     */
    public Object getModule(Class clazz) {
        return picoContainer.getComponentInstanceOfType(clazz);
    }

    /**
     *  Starts a gateway service, identified by subdomain.  The gateway
     *  service will only start if it is enabled.
     */
    private void maybeStartService(String serviceName) {
        GatewayInstance gwInstance = gateways.get(serviceName);
        gwInstance.startInstance();
        Log.debug("Starting gateway service: "+serviceName);
    }

    /**
     *  Enables a gateway service, identified by subdomain.
     */
    public void enableService(String serviceName) {
        GatewayInstance gwInstance = gateways.get(serviceName);
        gwInstance.enable();
        Log.debug("Enabling gateway service: "+serviceName);
    }

    /**
     *  Disables a gateway service, identified by subdomain.
     */
    public void disableService(String serviceName) {
        GatewayInstance gwInstance = gateways.get(serviceName);
        gwInstance.disable();
        Log.debug("Disabling gateway service: "+serviceName);
    }

    /**
     *  Returns the state of a gateway service, identified by subdomain.
     */
    public Boolean serviceEnabled(String serviceName) {
        GatewayInstance gwInstance = gateways.get(serviceName);
        return gwInstance.isEnabled();
    }
}