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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.FutureTask;

/**
 * Simple single thread executor which can be canceled and waited upon more
 * reliably than ThreadPoolExecutor.
 */
public class CancelableExecutor
{
	private Worker mWorker;

	private final BlockingQueue<FutureTask<?>> mQueue;

	private volatile boolean mShutdown;
	private volatile boolean mCanceled;

	public CancelableExecutor(BlockingQueue<FutureTask<?>> queue)
	{
		mQueue = queue;
	}

	public synchronized void execute(FutureTask<?> command)
	{
		if (mWorker == null)
		{
			mWorker = new Worker();
			mWorker.start();
		}

		try {
			mQueue.put(command);
		} catch (InterruptedException e) {
		}
	}

	public synchronized void shutdownAndWait()
	{
		mShutdown = true;

		if (mWorker != null)
		{
			mWorker.interrupt();
			mWorker.joinUninterruptibly();
		}
	}

	public synchronized void requestCancel()
	{
		mShutdown = true;
		mCanceled = true;

		if (mWorker != null)
			mWorker.requestCancel();
	}

	private class Worker extends CancelableThread
	{
		private FutureTask<?> mCurrentTask;
		private final Object mLock = new Object();

		@Override
		protected void onRequestCancel()
		{
			interrupt();

			synchronized (mLock) {
				if (mCurrentTask != null)
					mCurrentTask.cancel(true);
			}
		}

		private FutureTask<?> getTask() throws InterruptedException
		{
			FutureTask<?> task;

			if (mShutdown == true)
			{
				task = mQueue.poll();
				if (task != null)
					return task;

				if (mQueue.isEmpty())
					return null;
			}

			return mQueue.take();
		}

		public void run()
		{
			while (mCanceled == false)
			{
				try {
					FutureTask<?> future = getTask();
					synchronized (mLock) {
						mCurrentTask = future;
					}
					if (future == null)
						break;
					future.run();
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
