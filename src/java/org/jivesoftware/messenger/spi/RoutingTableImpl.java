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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.*;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>Uses simple hashtables for table storage.</p>
 * <p>Leaves in the tree are indicated by a PacketHandler, while branches are stored in hashtables.
 * Traverse the tree according to an XMPPAddress' fields (host -> name -> resource) and when you
 * hit a PacketHandler, you have found the handler for that node and all sub-nodes. </p>
 *
 * @author Iain Shigeoka
 */
public class RoutingTableImpl extends BasicModule implements RoutingTable {

    /**
     * We need a three level tree built of hashtables: host -> name -> resource
     */
    private Hashtable routes = new Hashtable();

    /**
     * locks access to the routing tale
     */
    private ReadWriteLock routeLock = new ReentrantReadWriteLock();

    public RoutingTableImpl() {
        super("Routing table");
    }

    public ChannelHandler addRoute(JID node, RoutableChannelHandler destination) {

        ChannelHandler route = null;

        routeLock.writeLock().lock();
        try {
            if (node.getNode() == null) {
                Object item = routes.put(node.getDomain(), destination);
                if (item instanceof ChannelHandler) {
                    route = (ChannelHandler)item;
                }
            }
            else {
                Object nameRoutes = routes.get(node.getDomain());
                if (nameRoutes == null || nameRoutes instanceof ChannelHandler) {
                    nameRoutes = new Hashtable();
                    routes.put(node.getDomain(), nameRoutes);
                }
                if (node.getResource() == null) {
                    Object item = ((Hashtable)nameRoutes).put(node.getNode(),
                            destination);
                    if (item instanceof ChannelHandler) {
                        route = (ChannelHandler)item;
                    }
                }
                else {
                    Object resourceRoutes =
                            ((Hashtable)nameRoutes).get(node.getNode());
                    if (resourceRoutes == null
                            || resourceRoutes instanceof ChannelHandler) {
                        resourceRoutes = new Hashtable();
                        Object item = ((Hashtable)nameRoutes).put(node.getNode(),
                                resourceRoutes);
                        if (item instanceof ChannelHandler) {
                            // Associate the previous Route with the bare JID
                            ((Hashtable)resourceRoutes).put("", item);
                        }
                    }
                    Object resourceRoute =
                            ((Hashtable)resourceRoutes).put(node.getResource(),
                                    destination);
                    if (resourceRoute != null) {
                        if (resourceRoute instanceof ChannelHandler) {
                            route = (ChannelHandler)resourceRoute;
                        }
                    }
                }
            }
        }
        finally {
            routeLock.writeLock().unlock();
        }

        return route;
    }

    public RoutableChannelHandler getRoute(JID node) throws NoSuchRouteException {
        RoutableChannelHandler route = null;
        routeLock.readLock().lock();
        try {
            Object nameRoutes = routes.get(node.getDomain());
            if (nameRoutes instanceof ChannelHandler) {
                route = (RoutableChannelHandler)nameRoutes;
            }
            else {
                Object resourceRoutes = ((Hashtable)nameRoutes).get(node.getNode());
                if (resourceRoutes instanceof ChannelHandler) {
                    route = (RoutableChannelHandler)resourceRoutes;
                }
                else if (resourceRoutes != null) {
                    String resource = node.getResource() == null ? "" : node.getResource();
                    route = (RoutableChannelHandler) ((Hashtable)resourceRoutes).get(resource);
                }
                else {
                    throw new NoSuchRouteException(node.toString());
                }
            }
        }
        catch (Exception e) {
            throw new NoSuchRouteException(node == null ? "No node" : node.toString(), e);
        }
        finally {
            routeLock.readLock().unlock();
        }

        if (route == null) {
            throw new NoSuchRouteException(node == null ? "No node" : node.toString());
        }
        return route;
    }

    public Iterator getRoutes(JID node) {
        LinkedList list = null;
        routeLock.readLock().lock();
        try {
            if (node == null || node.getDomain() == null) {
                list = new LinkedList();
                getRoutes(list, routes);
            }
            else {
                Object nameRoutes = routes.get(node.getDomain());
                if (nameRoutes != null) {
                    if (nameRoutes instanceof ChannelHandler) {
                        list = new LinkedList();
                        list.add(nameRoutes);
                    }
                    else if (node.getNode() == null) {
                        list = new LinkedList();
                        getRoutes(list, (Hashtable)nameRoutes);
                    }
                    else {
                        Object resourceRoutes =
                                ((Hashtable)nameRoutes).get(node.getNode());
                        if (resourceRoutes != null) {
                            if (resourceRoutes instanceof ChannelHandler) {
                                list = new LinkedList();
                                list.add(resourceRoutes);
                            }
                            else if (node.getResource() == null || node.getResource().length() == 0) {
                                list = new LinkedList();
                                getRoutes(list, (Hashtable)resourceRoutes);
                            }
                            else {
                                Object entry =
                                        ((Hashtable)resourceRoutes).get(node.getResource());
                                if (entry != null) {
                                    list = new LinkedList();
                                    list.add(entry);
                                }
                            }
                        }
                    }
                }
            }
        }
        finally {
            routeLock.readLock().unlock();
        }
        if (list == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        else {
            return list.iterator();
        }
    }

    /**
     * <p>Recursive method to iterate through the given table (and any embedded tables)
     * and stuff non-Hashtable values into the given list.</p>
     * <p>There should be no recursion problems since
     * the routing table is at most 3 levels deep.</p>
     *
     * @param list  The list to stuff entries into
     * @param table The hashtable who's values should be entered into the list
     */
    private void getRoutes(LinkedList list, Hashtable table) {
        Iterator entryIter = table.values().iterator();
        while (entryIter.hasNext()) {
            Object entry = entryIter.next();
            if (entry instanceof Hashtable) {
                getRoutes(list, (Hashtable)entry);
            }
            else {
                // Do not include the same entry many times. This could be the case when the same 
                // session is associated with the bareJID and with a given resource
                if (!list.contains(entry)) {
                    list.add(entry);
                }
            }
        }
    }

    public ChannelHandler getBestRoute(JID node) throws NoSuchRouteException {

        ChannelHandler route = null;
        try {
            route = getRoute(node);
        }
        catch (NoSuchRouteException e) {
            JID defaultNode = new JID(node.getNode(), node.getDomain(), "");
            route = getRoute(defaultNode);
        }
        if (route == null) {
            throw new NoSuchRouteException();
        }
        return route;
    }

    public ChannelHandler removeRoute(JID node) {

        ChannelHandler route = null;

        routeLock.writeLock().lock();
        //System.err.println("Remove route " + node.toString());
        try {
            if (node.getNode() == null) {
                // Chop off all hosted names for this domain
                Object item = routes.remove(node.getDomain());
                if (item instanceof ChannelHandler) {
                    route = (ChannelHandler)item;
                }
            }
            else {
                Object nameRoutes = routes.get(node.getDomain());
                if (nameRoutes instanceof Hashtable) {
                    if (node.getResource() == null || node.getResource().trim().length() == 0) {
                        // Chop off all hosted resources for the given name
                        Object item = ((Hashtable)nameRoutes).remove(node.getNode());
                        if (item instanceof ChannelHandler) {
                            route = (ChannelHandler)item;
                        }
                    }
                    else {
                        Object resourceRoutes =
                                ((Hashtable)nameRoutes).get(node.getNode());
                        if (resourceRoutes instanceof Hashtable) {
                            route = (ChannelHandler)
                                    ((Hashtable)resourceRoutes).remove(node.getResource());
                            if (((Hashtable)resourceRoutes).isEmpty()) {
                                ((Hashtable)nameRoutes).remove(node.getNode());
                                if (((Hashtable)nameRoutes).isEmpty()) {
                                    routes.remove(node.getDomain());

                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error("Error removing route", e);
        }
        finally {
            routeLock.writeLock().unlock();
        }
        return route;
    }
}