/*
 * Copyright (C) 2007 Luca Veltri - University of Parma - Italy
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

package org.mjsip.ua;


import java.util.concurrent.ScheduledFuture;

import org.mjsip.pool.PortPool;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.call.ExtendedCall;
import org.mjsip.sip.call.RegistrationClient;
import org.mjsip.sip.call.RegistrationClientListener;
import org.mjsip.sip.call.RegistrationOptions;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.message.SipMethods;
import org.mjsip.sip.provider.MethodId;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipProviderListener;
import org.slf4j.LoggerFactory;


/** MultipleUAS is a simple UA that automatically responds to incoming calls.
  * <br>
  * At start up it may register with a registrar server (if properly configured).
  */
public abstract class MultipleUAS implements RegistrationClientListener, SipProviderListener {
	
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MultipleUAS.class);

	/** UserAgentProfile */
	protected UAOptions _uaConfig;
			
	/** SipProvider */
	protected SipProvider sip_provider;

	private final PortPool _portPool;
	
	private final int _hangupTime;

	private final RegistrationOptions _clientRegOptions;

	private RegistrationClient _rc;

	/**
	 * Creates a {@link MultipleUAS}.
	 */
	public MultipleUAS(SipProvider sip_provider, PortPool portPool, RegistrationOptions regOptions, UAOptions uaConfig, ServiceOptions serviceConfig) {
		this.sip_provider=sip_provider;
		_portPool = portPool;
		_uaConfig=uaConfig;
		_hangupTime = serviceConfig.getHangupTime();
		_clientRegOptions = regOptions.noRegistration();

		register(regOptions);
		
		// start UAS     
		sip_provider.addSelectiveListener(new MethodId(SipMethods.INVITE),this); 
	}

	/**
	 * Registers at the registrar.
	 */
	public void register(RegistrationOptions regConfig) {
		_rc = new RegistrationClient(sip_provider, regConfig, this);
		_rc.loopRegister(regConfig.getExpires(),regConfig.getExpires()/2);
	}

	/**
	 * Cancels registration.
	 */
	public void unregister() {
		if (_rc != null) {
			_rc.unregister();
			_rc.halt();
			_rc = null;
		}
	}
	
	/**
	 * Unregisters from the SIP provider.
	 */
	public void halt() {
		sip_provider.removeSelectiveListener(new MethodId(SipMethods.INVITE));
	}

	// ******************* SipProviderListener methods  ******************

	/** From SipProviderListener. When a new Message is received by the SipProvider. */
	@Override
	public void onReceivedMessage(SipProvider sip_provider, SipMessage msg) {
		LOG.debug("onReceivedMessage()");
		if (msg.isRequest() && msg.isInvite()) {
			onInviteReceived(sip_provider, msg);
		}
	}

	/**
	 * Handles an SIP invite.
	 * 
	 * <p>
	 * By default the call is accepted and a {@link UserAgent} created for handling the new call.
	 * </p>
	 *
	 * @param sip_provider
	 *        The sip stack.
	 * @param msg
	 *        The invite message.
	 * 
	 * @see #createCallHandler(SipMessage)
	 */
	protected void onInviteReceived(SipProvider sip_provider, SipMessage msg) {
		LOG.info("Received new INVITE request: " + msg);
		
		AutoHangup autoHangup;
		UserAgentListener listener = createCallHandler(msg);
		if (_hangupTime > 0) {
			autoHangup = new AutoHangup();
			listener = listener.andThen(autoHangup);
		} else {
			autoHangup = null;
		}
		
		final UserAgent ua=new UserAgent(sip_provider, _portPool, _clientRegOptions, _uaConfig, listener);
		
		// since there is still no proper method to init the UA with an incoming call, trick it by using the onNewIncomingCall() callback method
		new ExtendedCall(sip_provider,msg,ua);
		
		if (autoHangup != null) {
			autoHangup.start(ua);
		}
	}

	/**
	 * Creates a handler for controlling the call started with the given invite message.
	 * 
	 * @param msg
	 *        The message that represents the invite to the new call.
	 *
	 * @return The handler for controlling the user agent that handles the new call.
	 */
	protected abstract UserAgentListener createCallHandler(SipMessage msg);

	// *************** RegistrationClientListener methods ****************

	/** From RegistrationClientListener. When a UA has been successfully (un)registered. */
	@Override
	public void onRegistrationSuccess(RegistrationClient rc, NameAddress target, NameAddress contact, int expires, String result) {
		LOG.info("Registration success: expires="+expires+": "+result);
	}

	/** From RegistrationClientListener. When a UA failed on (un)registering. */
	@Override
	public void onRegistrationFailure(RegistrationClient rc, NameAddress target, NameAddress contact, String result) {
		LOG.info("Registration failure: "+result);
	}

	private final class AutoHangup implements UserAgentListenerAdapter {
		private ScheduledFuture<?> _hangupTimer;
	
		@Override
		public void onUaCallClosed(UserAgent ua) {
			if (_hangupTimer != null) _hangupTimer.cancel(false);
		}
	
		public void start(UserAgent ua) {
			_hangupTimer = sip_provider.scheduler().schedule(_hangupTime*1000, () -> ua.hangup());			
		}
	}

}
