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
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;
import net.sbbi.upnp.messages.UPNPResponseException;

import org.devtcg.five.server.UPnPService.MappingListener;
import org.devtcg.five.server.UPnPService.NatMapping;

public class UPnPServiceTest extends TestCase
{
	public void testNattedPunchthroughAndTeardown() throws IOException, UPNPResponseException
	{
		new EnableTester().run();
	}

	private static class EnableTester
	{
		private boolean mQuit = false;
		private final Object mQuitLock = new Object();

		private volatile boolean mReady = false;

		public void run() throws IOException, UPNPResponseException
		{
			UPnPService service = UPnPService.getInstance();
			service.addMappingListener(new MappingListener() {
				public void onMappingReady(boolean ready)
				{
					mReady = ready;
					synchronized(mQuitLock) {
						mQuit = true;
						mQuitLock.notify();
					}
				}
			});

			service.enableUPnP();

			synchronized(mQuitLock) {
				while (mQuit == false)
				{
					try {
						mQuitLock.wait();
					} catch (InterruptedException e) {}
				}
			}

			/* XXX: This won't be equal if not connected through NAT! */
			assertEquals(mReady, service.isMapped());
			assertFalse(service.mNatMappings.isEmpty());

			/*
			 * Copy here so that we can first verify that the mappings exist,
			 * and then after disabling UPnP check that these same mappings do
			 * not exist.
			 */
			Set<NatMapping> mappingsCopy;
			synchronized(service) {
				mappingsCopy = new HashSet<NatMapping>(service.mNatMappings);
			}

			/* Verify that all mappings in mNatMappings are confirmed by the UPnP node. */
			verifyMappings(service, mappingsCopy, true);

			service.disableUPnP();
			service.mWorker.joinUninterruptibly();

			verifyMappings(service, mappingsCopy, false);
		}

		private static void verifyMappings(UPnPService service, Set<NatMapping> mappings,
				boolean expectedMappedValue) throws IOException, UPNPResponseException
		{
			for (NatMapping mapping: mappings)
			{
				boolean mapped = UPnPService.isAlreadyMapped(null, mapping.device,
						mapping.internalIp, mapping.port);
				if (expectedMappedValue)
				{
					assertTrue("Reported successful mapping of port " + mapping.port
							+ " that could not be confirmed", mapped);
				}
				else
				{
					assertFalse("Failed to remove mapping of port " + mapping.port, mapped);
				}
			}
		}
	}
}
