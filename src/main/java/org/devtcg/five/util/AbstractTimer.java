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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.swt.widgets.Display;

/**
 * Abstraction to allow us to use the SWT event loop when present, and the
 * standard java.util.Timer interface when running in "console" mode.
 */
public abstract class AbstractTimer
{
	private static final Log LOG = LogFactory.getLog(AbstractTimer.class);

	public static AbstractTimer newInstance(String name, Display display)
	{
		if (display == null)
			return new TimerImpl(name);
		else
			return new DisplayImpl(display);
	}

	/**
	 * Schedule a runnable. If you schedule the same runnable multiple times,
	 * only the most recent delay will be honored.
	 */
	public abstract void schedule(Runnable runnable, long delay);

	private static class TimerImpl extends AbstractTimer
	{
		private final Timer mTimer;

		/**
		 * Maintains a collection of all pending tasks so that we can detect
		 * duplicate events and unschedule the previous entry. This causes Timer
		 * to match the semantics of the SWT Display event loop.
		 */
		private final Map<Runnable, WeakReference<TimerTask>> mTasks =
			Collections.synchronizedMap(new WeakHashMap<Runnable, WeakReference<TimerTask>>());

		public TimerImpl(String name)
		{
			super();
			mTimer = new Timer(name, true);
		}

		@Override
		public void schedule(final Runnable runnable, long delay)
		{
			TimerTask task = new TimerTask() {
				public void run() {
					mTasks.remove(runnable);
					runnable.run();
				}
			};

			synchronized(mTasks) {
				/* Cancel any previously scheduled task. */
				WeakReference<TimerTask> oldTaskRef = mTasks.remove(runnable);
				TimerTask oldTask = (oldTaskRef != null) ? oldTaskRef.get() : null;
				if (oldTask != null && !oldTask.cancel())
				{
					/* Hmm, this is unlikely, but not impossible.  Just warn. */
					if (LOG.isDebugEnabled())
						LOG.debug("Old task failed to cancel but it is not likely to be running.");
				}

				mTasks.put(runnable, new WeakReference<TimerTask>(task));
			}

			mTimer.schedule(task, delay);
		}
	}

	private static class DisplayImpl extends AbstractTimer
	{
		private final Display mDisplay;

		public DisplayImpl(Display display)
		{
			super();
			mDisplay = display;
		}

		@Override
		public void schedule(Runnable runnable, long delay)
		{
			mDisplay.timerExec((int)delay, runnable);
		}
	}
}
