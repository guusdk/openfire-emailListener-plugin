/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.dom4j.Element;

public class IQProxy extends AbstractPacketProxy implements IQ {

    protected IQ iq;

    public IQProxy(IQ iq, AuthToken authToken, Permissions permissions) {
        super(iq, authToken, permissions);
        this.iq = iq;
    }

    public String getChildNamespace() {
        return iq.getChildNamespace();
    }

    public void setChildNamespace(String namespace) {
        iq.setChildNamespace(namespace);
    }

    public String getChildName() {
        return iq.getChildName();
    }

    public void setChildName(String name) {
        iq.setChildName(name);
    }

    public XMPPFragment getChildFragment() {
        return iq.getChildFragment();
    }

    public void setChildFragment(XMPPFragment fragment) {
        iq.setChildFragment(fragment);
    }

    public IQ createResult(Element body) {
        return iq.createResult(body);
    }

    public IQ createResult() {
        return iq.createResult();
    }
}
