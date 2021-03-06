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

package org.devtcg.five.util;

import junit.framework.TestCase;

public class CancelableThreadTest extends TestCase
{
	public void testExpectedCancelation() throws Exception
	{
		WaitUntilCanceledThread thread = new WaitUntilCanceledThread();

		thread.start();
		thread.waitForStartup();

		/*
		 * TODO: Add a requestCancelAndWait(long) method to wait up to a length
		 * of time. This will help us report an error condition in the
		 * CancelableThread class.
		 */
		thread.requestCancelAndWait();

		/* If we made it here, the cancel worked. */
		assertFalse(thread.isAlive());
	}

	public void testPrematureCancelation() throws Exception
	{
		WaitUntilCanceledThread thread = new WaitUntilCanceledThread();

		thread.start();
		thread.requestCancelAndWait();

		assertFalse(thread.isAlive());
	}

	private static class WaitUntilCanceledThread extends CancelableAndTestableThread
	{
		private final Object mWaitUntilCanceled = new Object();

		@Override
		protected void onRun()
		{
			synchronized(mWaitUntilCanceled) {
				while (hasCanceled() == false) {
					try {
						mWaitUntilCanceled.wait();
					} catch (InterruptedException e) {}
				}
			}
		}

		@Override
		protected void onRequestCancel()
		{
			synchronized(mWaitUntilCanceled) {
				mWaitUntilCanceled.notify();
			}
		}
	}

	private abstract static class CancelableAndTestableThread extends CancelableThread
	{
		private boolean mStarted = false;
		private final Object mStartLock = new Object();

		public void run()
		{
			if (hasCanceled())
				return;

			synchronized(mStartLock) {
				mStarted = true;
				mStartLock.notify();
			}

			onRun();
		}

		protected abstract void onRun();

		public void waitForStartup()
		{
			synchronized(mStartLock) {
				while (mStarted == false)
				{
					try {
						mStartLock.wait();
					} catch (InterruptedException e) {}
				}
			}
		}
	}
}
