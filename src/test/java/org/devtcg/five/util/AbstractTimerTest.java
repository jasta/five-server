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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class AbstractTimerTest extends TestCase
{
	public void testTimerImplPrecision() throws InterruptedException
	{
		AbstractTimer timer = AbstractTimer.newInstance("TimerImpl", null);

		final long started = System.currentTimeMillis();

		final CountDownLatch latch = new CountDownLatch(1);

		Runnable r = new Runnable() {
			public void run() {
				long elapsed = System.currentTimeMillis() - started;
				assertTrue("Timer precision too low", elapsed >= 500 && elapsed <= 1500);
				latch.countDown();
			}
		};

		timer.schedule(r, 1000);
		boolean countIsZero = latch.await(2000, TimeUnit.MILLISECONDS);
		assertTrue("Timer runnable never fired", countIsZero);
	}

	public void testTimerImplMultipleScheduling() throws InterruptedException
	{
		AbstractTimer timer = AbstractTimer.newInstance("TimerImpl", null);

		final CountDownLatch latch = new CountDownLatch(1);

		Runnable r = new Runnable() {
			public void run() {
				assertTrue("Runnable executed twice", latch.getCount() == 1);
				latch.countDown();
			}
		};

		timer.schedule(r, 1000);
		Thread.sleep(500);
		timer.schedule(r, 1000);
		Thread.sleep(500);
		timer.schedule(r, 1000);
		Thread.sleep(500);
		timer.schedule(r, 1000);
		Thread.sleep(500);
		timer.schedule(r, 1000);

		boolean countIsZero = latch.await(2000, TimeUnit.MILLISECONDS);
		assertTrue("Timer runnable never fired", countIsZero);
	}
}
