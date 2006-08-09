/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 *
 * Heavily inspired by joscardemo of the Joust Project: http://joust.kano.net/
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import net.kano.joscar.snac.SnacRequest;

/**
 * Handles events from pending SNAC manager.
 * 
 * @author Daniel Henninger
 * Heavily inspired by joscardemo from the joscar project.
 */
public interface PendingSnacListener {
    void dequeueSnacs(SnacRequest[] pending);
}
