/**
 * 
 */
package org.jivesoftware.wildfire.gateway.roster;

import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.xmpp.packet.JID;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public interface ForeignContact {

    /**
     * Get the JID, constructing it if necessary.
     * 
     * @return jid returns the jid.
     */
    public JID getJid();

    /**
     * Add a session to the associated sessions for this foreign contact.
     * 
     * @param session
     */
    public void addSession(GatewaySession session);

    /**
     * Remove a <code>GatewaySession</code> from the foreign contact.
     * 
     * @param session
     */
    public void removeSession(GatewaySession session);

    /**
     * Returns true if at least one associated session is connected.
     * 
     * @return connected 
     */
    public boolean isConnected();
    
    
    /**
     * Return the translated status for the contact.
     * 
     * @return Status
     */
    public Status getStatus();
    
    
    /**
     * Return the name of the contact.
     * 
     * @return name The name of the contact.
     */
    public String getName();

}
