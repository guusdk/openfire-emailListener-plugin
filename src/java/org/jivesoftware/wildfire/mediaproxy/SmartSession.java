/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.mediaproxy;

import java.io.IOException;
import java.net.*;

/**
 * A Session Class will control "receive and relay" proccess.
 * It creates UDP channels from Host A to Host B and from Host B to Host A using or NOT the specified hosts and ports.
 * The IP and port pairs can change depending of the Senders IP and port.
 * Which means that the IP and port values of the points can dynamic change after the Channel is opened.
 * When the agent receives a packet from Point A, the channel set the point A IP and port according to the received packet sender IP and port.
 * Every packet received from Point B will be relayed to the new Point A IP and port.
 * When the agent receives a packet from Point B, the channel set the point B IP and port according to the received packet sender IP and port.
 * Every packet received from Point A will be relayed to the new Point B IP and port.
 * Create a dynamic channel between two IPs. ( Dynamic Point A - Dynamic Point B )
 * It has 4 Channels. 2 for data and 2 for control.
 */

public class SmartSession extends MediaProxySession {

    /**
     * Creates a new Smart Session to provide connectivity between Host A and Host B.
     *
     * @param id        of the Session (Could be a Jingle session ID)
     * @param localhost The localhost IP that will listen for UDP packets
     * @param hostA     the hostname or IP of the point A of the Channel
     * @param portA     the port number point A of the Channel
     * @param hostB     the hostname or IP of the point B of the Channel
     * @param portB     the port number point B of the Channel
     * @param creator   the created name or description of the Channel
     * @param minPort   the minimal port number to be used by the proxy
     * @param maxPort   the maximun port number to be used by the proxy
     */
    public SmartSession(String id, String creator, String localhost, String hostA, int portA, String hostB, int portB, int minPort, int maxPort) {
        super(id, creator, localhost, hostA, portA, hostB, portB, minPort, maxPort);
    }

    /**
     * Creates a new Smart Session to provide connectivity between Host A and Host B.
     *
     * @param id        of the Session (Could be a Jingle session ID)
     * @param localhost The localhost IP that will listen for UDP packets
     * @param hostA     the hostname or IP of the point A of the Channel
     * @param portA     the port number point A of the Channel
     * @param hostB     the hostname or IP of the point B of the Channel
     * @param portB     the port number point B of the Channel
     * @param creator   the created name or description of the Channel
     */
    public SmartSession(String id, String creator, String localhost, String hostA, int portA, String hostB, int portB) {
        super(id, creator, localhost, hostA, portA, hostB, portB, 10000, 20000);
    }

    /**
     * Thread override method
     */
    public void run() {

        channelAtoB = new SmartChannel(socketA, hostB, portB);
        channelAtoBControl = new SmartChannel(socketAControl, hostB, portB + 1);
        channelBtoA = new SmartChannel(socketB, hostA, portA);
        channelBtoAControl = new SmartChannel(socketBControl, hostA, portA + 1);

        channelAtoB.addListener((SmartChannel) channelBtoA);
        channelAtoBControl.addListener((SmartChannel) channelBtoAControl);
        channelBtoA.addListener((SmartChannel) channelAtoB);
        channelBtoAControl.addListener((SmartChannel) channelAtoBControl);

        threadAtoB = new Thread(channelAtoB);
        threadAtoBControl = new Thread(channelAtoBControl);
        threadBtoA = new Thread(channelBtoA);
        threadBtoAControl = new Thread(channelBtoAControl);

        threadAtoB.start();
        threadAtoBControl.start();
        threadBtoA.start();
        threadBtoAControl.start();

        channelAtoB.addListener(this);
        channelAtoBControl.addListener(this);
        channelBtoA.addListener(this);
        channelBtoAControl.addListener(this);

    }

    /**
     * Protected Class Channel.
     * Listen packets from defined dataSocket and send packets to the defined host.
     * But also provides a mechanism to dynamic bind host and port implementing DatagramListener methods to change the host and port values according to the received packets.
     */
    protected class SmartChannel extends Channel implements Runnable, DatagramListener {
        int c = 0;

        /**
         * Default Channel Constructor
         *
         * @param dataSocket datasocket to used to send and receive packets
         * @param host       default destination host for received packets
         * @param port       default destination port for received packets
         */
        public SmartChannel(DatagramSocket dataSocket, InetAddress host, int port) {
            super(dataSocket, host, port);
        }

        /**
         * Thread override method
         */
        public void run() {
            try {
                long c = 0;
                long band = System.currentTimeMillis();
                while (true) {
                    // Block until a datagram appears:
                    packet = new DatagramPacket(buf, buf.length);
                    dataSocket.receive(packet);

                    // Relay Destination
                    if (c++ < 100) { // 100 packets are enough to discover relay address
                        this.setHost(packet.getAddress());
                        this.setPort(packet.getPort());
                    } else {
                        c = 1000; // Prevents long overflow
                        // Check Source Address. If it�s different, discard packet. 
                        if (!this.getHost().equals(packet.getAddress())) continue;
                    }

                    boolean resend = true;

                    for (DatagramListener dl : listeners) {
                        boolean send = dl.datagramReceived(packet);
                        if (resend)
                            if (!send)
                                resend = false;
                    }

                    if (resend) relayPacket(packet);

                }
            } catch (UnknownHostException e) {
                if (enabled)
                    System.err.println("Unknown Host");
            }
            catch (SocketException e) {
                if (enabled)
                    System.err.println("Socket closed");
            } catch (IOException e) {
                if (enabled)
                    System.err.println("Communication error");
                e.printStackTrace();
            }
        }

        /**
         * Implement DatagramListener method.
         * Set the host and port value to the host and port value from the received packet.
         *
         * @param datagramPacket the received packet
         */
        public boolean datagramReceived(DatagramPacket datagramPacket) {
            //InetAddress host = datagramPacket.getAddress();
            //this.setHost(host);
            //int port = datagramPacket.getPort();
            //this.setPort(port);
            this.relayPacket(datagramPacket);

            return false;
        }
    }
}
