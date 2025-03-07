/*
 * Copyright (C) 2005 Luca Veltri - University of Parma - Italy
 * 
 * This source code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.mjsip.server.sbc;



import org.mjsip.sip.header.ViaHeader;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.ConnectionId;
import org.mjsip.sip.provider.SipOptions;
import org.mjsip.sip.provider.SipTransport;
import org.mjsip.time.Scheduler;
import org.slf4j.LoggerFactory;
import org.zoolu.net.SocketAddress;



/** ExtendedSipProvider extends SipProvider implementing
  * symmetric SIP (over symmetric UDP), allowing symmetric NAT traversal.
  * <p>
  * When sending a SIP message, as destination address it is taken
  * the source address used in the opposite direction,
  * that is the address where the messages in the opposite direction came from
  * (reverse forwarding).
  */
public class ExtendedSipProvider extends org.mjsip.sip.provider.SipProvider {
	
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ExtendedSipProvider.class);

	/** Binder of pairs of SocketAddresses */
	AddressResolver address_resolver;

	/** Inits private attribute. */ 
	private void init(long refresh_time, long keepalive_time) {
		if (keepalive_time > 0)
			address_resolver = new AddressResolverKeepAlive(this, refresh_time, keepalive_time);
		else
			address_resolver = new AddressResolver(this, refresh_time);
	}

	/** 
	 * Constructs an {@link ExtendedSipProvider}. 
	 */ 
	public ExtendedSipProvider(SipOptions sipConfig, Scheduler scheduler, long refresh_time, long keepalive_time) {
		super(sipConfig, scheduler);
		init(refresh_time,keepalive_time);
	}

	//********************** extended methods **********************

	/** From TransportListener. When a new SIP message is received. */
	@Override
	public synchronized void onReceivedMessage(SipTransport transport, SipMessage msg) {
		
		SocketAddress src_soaddr=new SocketAddress(msg.getRemoteAddress(),msg.getRemotePort());
		
		// update src address binding for received message
		if (msg.isRequest())
		try {
			// maintain socket address bindiding for symmetring NAT
			ViaHeader via=msg.getViaHeader();
			SocketAddress via_soaddr=new SocketAddress(via.getHost(),(via.hasPort())?via.getPort():sipConfig().getDefaultPort());
			if (via_soaddr.equals(src_soaddr)) {
				if (address_resolver.contains(via_soaddr)) {
					// remove binding
					address_resolver.removeBinding(via_soaddr);        
				}
			}
			else {
				// update binding
				address_resolver.updateBinding(via_soaddr,src_soaddr);
			}
		}
		catch (Exception e) {
			LOG.warn("Error managing addressing material", e);
		}

		super.onReceivedMessage(transport,msg);
	}
	

	/** Sends a SipMessage, specifing the transport portocol, nexthop address and port. */
	@Override
	public ConnectionId sendMessage(SipMessage msg, String proto, String dest_addr, int dest_port, int ttl) {
		// logs
		String foot_print=msg.getFirstLine();
		if (foot_print==null) foot_print="NOT a SIP message\r\n";
		LOG.info("Sending message ({} bytes): {}",msg.getLength(), foot_print);
		String refer_addr=dest_addr;
		int refer_port=dest_port;
		if (msg.isResponse()) {
			// try to look if the via parameter "received" has changed the destination address
			ViaHeader via=msg.getViaHeader();
			if (via.hasReceived()) {
				refer_addr=via.getHost();
			}
			// try to look if the via parameter "rport" has changed the destination port
			if (via.hasRport()) {
				refer_port=via.getPort();
			}
		}
		SocketAddress refer_soaddr=new SocketAddress(refer_addr,refer_port);
		SocketAddress dest_soaddr=refer_soaddr;
		//printLog("DEBUG: refer_soaddr="+refer_soaddr.toString(),LogWriter.LEVEL_HIGH);
		//for (Enumeration e=address_resolver.getAllSocketAddresses(); e.hasMoreElements(); )
		//{  printLog("DEBUG: resolv_soaddr="+((String)e.nextElement()),LogWriter.LEVEL_HIGH);
		//}
		if (address_resolver.contains(refer_soaddr)) {
			dest_soaddr=address_resolver.getSocketAddress(refer_soaddr);
			LOG.info("CHANGING DESTINATION {} >> {}", refer_soaddr, dest_soaddr);
			dest_addr=dest_soaddr.getAddress().toString();
			dest_port=dest_soaddr.getPort();
		}
		else {
			LOG.info("destination unchanged: {}", dest_soaddr);
		}
		
		return super.sendRawMessage(msg,proto,dest_addr,dest_port,ttl);
	}
}
