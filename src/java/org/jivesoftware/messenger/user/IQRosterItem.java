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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.XMPPFragment;
import org.dom4j.Element;

/**
 * <p>The standard XMPP IQ packet representing a roster item entry.</p>
 * <p/>
 * <p>IQRosterItems can read and write the standard XML representation of a roster item.
 * Roster items are of the form:</p>
 * <p><code><pre>
 * &lt;item jid='jid' subscription='none|both|to|from' ask='subscribe|unsubscribe' name='nickname'&gt;
 *   &lt;group&gt;Friends&lt;/group&gt;
 *   &lt;group&gt;Co-workers&lt;/group&gt;
 * &lt;/item&gt;
 * </pre></code></p>
 *
 * @author Iain Shigeoka
 */
public interface IQRosterItem extends XMPPFragment, RosterItem {

    /**
     * <p>Obtain the roster item as an XML DOM element.</p>
     *
     * @return The item as an XML DOM element
     */
    public Element asXMLElement();
}