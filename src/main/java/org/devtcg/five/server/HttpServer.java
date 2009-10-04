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
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.content.AbstractTableMerger.SyncableColumns;
import org.devtcg.five.meta.MetaProvider;
import org.devtcg.five.meta.MetaSyncAdapter;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.SyncableProvider;

public class HttpServer extends AbstractHttpServer
{
	public HttpServer(int port) throws IOException {
		super(port);
		setRequestHandler(mHttpHandler);
	}

	private final HttpRequestHandler mHttpHandler = new HttpRequestHandler()
	{
		private static final String LAST_MODIFIED_HEADER = "Last-Modified";
		private static final String MODIFIED_SINCE_HEADER = "X-Modified-Since";
		private static final String ENTITY_COUNT_HEADER = "X-Entity-Count";

		private boolean handleFeed(HttpRequest request, HttpResponse response,
			HttpContext context) throws SQLException
		{
			String[] segments = request.getRequestLine().getUri().split("/");
			if (segments.length < 3)
				return false;

			String feedType = segments[2];

			MetaSyncAdapter adapter = (MetaSyncAdapter)MetaProvider.getInstance().getSyncAdapter();
			AbstractTableMerger merger = null;

			merger = adapter.getMerger(feedType);

			if (merger != null)
			{
				Header modifiedSinceHeader = request.getLastHeader(MODIFIED_SINCE_HEADER);
				long modifiedSince = 0;
				if (modifiedSinceHeader != null)
				{
					try {
						modifiedSince = Long.parseLong(modifiedSinceHeader.getValue());
					} catch (NumberFormatException e) {
						if (LOG.isWarnEnabled())
						{
							LOG.warn("Can't parse " + MODIFIED_SINCE_HEADER + " value: " +
								modifiedSinceHeader.getValue());
						}
						return false;
					}
				}

				SyncableProvider clientDiffs = MetaProvider.getTemporaryInstance();
				merger.findLocalChanges(clientDiffs, modifiedSince);

				int entityCount = DatabaseUtils.integerForQuery(clientDiffs.getConnection(),
					"SELECT COUNT(*) FROM " + merger.getTableName(), (String[])null);

				long lastModified = DatabaseUtils.longForQuery(clientDiffs.getConnection(),
					"SELECT MAX(" + SyncableColumns._SYNC_TIME + ") FROM " + merger.getTableName(),
					(String[])null);

				SyncableEntryDAO entryDAO = merger.getEntryDAO(clientDiffs);

				response.setHeader(ENTITY_COUNT_HEADER, String.valueOf(entityCount));
				response.setHeader(LAST_MODIFIED_HEADER, String.valueOf(lastModified));
				response.setEntity(new EntryDAOEntity(clientDiffs, entryDAO));
				response.setStatusCode(HttpStatus.SC_OK);

				return true;
			}

			return false;
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException
		{
			RequestLine requestLine = request.getRequestLine();
			System.out.println("requestLine=" + requestLine);

			String method = requestLine.getMethod();
			if (!method.equalsIgnoreCase("GET"))
				throw new MethodNotSupportedException(method + " method not supported");

			String requestUriString = requestLine.getUri();

			boolean handled = false;
			try {
				if (requestUriString.startsWith("/feeds/"))
					handled = handleFeed(request, response, context);
			} catch (Exception e) {
				if (LOG.isWarnEnabled())
					LOG.warn("Failed to process client sync request", e);
			}

			if (handled == false)
				response.setStatusCode(HttpStatus.SC_NOT_FOUND);
		}
	};

	private static class EntryDAOEntity extends AbstractHttpEntity
	{
		private final SyncableProvider mProvider;
		private final SyncableEntryDAO mDAO;

		public EntryDAOEntity(SyncableProvider provider, SyncableEntryDAO dao)
		{
			super();
			mProvider = provider;
			mDAO = dao;
			setContentType(dao.getContentType());
		}

		public InputStream getContent() throws IOException, IllegalStateException
		{
			return null;
		}

		public long getContentLength()
		{
			return -1;
		}

		public boolean isRepeatable()
		{
			return false;
		}

		public boolean isStreaming()
		{
			return true;
		}

		public boolean isChunked()
		{
			return true;
		}

		public void writeTo(OutputStream out) throws IOException
		{
			try {
				while (mDAO.moveToNext())
					mDAO.writeRecordTo(out);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					mDAO.close();
					mProvider.close();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
