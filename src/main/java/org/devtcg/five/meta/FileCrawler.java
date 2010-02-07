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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.Main;
import org.devtcg.five.meta.dao.AlbumDAO;
import org.devtcg.five.meta.dao.ArtistDAO;
import org.devtcg.five.meta.dao.PlaylistDAO;
import org.devtcg.five.meta.dao.SongDAO;
import org.devtcg.five.meta.dao.PlaylistDAO.Playlist;
import org.devtcg.five.meta.dao.PlaylistDAO.PlaylistEntryDAO;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.util.AbstractTimer;
import org.devtcg.five.util.CancelableExecutor;
import org.devtcg.five.util.CancelableThread;
import org.devtcg.five.util.FileUtils;
import org.devtcg.five.util.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

public class FileCrawler
{
	private static final Log LOG = LogFactory.getLog(FileCrawler.class);

	private static FileCrawler INSTANCE;

	private CrawlerThread mThread;
	private List<String> mPaths;

	private Listener mListener;

	private final AbstractTimer mRescanTimer =
		AbstractTimer.newInstance("RescanTimer", Main.mDisplay);

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

	public void updateRescanInterval() throws SQLException
	{
		long rescanInterval = Configuration.getInstance().getRescanInterval();

		if (LOG.isInfoEnabled())
			LOG.info("Updating rescan interval to " + rescanInterval + " msec");

		mRescanTimer.schedule(mRescanRunnable, rescanInterval);
	}

	private final Runnable mRescanRunnable = new Runnable()
	{
		public void run()
		{
			synchronized(FileCrawler.this) {
				if (!isActive())
				{
					if (LOG.isInfoEnabled())
						LOG.info("Auto-rescan starting...");

					startScan();
				}
				else
				{
					if (LOG.isInfoEnabled())
						LOG.info("Scan already in progress, skipping auto-rescan.");
				}
			}

			try {
				updateRescanInterval();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	};

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
		/**
		 * Maximum number of Last.fm request tasks that can be queued before
		 * offers become blocking. This keeps memory usage reasonable for large
		 * collections and also helps to throttle the disk scanner to not hammer
		 * quite so hard.
		 */
		private static final int MAX_NETWORK_TASK_QUEUE_SIZE = 10;

		/**
		 * Used to execute network tasks which will collect extra meta data
		 * than is available on the filesystem. Currently uses both MusicBrainz
		 * and Last.fm.
		 */
		private final CancelableExecutor mNetworkMetaExecutor =
			new CancelableExecutor(new LinkedBlockingQueue<FutureTask<?>>(MAX_NETWORK_TASK_QUEUE_SIZE));

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

			if (ext.equalsIgnoreCase("mp4") == true)
				return true;

			if (ext.equalsIgnoreCase("m4a") == true)
				return true;

			if (ext.equalsIgnoreCase("m4p") == true)
				return true;

//			if (ext.equalsIgnoreCase("ogg") == true)
//				return true;
//
//			if (ext.equalsIgnoreCase("wma") == true)
//				return true;
//
//			if (ext.equalsIgnoreCase("flac") == true)
//				return true;

			return false;
		}

		private long processPlaylistSong(PlaylistEntryDAO existing, Playlist playlistBuf,
			File entry) throws SQLException
		{
			if (entry.exists() == false || isSong(entry, FileUtils.getExtension(entry)) == false)
				return -1;

			/*
			 * Make sure we have this song in our database to send to clients.
			 * Duplicates will be prevented by the normal filesystem logic.
			 */
			long songId = handleFileSong(entry);
			if (songId == -1)
				return -1;

			/*
			 * Try to detect if an existing playlist has any songs deleted or
			 * changed positions. This will trigger an updated sync time which
			 * will then communicate the entire new playlist to the client on
			 * sync.
			 */
			if (existing != null && playlistBuf.hasChanges == false)
			{
				long songAtPos = mProvider.getPlaylistSongDAO().getSongAtPosition(existing.getId(),
					playlistBuf.songs.size());
				if (songAtPos != songId)
					playlistBuf.hasChanges = true;
			}

			playlistBuf.songs.add(songId);

			return songId;
		}

		private long handlePlaylistPls(File file) throws SQLException
		{
			try {
				BufferedReader reader = new BufferedReader(new FileReader(file), 1024);

				/* Sanity check. */
				String firstLine = reader.readLine();
				if (firstLine == null || firstLine.trim().equalsIgnoreCase("[playlist]") == false)
					return -1;

				PlaylistDAO.PlaylistEntryDAO existingPlaylist =
					mProvider.getPlaylistDAO().getPlaylist(file.getAbsolutePath());

				/*
				 * Temporary container used to buffer all playlist entries in
				 * order to effectively detect changes before we commit to the
				 * database.
				 */
				PlaylistDAO.Playlist playlistBuf =
					mProvider.getPlaylistDAO().newPlaylist();

				String line;
				while ((line = reader.readLine()) != null)
				{
					line = line.trim();

					if (line.startsWith("File"))
					{
						String[] keyPair = line.split("=", 2);
						if (keyPair.length < 2)
							continue;

						File entry;
						try {
							URL entryUrl = new URL(URLDecoder.decode(keyPair[1], "UTF-8"));
							entry = new File(entryUrl.getFile());
						} catch (MalformedURLException e) {
							entry = new File(keyPair[1]);
							if (!entry.isAbsolute()) {
								entry = new File(file.getAbsoluteFile().getParent(), keyPair[1]);
							}
						}
						System.out.println("entry=" + entry);
						processPlaylistSong(existingPlaylist, playlistBuf, entry);
					}
				}

				/*
				 * Couldn't parse any of the playlist entries, or it contained
				 * none.
				 */
				if (playlistBuf.songs.isEmpty())
				{
					if (existingPlaylist == null)
						return -1;
					else
						throw new UnsupportedOperationException("Need to delete, but not implemented.");
				}

				long id;
				if (existingPlaylist == null)
				{
					id = mProvider.getPlaylistDAO().insert(file.getAbsolutePath(),
						FileUtils.removeExtension(file.getName()), System.currentTimeMillis());
				}
				else
				{
					id = existingPlaylist.getId();
					mProvider.getPlaylistDAO().unmark(id);
				}

				if (existingPlaylist == null || playlistBuf.hasChanges)
				{
					if (existingPlaylist != null)
						mProvider.getPlaylistSongDAO().deleteByPlaylist(id);

					int pos = 0;
					for (long songId: playlistBuf.songs)
						mProvider.getPlaylistSongDAO().insert(id, pos++, songId);
				}

				return id;
			} catch (IOException e) {
				return -1;
			}
		}

		private long handlePlaylistM3u(File file) throws SQLException
		{
			return -1;
		}

		private long handleFilePlaylist(File file) throws SQLException
		{
			String ext = FileUtils.getExtension(file);

			long id = -1;

			if (ext != null)
			{
				if (ext.equalsIgnoreCase("m3u") == true)
					id = handlePlaylistM3u(file);
				else if (ext.equalsIgnoreCase("pls") == true)
					id = handlePlaylistPls(file);
			}

			if (id == -1)
			{
				if (LOG.isWarnEnabled())
					LOG.warn("Can't handle playlist file " + file.getAbsolutePath());
			}

			return id;
		}

		private String stringTagValue(String value, String prefixHack, String defaultValue)
		{
			if (StringUtils.isEmpty(value)) {
				return defaultValue;
			}

			/*
			 * For some reason entagged prepends a prefix like "ARTIST : " on
			 * id3v1 records. Seems like a bug, but I don't have the energy to
			 * fix it upstream at the moment.
			 */
			if (prefixHack != null && value.startsWith(prefixHack))
				return value.substring(prefixHack.length());
			else
				return value;
		}

		private int intTagValue(String value, int defaultValue)
		{
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
			{
				long id = mProvider.getArtistDAO().insert(artist);
				mNetworkMetaExecutor.execute(new LastfmArtistPhotoTask(mProvider,
					id, artist).getTask());
				return id;
			}

			try {
				return artistEntry.getId();
			} finally {
				artistEntry.close();
			}
		}

		private long getAlbumId(long artistId, String album) throws SQLException
		{
			String nameMatch = StringUtils.getNameMatch(album);

			AlbumDAO.AlbumEntryDAO albumEntry =
				mProvider.getAlbumDAO().getAlbum(artistId, nameMatch);

			if (albumEntry == null)
			{
				ArtistDAO.ArtistEntryDAO artistEntry =
					mProvider.getArtistDAO().getArtist(artistId);
				long id = mProvider.getAlbumDAO().insert(artistId, album);
				mNetworkMetaExecutor.execute(new LastfmAlbumArtworkTask(mProvider,
					id, artistEntry.getName(), album).getTask());
				return id;
			}

			try {
				return albumEntry.getId();
			} finally {
				albumEntry.close();
			}
		}

		private long handleFileSong(File file) throws SQLException
		{
			SongDAO.SongEntryDAO existingEntry =
				mProvider.getSongDAO().getSong(file.getAbsolutePath());

			/* Typical case; no data needs to be updated. */
			try {
				if (existingEntry != null &&
					existingEntry.getMtime() == file.lastModified())
				{
					mProvider.getSongDAO().unmark(existingEntry.getId());
					return existingEntry.getId();
				}
				else
				{
					/*
					 * This file is either new or has been updated since the last time
					 * we scanned.  Re-parse.
					 */
					return handleFileNewOrUpdatedSong(file, existingEntry);
				}
			} finally {
				if (existingEntry != null)
					existingEntry.close();
			}
		}

		private long handleFileNewOrUpdatedSong(File file, SongDAO.SongEntryDAO existingEntry)
			throws SQLException
		{
			try {
				AudioFile audioFile = AudioFileIO.read(file);
				Tag tag = audioFile.getTag();
				AudioHeader header = audioFile.getAudioHeader();

				String artist = stringTagValue(tag.getFirst(FieldKey.ARTIST), "ARTIST : ", "<Unknown>");
				String album = stringTagValue(tag.getFirst(FieldKey.ALBUM), "ALBUM : ", "<Unknown>");
				String title = stringTagValue(tag.getFirst(FieldKey.TITLE), "TITLE : ", null);
				long bitrate = header.getBitRateAsNumber();
				int length = header.getTrackLength();

				/*
				 * Hmm, this library appears to have very unusual bugs when
				 * reading TRACK tags. Any exception the library throws here is
				 * ignored.
				 */
				int track;
				try {
					track = intTagValue(tag.getFirst(FieldKey.TRACK), -1);
				} catch (Exception e) {
					track = -1;
				}

				/* Title is actually the only property we strictly require. */
				if (title == null)
					throw new CannotReadException("No title property set");

				long artistId = getArtistId(artist);
				long albumId = getAlbumId(artistId, album);

				SongDAO.Song song = mProvider.getSongDAO().newSong(file,
					artistId, albumId, title, bitrate, length, track);

				if (existingEntry != null)
					return mProvider.getSongDAO().update(existingEntry.getId(), song);
				else
					return mProvider.getSongDAO().insert(song);
			} catch (Exception e) {
				if (LOG.isWarnEnabled())
					LOG.warn(file + ": unable to parse song: " + e, e);

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
			if (files == null)
				return;

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

			/* Will work whether we were canceled or not. */
			mNetworkMetaExecutor.shutdownAndWait();
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

		@Override
		protected void onRequestCancel()
		{
			interrupt();
			mNetworkMetaExecutor.requestCancel();
		}
	}
}
