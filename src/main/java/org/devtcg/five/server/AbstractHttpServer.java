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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.devtcg.five.util.CancelableThread;

public abstract class AbstractHttpServer extends CancelableThread
{
	/* package */ static final Log LOG = LogFactory.getLog(AbstractHttpServer.class);

	protected final HashSet<WorkerThread> mWorkers =
		new HashSet<WorkerThread>();

	private ServerSocket mSocket;

	protected final HttpParams mParams;
	private HttpRequestHandler mReqHandler;

	public AbstractHttpServer() throws IOException
	{
		mSocket = new ServerSocket();

		mParams = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(mParams, 60000);

		setDaemon(true);
		setPriority(MIN_PRIORITY);
	}

	public AbstractHttpServer(int port) throws IOException
	{
		this();
		bind(port);
	}

	private synchronized ServerSocket getSocket()
	{
		return mSocket;
	}

	public void rebind(int port) throws IOException
	{
		synchronized(this) {
			mSocket.close();
			mSocket = new ServerSocket();
		}
		bind(port);
	}

	public void bind(int port) throws IOException
	{
		try {
			mSocket.bind(new InetSocketAddress(port));
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	public void setRequestHandler(HttpRequestHandler handler)
	{
		mReqHandler = handler;
	}

	private void reset()
	{
		WorkerThread[] workersCopy;

		synchronized(mWorkers) {
			/* Copied because shutdown() will try to access mWorkers. */
			workersCopy =
				mWorkers.toArray(new WorkerThread[mWorkers.size()]);
		}

		for (WorkerThread t: workersCopy)
			t.requestCancelAndWait();
	}

	@Override
	protected void onRequestCancel()
	{
		reset();
		interrupt();

		try {
			getSocket().close();
		} catch (IOException e) {
			if (LOG.isErrorEnabled())
				LOG.error("Error shutting down HTTP server", e);
		}
	}

	public void shutdown()
	{
		requestCancel();
	}

	public void run()
	{
		if (mReqHandler == null)
			throw new IllegalStateException("Request handler not set.");

		while (Thread.interrupted() == false)
		{
			try {
				Socket sock = getSocket().accept();
				DefaultHttpServerConnection conn =
					new DefaultHttpServerConnection();

				conn.bind(sock, mParams);

				BasicHttpProcessor proc = new BasicHttpProcessor();
				proc.addInterceptor(new ResponseContent());
				proc.addInterceptor(new ResponseConnControl());

				HttpRequestHandlerRegistry reg =
					new HttpRequestHandlerRegistry();
				reg.register("*", mReqHandler);

				HttpService svc = new HttpService(proc,
					new DefaultConnectionReuseStrategy(),
					new DefaultHttpResponseFactory());

				svc.setParams(mParams);
				svc.setHandlerResolver(reg);

				WorkerThread t;

				synchronized(mWorkers) {
					t = new WorkerThread(svc, conn);
					mWorkers.add(t);
				}

				t.start();
			} catch (IOException e) {
				if (!hasCanceled())
				{
					if (LOG.isErrorEnabled())
						LOG.error("I/O error initializing connection thread", e);
				}
				break;
			}
		}
	}

	private class WorkerThread extends CancelableThread
	{
		private HttpService mService;
		private HttpServerConnection mConn;

		public WorkerThread(HttpService svc, HttpServerConnection conn)
		{
			super();

			setName("WorkerThread");
			setDaemon(true);
			setPriority(MIN_PRIORITY);

			mService = svc;
			mConn = conn;
		}

		public void run()
		{
			HttpContext ctx = new BasicHttpContext(null);

			try {
				while (isInterrupted() == false && mConn.isOpen())
					mService.handleRequest(mConn, ctx);
			} catch (Exception e) {
				if (LOG.isDebugEnabled())
					LOG.debug("HTTP client worker disrupted", e);
			} finally {
				if (hasCanceled() == false)
				{
					try {
						mConn.shutdown();
					} catch (IOException e) {}
				}

				synchronized(mWorkers) {
					mWorkers.remove(this);
				}
			}
		}

		public void onRequestCancel()
		{
			interrupt();

			try {
				mConn.shutdown();
			} catch (IOException e) {}
		}
	}
}
