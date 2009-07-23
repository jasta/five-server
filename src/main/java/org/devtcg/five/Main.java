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

package org.devtcg.five;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.meta.FileCrawler;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.server.HttpServer;

public class Main {
	private static final Log LOG = LogFactory.getLog(Main.class);

	private static HttpServer mServer;
	private static FileCrawler mCrawler;

	private static final Object mQuitLock = new Object();
	private static boolean mQuit = false;

	public static void main(String[] args)
	{
		try {
			start();

			synchronized (mQuitLock) {
				while (mQuit == false) {
					try {
						mQuitLock.wait();
					} catch (InterruptedException e) {}
				}
			}
		} finally {
			if (mServer != null)
				mServer.shutdown();

			if (mCrawler != null && mCrawler.isActive() == true)
				mCrawler.stopAbruptly();
		}
	}

	private static void start()
	{
		Configuration config = Configuration.getInstance();

		try {
			if (config.isFirstTime() == true) {
				config.setDebugConfiguration();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		int port = config.getServerPort();

		mServer = new HttpServer(port);
		mServer.start();

		mCrawler = FileCrawler.getInstance();
		mCrawler.setListener(mCrawlerListener);
		mCrawler.startScan();
	}

	public static void quit()
	{
		synchronized (mQuitLock) {
			mQuit = true;
			mQuitLock.notify();
		}
	}

	private static final FileCrawler.Listener mCrawlerListener = new FileCrawler.Listener()
	{
		public void onFinished(boolean canceled)
		{
			if (LOG.isInfoEnabled())
				LOG.info("onFinished: canceled=" + canceled);
		}

		public void onProgress(int scannedSoFar)
		{
			if (LOG.isInfoEnabled())
				LOG.info("onProgress: " + scannedSoFar);
		}

		public void onStart()
		{
			if (LOG.isInfoEnabled())
				LOG.info("onStart");
		}
	};
}
