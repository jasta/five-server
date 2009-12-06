/*
 * Copyright (C) 2009 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sbbi.upnp.devices.UPNPRootDevice;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.ActionResponse;
import net.sbbi.upnp.messages.UPNPResponseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.util.CancelableThread;

/**
 * Provides UPnP services to the five application (such as NAT port mapping).
 * Does not implement a UPnP server as the name might suggest.
 */
public class UPnPService
{
	private static final Log LOG = LogFactory.getLog(UPnPService.class);

	private static final UPnPService sInstance = new UPnPService();

	private static final int SCAN_TIMEOUT = 5000;

	final Set<NatMapping> mNatMappings = new HashSet<NatMapping>();
	private InetAddress mLocalAddress;

	/** Worker thread used to add or delete port mappings. */
	CancelableThread mWorker;
	private boolean mEnabled;
	private boolean mMapped;

	private final List<MappingListener> mMappingListeners =
		new LinkedList<MappingListener>();

	private UPnPService() {}

	public static UPnPService getInstance()
	{
		return sInstance;
	}

	/**
	 * Enable UPnP and initiate a background request to add our port mapping.
	 */
	public synchronized void enableUPnP()
	{
		if (mEnabled)
			return;

		mMapped = false;

		if (mWorker != null)
			mWorker.requestCancel();

		mWorker = new AddPortMappingsThread();
		mWorker.start();

		mEnabled = true;
	}

	/**
	 * Disable UPnP and remove any port mappings we registered. This should be
	 * called during application shutdown.
	 */
	public synchronized void disableUPnP()
	{
		if (!mEnabled)
			return;

		if (mWorker != null)
			mWorker.requestCancel();

		if (mMapped)
		{
			mWorker = new DeletePortMappingsThread();
			mWorker.start();
		}

		mEnabled = false;
	}

	public void addMappingListener(MappingListener listener)
	{
		mMappingListeners.add(listener);
	}

	public void removeMappingListener(MappingListener listener)
	{
		mMappingListeners.remove(listener);
	}

	public synchronized boolean isMapped()
	{
		return mMapped;
	}

	public synchronized boolean isEnabled()
	{
		return mEnabled;
	}

	/**
	 * Determine the source IP (our IP) of our route to www.google.com:80. If
	 * the user has layers of indirection to access the Internet, it is our
	 * assumption that this function will return an address in the RFC1918
	 * private space; otherwise, no IGD mappings are necessary.
	 *
	 * @throws IOException
	 */
	private static InetAddress determineDefaultRouteLocalAddress() throws IOException
	{
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress("www.google.com", 80));
			return socket.getLocalAddress();
		} finally {
			socket.close();
		}
	}

	private synchronized InetAddress getDefaultRouteLocalAddress() throws IOException
	{
		if (mLocalAddress == null)
			mLocalAddress = determineDefaultRouteLocalAddress();

		return mLocalAddress;
	}

	@Override
	protected void finalize() throws Throwable
	{
		if (!mNatMappings.isEmpty())
		{
			throw new IllegalStateException(
					"UPnPService must be cleaned up using removeNatMappings");
		}
	}

	static class NatMapping
	{
		public final InternetGatewayDevice device;
		public final String internalIp;
		public final String protocol;
		public final int port;

		public NatMapping(InternetGatewayDevice device, String internalIp, String protocol, int port)
		{
			this.device = device;
			this.internalIp = internalIp;
			this.protocol = protocol;
			this.port = port;
		}

		public String toString()
		{
			return "{device=" + device + "; internalIp=" + internalIp +
					"; protocol=" + protocol + "; port=" + port + "}";
		}
	}

	/**
	 * Tests whether the mapping is already registered with the remote device.
	 * Note that this has nothing to do with whether it is in
	 * {@link #mNatMappings}.
	 */
	static boolean isAlreadyMapped(CancelableThread thread, InternetGatewayDevice device,
			String localIp, int port)	throws IOException, UPNPResponseException
	{
		int mappings = device.getNatMappingsCount();

		String portString = String.valueOf(port);

		if (thread != null && thread.hasCanceled())
			return false;

		for (int j = 0; j < mappings; j++) {
			ActionResponse response = device.getGenericPortMappingEntry(j);

			if (thread != null && thread.hasCanceled())
				return false;

			if (!localIp.equals(response.getOutActionArgumentValue("NewInternalClient")))
				continue;
			if (!portString.equals(response.getOutActionArgumentValue("NewExternalPort")))
				continue;
			if (!portString.equals(response.getOutActionArgumentValue("NewInternalPort")))
				continue;
			if (!"1".equals(response.getOutActionArgumentValue("NewEnabled")))
				continue;

			return true;
		}

		return false;
	}

	private class AddPortMappingsThread extends CancelableThread
	{
		public AddPortMappingsThread()
		{
			super();
			setName("AddPortMappings");
			setDaemon(true);
		}

		public void run()
		{
			boolean mapped = false;
			boolean ready = false;
			try {
				if (!needsPortMapping())
					ready = true;
				else
				{
					mapped = addPortMapping(Configuration.getInstance().getServerPort());
					ready = true;
				}
			} catch (Exception e) {
				if (LOG.isWarnEnabled())
					LOG.warn("Unable to add NAT port mappings", e);
			} finally {
				synchronized(UPnPService.this) {
					mMapped = mapped;
				}
				for (MappingListener listener: mMappingListeners)
					listener.onMappingReady(ready);
			}
		}

		private boolean needsPortMapping() throws IOException
		{
			InetAddress address = getDefaultRouteLocalAddress();
			return address.isSiteLocalAddress();
		}

		private boolean addPortMapping(int port) throws IOException
		{
			NatMapping mapping = internalAddPortMapping(port);
			if (mapping == null)
				return false;

			synchronized(mNatMappings) {
				mNatMappings.add(mapping);
			}

			return true;
		}

		private NatMapping internalAddPortMapping(int port) throws IOException
		{
			if (LOG.isDebugEnabled())
				LOG.debug("Searching for UPnP Internet Gateway devices...");

			InternetGatewayDevice[] devices = InternetGatewayDevice.getDevices(SCAN_TIMEOUT);

			if (LOG.isDebugEnabled())
				LOG.debug("Found " + (devices != null ? devices.length : 0) + " devices.");

			if (hasCanceled() || devices == null || devices.length == 0)
				return null;

			String localIp = getDefaultRouteLocalAddress().getHostAddress();
			String mappingDesc = "Five server to " + localIp;

			/*
			 * Walk through all returned devices looking for the first device with a
			 * non-local IP that allows us to add mappings.
			 */
			for (InternetGatewayDevice device: devices)
			{
				if (hasCanceled())
					return null;

				try {
					UPNPRootDevice rootDevice = device.getIGDRootDevice();

					String externalIp = device.getExternalIPAddress();
					InetAddress externalAddr = InetAddress.getByName(externalIp);
					if (externalAddr.isSiteLocalAddress())
					{
						if (LOG.isWarnEnabled())
						{
							LOG.warn("Ignoring device[" + rootDevice.getModelName() +
									"], external IP reported as " + externalIp);
						}

						continue;
					}

					if (hasCanceled())
						return null;

					if (isAlreadyMapped(this, device, localIp, port))
						return new NatMapping(device, localIp, "TCP", port);

					boolean mapped = device.addPortMapping(mappingDesc, null, port, port,
							localIp, 0, "TCP");

					/*
					 * XXX: We can't handle cancellation while executing
					 * addPortMapping. The implication here is that we might
					 * leave a port mapping during the life of the program which
					 * ought not be there according to user preference.
					 */

					if (mapped)
						return new NatMapping(device, localIp, "TCP", port);
					else
					{
						if (LOG.isWarnEnabled())
						{
							LOG.warn("Failed to map port " + port + " to " + localIp + ":" + port
									+ " on device[" + rootDevice.getModelName() + "]");
						}
					}
				} catch (Exception e) {
					if (LOG.isWarnEnabled())
						LOG.warn("Network error occurred in UPnP communication", e);
				}
			}

			if (LOG.isWarnEnabled())
				LOG.warn("Exhausted IGD device list, UPnP port mapping unsuccessful.");

			return null;
		}
	}

	private class DeletePortMappingsThread extends CancelableThread
	{
		public DeletePortMappingsThread()
		{
			super();
			setName("DeletePortMappings");

			/*
			 * We want this to stick around otherwise we'll leave the UPnP
			 * mappings on our IGD in an unclean state after exit.
			 */
			setDaemon(false);
		}

		public void run()
		{
			try {
				removePortMappings();
			} finally {
				synchronized(UPnPService.this) {
					mMapped = false;
				}
			}
		}

		/**
		 * Removes all mappings that were added by us.
		 */
		private void removePortMappings()
		{
			Set<NatMapping> mappingsCopy;
			synchronized(mNatMappings) {
				if (mNatMappings.isEmpty())
					return;

				mappingsCopy = new HashSet<NatMapping>(mNatMappings);
			}

			for (NatMapping mapping: mappingsCopy)
			{
				try {
					mapping.device.deletePortMapping(null, mapping.port, mapping.protocol);
				} catch (Exception e) {
					if (LOG.isWarnEnabled())
						LOG.warn("Cannot delete port mapping " + mapping);
				} finally {
					synchronized(mNatMappings) {
						mNatMappings.remove(mapping);
					}
				}
			}
		}
	}

	public interface MappingListener
	{
		public void onMappingReady(boolean success);
	}
}
