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

package org.jivesoftware.messenger.user.spi;

import java.util.Iterator;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.messenger.user.BasicRosterItem;
import org.jivesoftware.messenger.user.IQRosterItem;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.util.ConcurrentHashSet;
import org.xmpp.packet.JID;

public class IQRosterItemImpl extends BasicRosterItem implements IQRosterItem {

    public IQRosterItemImpl(JID jid) {
        super(jid);
    }

    public IQRosterItemImpl(JID jid, String nickname, List groups) {
        super(jid, nickname, groups);
    }

    /**
     * <p>Create a copy of the given roster item.</p>
     *
     * @param item
     */
    public IQRosterItemImpl(RosterItem item) {
        super(item);
        if (item instanceof IQRosterItemImpl) {
            fragments = (ConcurrentHashSet)((IQRosterItemImpl)item).fragments.clone();
        }
    }

    public Element asXMLElement() {
        Element item = DocumentHelper.createElement("item");
        item.addAttribute("jid", jid.toBareJID());
        item.addAttribute("subscription", subStatus.getName());
        if (askStatus != ASK_NONE) {
            item.addAttribute("ask", askStatus.getName());
        }
        if (nickname != null) {
            if (nickname.trim().length() > 0) {
                item.addAttribute("name", nickname.trim());
            }
        }
        if (groups != null) {
            Iterator groupsItr = groups.iterator();
            while (groupsItr.hasNext()) {
                item.addElement("group").addText((String)groupsItr.next());
            }
        }
        return item;
    }

    public String getNamespace() {
        return "jabber:iq:roster";
    }

    public void setNamespace(String namespace) {
        // do nothing
    }

    public String getName() {
        return "item";
    }

    public void setName(String name) {
        // do nothing
    }

    private ConcurrentHashSet fragments = new ConcurrentHashSet();

    public void addFragment(Element fragment) {
        fragments.add(fragment);
    }

    public Iterator getFragments() {
        return fragments.iterator();
    }

    public Element getFragment(String name, String namespace) {
        if (fragments == null) {
            return null;
        }
        Element frag;
        for (Iterator frags = fragments.iterator(); frags.hasNext();) {
            frag = (Element)frags.next();
            if (name.equals(frag.getName()) && namespace.equals(frag.getNamespace())) {
                return frag;
            }
        }
        return null;
    }

    public void clearFragments() {
        fragments.clear();
    }

    public int getSize() {
        return fragments.size();
    }
}
