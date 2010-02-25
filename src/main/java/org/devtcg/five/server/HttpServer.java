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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.devtcg.five.Build;
import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.content.AbstractTableMerger.SyncableColumns;
import org.devtcg.five.meta.MetaProvider;
import org.devtcg.five.meta.MetaSyncAdapter;
import org.devtcg.five.meta.dao.ImageDAO;
import org.devtcg.five.meta.dao.SongDAO;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.SyncableProvider;

import sun.misc.BASE64Decoder;

import com.google.protobuf.CodedOutputStream;

public class HttpServer extends AbstractHttpServer
{
	private static final String STANDARD_USER = "fiveuser";

	public HttpServer(int port) throws IOException {
		super(port);
		setRequestHandler(mHttpHandler);
	}

	private static final HttpRequestHandler mHttpHandler = new HttpRequestHandler()
	{
		private static final String RANGE_HEADER = "Range";
		private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";
		private static final String AUTHORIZATION_HEADER = "Authorization";
		private static final String CONTENT_RANGE_HEADER = "Content-Range";
		private static final String LAST_MODIFIED_HEADER = "X-Last-Modified";
		private static final String MODIFIED_SINCE_HEADER = "X-Modified-Since";
		private static final String INSERT_OR_UPDATE_COUNT_HEADER = "X-Records-Modified";
		private static final String DELETE_COUNT_HEADER = "X-Records-Deleted";
		private static final String FIVE_VERSION_HEADER = "X-Five-Version";

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

				int insertOrUpdateCount = DatabaseUtils.integerForQuery(
						clientDiffs.getConnection().getWrappedConnection(), 0,
						"SELECT COUNT(*) FROM " + merger.getTableName(), (String[])null);

				int deleteCount = DatabaseUtils.integerForQuery(
						clientDiffs.getConnection().getWrappedConnection(), 0,
						"SELECT COUNT(*) FROM " + merger.getDeletesTableName(), (String[])null);

				long lastModified = DatabaseUtils.longForQuery(
						clientDiffs.getConnection().getWrappedConnection(), 0,
						"SELECT MAX(" + SyncableColumns._SYNC_TIME + ") FROM " +
								merger.getTableName(), (String[])null);

				SyncableEntryDAO entryDAO = merger.getEntryDAO(clientDiffs);
				SyncableEntryDAO deletedEntryDAO = merger.getDeletedEntryDAO(clientDiffs);

				response.setHeader(INSERT_OR_UPDATE_COUNT_HEADER, String.valueOf(insertOrUpdateCount));
				response.setHeader(DELETE_COUNT_HEADER, String.valueOf(deleteCount));
				response.setHeader(LAST_MODIFIED_HEADER, String.valueOf(lastModified));
				response.setEntity(new EntryDAOEntity(clientDiffs, entryDAO, insertOrUpdateCount,
						deletedEntryDAO, deleteCount));
				response.setStatusCode(HttpStatus.SC_OK);

				return true;
			}

			return false;
		}

		private RangeHeader parseRangeRequest(HttpRequest request)
		{
			Header hdr = request.getLastHeader(RANGE_HEADER);

			if (hdr == null)
				return null;

			String rangeStr = hdr.getValue();
			Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d+)?");
			Matcher matcher = pattern.matcher(rangeStr);

			try {
				if (matcher.matches() == false)
					throw new IllegalArgumentException();

				long first;
				long last;

				first = Long.parseLong(matcher.group(1));

				String lastString = matcher.group(2);
				if (lastString != null)
					last = Long.parseLong(lastString);
				else
					last = -1;

				if (first < 0)
					throw new IllegalArgumentException();

				RangeHeader header = new RangeHeader();
				header.firstBytePos = first;
				header.lastBytePos = last;

				return header;
			} catch (Exception e) {
				if (LOG.isWarnEnabled())
					LOG.warn("Failed to parse range header: " + rangeStr);
				return null;
			}
		}

		private boolean handleSong(HttpRequest request, HttpResponse response,
			HttpContext context) throws SQLException
		{
			String uri = request.getRequestLine().getUri();
			String[] segments = uri.split("/");
			if (segments.length < 3)
				return false;

			long songId;
			try {
				songId = Long.parseLong(segments[2]);
			} catch (NumberFormatException e) {
				return false;
			}

			SongDAO.SongEntryDAO song = MetaProvider.getInstance().getSongDAO().getSong(songId);

			if (song == null)
			{
				if (LOG.isWarnEnabled())
					LOG.warn("No song matching request: " + uri);

				return false;
			}

			File file = new File(song.getFilename());
			if (file.length() == 0)
			{
				if (LOG.isErrorEnabled())
					LOG.error("Can't serve file " + song.getFilename() + ", 0 length content");

				return false;
			}

			response.setHeader(LAST_MODIFIED_HEADER, String.valueOf(song.getMtime()));

			RangeHeader rangeHeader = parseRangeRequest(request);
			if (rangeHeader != null)
			{
				long length = file.length();

				response.setHeader(CONTENT_RANGE_HEADER,
					"bytes " + rangeHeader.firstBytePos + "-" + (length - 1) + "/" + length);
				response.setEntity(new RangeFileEntity(new File(song.getFilename()),
					song.getMimeType(), rangeHeader));
				response.setStatusCode(HttpStatus.SC_PARTIAL_CONTENT);
			}
			else
			{
				response.setEntity(new FileEntity(new File(song.getFilename()), song.getMimeType()));
				response.setStatusCode(HttpStatus.SC_OK);
			}

			return true;
		}

		private boolean handleImage(HttpRequest request, HttpResponse response,
			HttpContext context) throws SQLException
		{
			String uri = request.getRequestLine().getUri();
			String[] segments = uri.split("/");
			if (segments.length < 5)
				return false;

			String feedType = segments[2];
			String[] dimensions = segments[3].split("x", 2);

			int width;
			int height;
			long id;
			try {
				width = Integer.parseInt(dimensions[0]);
				height = Integer.parseInt(dimensions[1]);
				id = Long.parseLong(segments[4]);
			} catch (NumberFormatException e) {
				return false;
			}

			ImageDAO.ImageEntryDAO entry = MetaProvider.getInstance().getImageDAO()
					.requestImageAtSize(feedType, id, width, height);

			/* Either bogus URI or no photo to transmit, return 404 Not Found. */
			if (entry == null)
			{
				System.out.println("No data available for: " + uri);
				return false;
			}

			try {
				response.setEntity(new ByteArrayEntity(entry.getData()));
				response.setStatusCode(HttpStatus.SC_OK);
			} finally {
				entry.close();
			}

			return true;
		}

		private boolean handleInfo(HttpRequest request, HttpResponse response, HttpContext context)
		{
			response.setHeader(FIVE_VERSION_HEADER, Build.VERSION);
			response.setStatusCode(HttpStatus.SC_OK);
			return true;
		}

		private void handleAuthed(HttpRequest request, HttpResponse response, HttpContext context)
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
				else if (requestUriString.startsWith("/songs/"))
					handled = handleSong(request, response, context);
				else if (requestUriString.startsWith("/image/"))
					handled = handleImage(request, response, context);
				else if (requestUriString.equals("/info"))
					handled = handleInfo(request, response, context);
			} catch (Exception e) {
				if (LOG.isWarnEnabled())
					LOG.warn("Failed to process client sync request", e);
			}

			if (handled == false)
				response.setStatusCode(HttpStatus.SC_NOT_FOUND);
		}

		private boolean acceptAuth(HttpRequest request)
		{
			Header authHead = request.getLastHeader(AUTHORIZATION_HEADER);
			if (authHead == null)
				return false;

			String value = authHead.getValue();

			String requiredScheme = "Basic ";

			if (!value.startsWith(requiredScheme))
				return false;

			String base64userpass = value.substring(requiredScheme.length());

			String userpassString;
			try {
				userpassString = new String(new BASE64Decoder()
						.decodeBuffer(base64userpass), "UTF-8");
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			String[] userpass = userpassString.split(":", 2);
			if (userpass.length < 2)
				throw new RuntimeException("Failed to parse authorize header: " + value);

			if (!STANDARD_USER.equals(userpass[0]))
				return false;

			return true;
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext context)
			throws HttpException, IOException
		{
			if (acceptAuth(request))
				handleAuthed(request, response, context);
			else
			{
				response.setHeader(WWW_AUTHENTICATE_HEADER, "Basic realm=\"five-server\"");
				response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
			}
		}
	};


	private static class RangeHeader
	{
		public long firstBytePos;
		public long lastBytePos;
	}

	private static class RangeFileEntity extends FileEntity
	{
		protected RangeHeader mRangeRequest;

		public RangeFileEntity(File file, String mime, RangeHeader rangeRequest)
		{
			super(file, mime);
			mRangeRequest = rangeRequest;
		}

		@Override
		public long getContentLength()
		{
			if (mRangeRequest.lastBytePos >= 0)
				return mRangeRequest.lastBytePos - mRangeRequest.firstBytePos + 1;
			else
				return file.length() - mRangeRequest.firstBytePos;
		}

		private FileInputStream getPositionedStream() throws IOException
		{
			FileInputStream in = new FileInputStream(file);

			if (mRangeRequest.firstBytePos > 0)
				in.skip(mRangeRequest.firstBytePos);

			return in;
		}

		@Override
		public InputStream getContent() throws IOException
		{
			return getPositionedStream();
		}

		@Override
		public void writeTo(OutputStream out) throws IOException
		{
			long remaining = getContentLength();

			InputStream in = getPositionedStream();
			try {
				byte[] buf = new byte[4096];
				int n;

				int max = (int)Math.min(remaining, (long)buf.length);

				while ((n = in.read(buf, 0, max)) >= 0)
				{
					out.write(buf, 0, n);

					if (remaining == n)
						break;

					remaining -= n;
				}
			} finally {
				in.close();
			}
		}
	}

	private static class EntryDAOEntity extends AbstractHttpEntity
	{
		private final SyncableProvider mProvider;
		private final SyncableEntryDAO mDAO;
		private final int mCount;
		private final SyncableEntryDAO mDeletedDAO;
		private final int mDeletesCount;

		public EntryDAOEntity(SyncableProvider provider, SyncableEntryDAO dao, int entityCount,
				SyncableEntryDAO deletedDao, int deletesCount)
		{
			super();
			mProvider = provider;
			mDAO = dao;
			mCount = entityCount;
			mDeletedDAO = deletedDao;
			mDeletesCount = deletesCount;
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

		private void exhaustEntries(CodedOutputStream stream, SyncableEntryDAO dao, int count,
				boolean sendIdOnly) throws IOException, SQLException
		{
			try {
				stream.writeRawLittleEndian32(count);
				while (dao.moveToNext())
				{
					if (sendIdOnly)
						stream.writeRawLittleEndian64(dao.getId());
					else
					{
						Protos.Record entry = dao.getEntry();
						stream.writeRawLittleEndian32(entry.getSerializedSize());
						entry.writeTo(stream);
					}
				}
			} finally {
				dao.close();
			}
		}

		public void writeTo(OutputStream out) throws IOException
		{
			CodedOutputStream stream = CodedOutputStream.newInstance(out);
			try {
				exhaustEntries(stream, mDeletedDAO, mDeletesCount, true);
				exhaustEntries(stream, mDAO, mCount, false);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					stream.flush();
				} catch (IOException e) {}
				try {
					mProvider.close();
				} catch (SQLException e) {
					if (LOG.isWarnEnabled())
						LOG.warn("Unable to cleanup data objects after client sync", e);
				}
			}
		}
	}
}
