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
import org.devtcg.five.server.UPnPService;
import org.devtcg.five.ui.Docklet;
import org.devtcg.five.ui.Setup;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Display;

public class Main {
	private static final Log LOG = LogFactory.getLog(Main.class);

	public static Display mDisplay;

	/* Uhm, not at all sure what I was thinking with these fields... */
	public static HttpServer mServer;
	public static FileCrawler mCrawler;
	public static Docklet mDocklet;

	public static void main(String[] args) throws SQLException
	{
		Configuration config = Configuration.getInstance();

		try {
			mDisplay = new Display();
			Display.setAppName("five");
		} catch (SWTError e) {
			if (LOG.isDebugEnabled())
				LOG.debug("Fatal display exception", e);

			if (LOG.isWarnEnabled())
				LOG.warn("Failed to initialize display, falling back to text-based UI.");
		}

		try {
			if (config.isFirstTime())
				Setup.show(mDisplay);
			else
			{
				Docklet docklet = mDocklet = new Docklet(mDisplay);
				startServices();
				docklet.open();
			}
		} finally {
			/* Stop any services if started (http server, file crawler, etc...) */
			stopServices();
		}
	}

	public static void startServices()
	{
		Configuration config = Configuration.getInstance();

		try {
			mServer = new HttpServer(config.getServerPort());
			mServer.start();

			if (config.useUPnP())
				UPnPService.getInstance().enableUPnP();

			mCrawler = FileCrawler.getInstance();
			mCrawler.setListener(mCrawlerListener);
			mCrawler.startScan();
			mCrawler.updateRescanInterval();
		} catch (Exception e) {
			/* TODO */
			throw new RuntimeException(e);
		}
	}

	private static void stopServices()
	{
		if (mServer != null)
			mServer.shutdown();

		if (mCrawler != null && mCrawler.isActive() == true)
			mCrawler.stopAbruptly();
	}

	private static final FileCrawler.Listener mCrawlerListener = new FileCrawler.Listener()
	{
		private static final int TOOLTIP_UPDATE_INTERVAL = 1000;
		private long mLastUpdateTime;

		public void onFinished(boolean canceled)
		{
			if (LOG.isInfoEnabled())
				LOG.info("File crawler finished, canceled=" + canceled);

			mDocklet.setToolTipText("Five server is ready.");
		}

		public void onProgress(int scannedSoFar)
		{
			update(scannedSoFar);
		}

		public void onStart()
		{
			if (LOG.isInfoEnabled())
				LOG.info("File crawler starting...");

			update(0);
		}

		private void update(int scannedSoFar)
		{
			if (System.currentTimeMillis() - mLastUpdateTime >= TOOLTIP_UPDATE_INTERVAL)
			{
				mDocklet.setToolTipText("Scanning music collection (" +
						scannedSoFar + " scanned so far)...");
				mLastUpdateTime = System.currentTimeMillis();
			}
		}
	};
}
