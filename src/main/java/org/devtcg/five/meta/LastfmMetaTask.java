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

package org.devtcg.five.meta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.devtcg.five.util.IOUtils;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public abstract class LastfmMetaTask implements Callable<Object>
{
	protected static final Log LOG = LogFactory.getLog(LastfmMetaTask.class);

	private static final String API_KEY = "3edc5156fb5dc2d185c1f00ba0282ea5";
	protected static final String LASTFM_CALL_URL = "http://ws.audioscrobbler.com/2.0/?api_key=" + API_KEY;

	protected static final HttpClient mClient = new DefaultHttpClient();

	protected static final int THUMB_WIDTH = 64;
	protected static final int THUMB_HEIGHT = 64;

	protected final MetaProvider mProvider;
	private final FutureTask<?> mTask;
	protected final long mId;
	protected DefaultHandler mXmlHandler;

	public LastfmMetaTask(MetaProvider provider, long id)
	{
		mProvider = provider;
		mTask = new FutureTask<Object>(this);
		mId = id;
	}

	public FutureTask<?> getTask()
	{
		return mTask;
	}

	public Object call() throws Exception
	{
		try {
			run();
		} catch (Exception e) {
			if (LOG.isWarnEnabled())
				LOG.warn("Error accessing Last.fm", e);
		}
		return null;
	}

	protected abstract String getMethodUrl();

	protected abstract DefaultHandler getContentHandler();

	protected abstract void onPostParse();

	public void run() throws Exception
	{
		System.out.println("Accessing " + getMethodUrl());
		HttpGet request = new HttpGet(getMethodUrl());

		HttpResponse response = mClient.execute(request);

		if (mTask.isCancelled())
			return;

		if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
		{
			response.getEntity().consumeContent();
			return;
		}

		HttpEntity entity = response.getEntity();
		InputStream in = entity.getContent();

		try {
			XMLReader reader = XMLReaderFactory.createXMLReader();
			reader.setContentHandler(getContentHandler());
			reader.parse(new InputSource(in));
		} finally {
			IOUtils.closeQuietlyNullSafe(in);
		}

		if (mTask.isCancelled())
			return;

		onPostParse();
	}

	protected byte[] downloadImage(String url) throws IOException
	{
		HttpGet request = new HttpGet(url);

		HttpResponse response = mClient.execute(request);

		if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
		{
			response.getEntity().consumeContent();
			return null;
		}

		HttpEntity entity = response.getEntity();

		ByteArrayOutputStream out =
			new ByteArrayOutputStream((int)entity.getContentLength());
		InputStream in = entity.getContent();

		try {
			IOUtils.copyStream(in, out);
			return out.toByteArray();
		} finally {
			IOUtils.closeQuietlyNullSafe(in);
		}
	}
}
