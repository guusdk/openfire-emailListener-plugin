/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.EventListener;
import org.jivesoftware.messenger.container.EventRegistration;

/**
 * A simple registration event for services.
 *
 * @author Iain Shigeoka
 */
public class ServiceEventRegistrationImpl extends EventRegistration {

    /**
     * The listener for this registration.
     */
    private EventListener listener;

    /**
     * Create an event registration with listener and lookup
     *
     * @param serviceLookup the lookup that generated the registration.
     * @param eventListener the listener for the registration event.
     * @param eventID       the event ID to associate with this event.
     * @param sequenceNo    the sequence number this event will use
     */
    public ServiceEventRegistrationImpl(ServiceLookupImpl serviceLookup,
                                        EventListener eventListener,
                                        long eventID,
                                        long sequenceNo) {
        super(eventID, serviceLookup, sequenceNo);
        this.listener = eventListener;
    }

    public void cancel() {
        ((ServiceLookupImpl)this.getSource()).remove(listener);
    }
}
