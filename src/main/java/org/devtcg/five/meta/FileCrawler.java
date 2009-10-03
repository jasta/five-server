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

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.meta.dao.AlbumDAO;
import org.devtcg.five.meta.dao.ArtistDAO;
import org.devtcg.five.meta.dao.SongDAO;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.util.CancelableThread;
import org.devtcg.five.util.FileUtils;
import org.devtcg.five.util.StringUtils;
import org.devtcg.five.util.TimeUtils;

import entagged.audioformats.AudioFile;
import entagged.audioformats.AudioFileIO;
import entagged.audioformats.Tag;
import entagged.audioformats.exceptions.CannotReadException;

public class FileCrawler
{
	private static final Log LOG = LogFactory.getLog(FileCrawler.class);

	private static FileCrawler INSTANCE;

	private CrawlerThread mThread;
	private List<String> mPaths;

	private Listener mListener;

	public interface Listener
	{
		public void onStart();
		public void onProgress(int scannedSoFar);
		public void onFinished(boolean canceled);
	}

	private FileCrawler()
	{
		try {
			setPaths(Configuration.getInstance().getLibraryPaths());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static synchronized FileCrawler getInstance()
	{
		if (INSTANCE == null)
			INSTANCE = new FileCrawler();

		return INSTANCE;
	}

	private void setPaths(List<String> paths)
	{
		mPaths = paths;
	}

	public void setListener(Listener l)
	{
		mListener = l;
	}

	public synchronized void startScan()
	{
		if (mThread == null)
		{
			mThread = new CrawlerThread(MetaProvider.getInstance());
			mThread.start();
		}
	}

	public synchronized void stopAbruptly()
	{
		if (mThread != null)
		{
			mThread.requestCancelAndWait();

			/* Should have been set to null by the thread itself. */
			assert mThread == null;
		}
	}

	public synchronized boolean isActive()
	{
		return mThread != null;
	}

	private class CrawlerThread extends CancelableThread
	{
		private final MetaProvider mProvider;

		private int mFilesScanned = 0;

		public CrawlerThread(MetaProvider provider)
		{
			setName("FileCrawler");
			setPriority(Thread.MIN_PRIORITY);

			mProvider = provider;
		}

		private boolean isPlaylist(File file, String ext)
		{
			if (ext == null)
				return false;

			if (ext.equalsIgnoreCase("pls") == true)
				return true;

			if (ext.equalsIgnoreCase("m3u") == true)
				return true;

			return false;
		}

		private boolean isSong(File file, String ext)
		{
			/* TODO: Inspect the file, do not rely on file extension. */
			if (ext == null)
				return false;

			if (ext.equalsIgnoreCase("mp3") == true)
				return true;

//			if (ext.equalsIgnoreCase("ogg") == true)
//				return true;
//
//			if (ext.equalsIgnoreCase("wma") == true)
//				return true;
//
//			if (ext.equalsIgnoreCase("flac") == true)
//				return true;
//
//			if (ext.equalsIgnoreCase("m4a") == true)
//				return true;

			return false;
		}

		private long handleFilePlaylist(File file) throws SQLException
		{
			if (LOG.isWarnEnabled())
				LOG.warn(file + ": handleFilePlaylist not implemented!");

			return -1;
		}

		private String stringTagValue(List<?> tagValue, String defaultValue)
		{
			if (tagValue == null || tagValue.size() == 0)
				return defaultValue;

			String value = tagValue.get(0).toString();
			if (StringUtils.isEmpty(value)) {
				return defaultValue;
			}

			return value;
		}

		private int intTagValue(List<?> tagValue, int defaultValue)
		{
			String value = stringTagValue(tagValue, null);
			if (value == null)
				return defaultValue;

			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}

		private long getArtistId(String artist) throws SQLException
		{
			String nameMatch = StringUtils.getNameMatch(artist);

			ArtistDAO.ArtistEntryDAO artistEntry =
				mProvider.getArtistDAO().getArtist(nameMatch);

			if (artistEntry == null)
				return mProvider.getArtistDAO().insert(artist);

			try {
				return artistEntry.getId();
			} finally {
				artistEntry.close();
			}
		}

		private long getAlbumId(String album) throws SQLException
		{
			String nameMatch = StringUtils.getNameMatch(album);

			AlbumDAO.Album albumEntry =
				mProvider.getAlbumDAO().getAlbum(nameMatch);

			if (albumEntry == null)
				return mProvider.getAlbumDAO().insert(album);

			return albumEntry._id;
		}

		private long handleFileSong(File file) throws SQLException
		{
			SongDAO.Song existingEntry =
				mProvider.getSongDAO().getSong(file.getAbsolutePath());

			/* Typical case; no data needs to be updated. */
			if (existingEntry != null &&
				existingEntry.mtime == TimeUtils.asUnixTimestamp(file.lastModified()))
			{
				existingEntry.unmark();
				return existingEntry._id;
			}

			/*
			 * This file is either new or has been updated since the last time
			 * we scanned.
			 */

			try {
				AudioFile audioFile = AudioFileIO.read(file);
				Tag tag = audioFile.getTag();

				String artist = stringTagValue(tag.getArtist(), "<Unknown>");
				String album = stringTagValue(tag.getAlbum(), "<Unknown>");
				String title = stringTagValue(tag.getTitle(), null);
				int bitrate = audioFile.getBitrate();
				int length = audioFile.getLength();
				int track = intTagValue(tag.getTrack(), -1);

				/* Title is actually the only property we strictly require. */
				if (title == null)
					throw new CannotReadException("No title property set");

				long artistId = getArtistId(artist);
				long albumId = getAlbumId(album);

				SongDAO.Song song = mProvider.getSongDAO().newSong(file,
					artistId, albumId, title, bitrate, length, track);

				if (existingEntry != null)
					return mProvider.getSongDAO().update(existingEntry._id, song);
				else
					return mProvider.getSongDAO().insert(song);
			} catch (CannotReadException e) {
				if (LOG.isWarnEnabled())
					LOG.warn(file + ": unable to parse song: " + e);

				return -1;
			}
		}

		private boolean handleFile(File file) throws SQLException
		{
			String ext = FileUtils.getExtension(file);

			if (isPlaylist(file, ext) == true)
				return handleFilePlaylist(file) != -1;
			else if (isSong(file, ext) == true)
				return handleFileSong(file) != -1;

			return false;
		}

		/**
		 * Recursively scan a given path, inserting any entries into the
		 * temporary provider.
		 */
		private void traverse(File path) throws SQLException
		{
			File[] files = path.listFiles();
			for (File file : files)
			{
				if (hasCanceled() == true)
					return;

				if (file.isDirectory() == true)
					traverse(file);
				else if (file.isFile() == true)
				{
					if (handleFile(file))
					{
						mFilesScanned++;

						if (mListener != null)
							mListener.onProgress(mFilesScanned);
					}
				}
			}
		}

		private void deleteAllMarked() throws SQLException
		{
			/* TODO */
		}

		private void crawlImpl() throws SQLException
		{
			mProvider.getSongDAO().markAll();

			for (String path : mPaths)
				traverse(new File(path));

			/* Delete every entry that hasn't been unmarked during traversal. */
			deleteAllMarked();
		}

		public void run()
		{
			if (mListener != null)
				mListener.onStart();

			try {
				crawlImpl();
			} catch (SQLException e) {
				/* TODO */
				e.printStackTrace();
			} finally {
				synchronized (FileCrawler.this) {
					mThread = null;
				}

				if (mListener != null)
					mListener.onFinished(hasCanceled());
			}
		}
	}
}
