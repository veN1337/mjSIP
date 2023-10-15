/*
 * Copyright (C) 2013 Luca Veltri - University of Parma - Italy
 * 
 * This file is part of MjSip (http://www.mjsip.org)
 * 
 * MjSip is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * MjSip is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MjSip; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.mjsip.sip.provider;



import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.time.Scheduler;
import org.zoolu.net.IpAddress;



/** Connected SIP provider.
  * It pre-establishes semi-permanent transport connections to the outbound proxy.
  * <br>
  * It establishes one connection per each connection-oriented transport protocol.
  * <p>
  * Methods getContactAddress() returns a contact address that refer to the
  * pre-establishes semi-permanent transport connections (if any).
  */
public class ConnectedSipProvider extends SipProvider {
	

	/** The semi-permanet connections, one for each connection-oriented porotocol (Hastable<String proto, SipTransportConnection transport_connection>) */
	Hashtable connections=new Hashtable();


	/**
	 * Creates a {@link ConnectedSipProvider}.
	 */
	public ConnectedSipProvider(SipConfig sipConfig, Scheduler scheduler) throws IOException {
		super(sipConfig, scheduler);
		setForceRport(true);
		connect();
	}

	/** Connects to the Oubound Proxy. */ 
	private void connect() throws IOException {
		if (sip_transports==null || sipConfig().getOutboundProxy()==null) return;
		// else
		IpAddress proxy_addr=IpAddress.getByName(sipConfig().getOutboundProxy().getHost());
		int proxy_port=sipConfig().getOutboundProxy().getPort();
		if (proxy_port<=0) proxy_port=5060;
		String proxy_proto=(sipConfig().getOutboundProxy().hasTransport())? sipConfig().getOutboundProxy().getTransport() : null;
		
		for (Enumeration i=sip_transports.elements(); i.hasMoreElements(); ) {
			try {
				SipTransportCO sip_transport=(SipTransportCO)i.nextElement();
				sip_transport.setForceSentBy(true);
				String proto=sip_transport.getProtocol();
				if (proxy_proto==null || proxy_proto.equals(proto)) {
					// establish connection
					SipTransportConnection conn=sip_transport.addConnection(proxy_addr,(isSecureTransport(proto))? proxy_port+1 : proxy_port);
					if (conn!=null) connections.put(sip_transport.getProtocol(),conn);
				}
			}
			catch (Exception e) {}
		}
	}


	/** Adds a semi-permanent transport connection. 
	  * Pre-establishes a transport connection to a remote socket address.
	  * @param proto transport protocol
	  * @param remote_addr remote address
	  * @param remote_port remote port
	  * @return the transport connection (if succeded in finding the corresponding SIP transport service), or null (if failed) */ 
	/*public SipTransportConnection addTransportConnection(String proto, String remote_addr, int remote_port) throws IOException {
		SipTransportCO sip_transport=null;
		sip_transport=(SipTransportCO)sip_transports.get(proto.toLowerCase());
		if (sip_transport==null) throw new IOException("Protocol '"+proto+"' is not supported by this SIP provider");
		// else
		return sip_transport.addConnection(new IpAddress(remote_addr),remote_port);
	}*/


	/** Adds a pre-established transport connection.
	  * @param conn the pre-established trasport connection */ 
	/*public void addTransportConnection(SipTransportConnection conn) throws IOException {
		SipTransportCO sip_transport=null;
		String proto=conn.getProtocol().toLowerCase();
		sip_transport=(SipTransportCO)sip_transports.get(proto);
		if (sip_transport==null) throw new IOException("Protocol '"+proto+"' is not supported by this SIP provider");
		// else
		sip_transport.addConnection(conn);
	}*/


	/** Removes a semi-permanent transport connection. */ 
	/*public void removeTransportConnection(String proto, String remote_addr, int remote_port) throws IOException {
		SipTransportCO sip_transport=(SipTransportCO)sip_transports.get(proto.toLowerCase());
		if (sip_transport==null) throw new IOException("Protocol '"+proto+"' is not supported by this SIP provider");
		// else
		sip_transport.removeConnection(new ConnectionId(proto,new IpAddress(remote_addr),remote_port));
	}*/


	/** Gets a valid SIP or SIPS contact address.
	  * @param secure whether returning a SIPS or SIP URI (true=SIPS, false=SIP)
	  * @param user local user's name
	  * @return a SIP or SIPS contact URI for this SIP provider */
	@Override
	public NameAddress getContactAddress(String user, boolean secure) {
		for (Enumeration i=connections.elements(); i.hasMoreElements(); ) {
			SipTransportConnection conn=(SipTransportConnection)i.nextElement();
			String proto=conn.getProtocol();
			if (secure==isSecureTransport(proto)) {
				String local_addr=conn.getLocalAddress().toString();
				int local_port=conn.getLocalPort();
				if (secure) local_port--;
				SipURI contact_uri=new SipURI(user,local_addr,local_port);
				if (secure) contact_uri.setSecure(true);
				else if (!proto.equalsIgnoreCase(SipProvider.PROTO_UDP)) contact_uri.addTransport(proto);
				return new NameAddress(contact_uri);
			}
		}
		// else
		return super.getContactAddress(user,secure);
	}
		
}
