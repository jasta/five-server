package org.devtcg.five.meta;

import java.io.File;
import java.sql.Connection;
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

	public synchronized void startScan()
	{
		if (mThread == null)
		{
			mThread = new CrawlerThread(MetaProvider.getTemporaryInstance());
			mThread.setName("FileCrawler");
			mThread.setPriority(Thread.MIN_PRIORITY);
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
		private MetaProvider mTempProvider;
		private MetaProvider mMainProvider;

		public CrawlerThread(MetaProvider tempProvider)
		{
			mTempProvider = tempProvider;
			mMainProvider = MetaProvider.getInstance();
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

			if (ext.equalsIgnoreCase("ogg") == true)
				return true;

			if (ext.equalsIgnoreCase("wma") == true)
				return true;

			if (ext.equalsIgnoreCase("flac") == true)
				return true;

			if (ext.equalsIgnoreCase("m4a") == true)
				return true;

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

			ArtistDAO.Artist artistEntry =
				mTempProvider.getArtistDAO().getArtist(nameMatch);

			if (artistEntry == null)
			{
				artistEntry = mMainProvider.getArtistDAO().getArtist(nameMatch);
				if (artistEntry == null)
					return mTempProvider.getArtistDAO().insert(artist);
			}

			return artistEntry._id;
		}

		private long getAlbumId(String album) throws SQLException
		{
			String nameMatch = StringUtils.getNameMatch(album);

			AlbumDAO.Album albumEntry =
				mTempProvider.getAlbumDAO().getAlbum(nameMatch);

			if (albumEntry == null)
			{
				albumEntry = mMainProvider.getAlbumDAO().getAlbum(nameMatch);
				if (albumEntry == null)
					return mTempProvider.getAlbumDAO().insert(album);
			}

			return albumEntry._id;
		}

		private long handleFileSong(File file) throws SQLException
		{
			SongDAO.Song existingEntry =
				mMainProvider.getSongDAO().getSong(file.getAbsolutePath());

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

				SongDAO.Song song = mTempProvider.getSongDAO().newSong(file,
					artistId, albumId, title, bitrate, length, track);

				if (existingEntry != null)
					return mTempProvider.getSongDAO().update(existingEntry._id, song);
				else
					return mTempProvider.getSongDAO().insert(song);
			} catch (CannotReadException e) {
				if (LOG.isErrorEnabled())
					LOG.error(file + ": unable to parse song: " + e);
			}

			return -1;
		}

		private void handleFile(File file) throws SQLException
		{
			String ext = FileUtils.getExtension(file);

			if (isPlaylist(file, ext) == true)
				handleFilePlaylist(file);
			else if (isSong(file, ext) == true)
				handleFileSong(file);
		}

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
					handleFile(file);
			}
		}

		private void mergeToMainProviderLocked() throws SQLException
		{
			Connection conn = mMainProvider.getConnection();
			conn.setAutoCommit(false);
			try {
				/* do merge work... */
				conn.commit();
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		}

		private void mergeToMainProvider() throws SQLException
		{
			mMainProvider.lock();
			try {
				mergeToMainProviderLocked();
			} finally {
				mMainProvider.unlock();
			}
		}

		private void deleteAllMarked() throws SQLException
		{
			/* TODO */
		}

		private void crawlImpl() throws SQLException
		{
			mMainProvider.getSongDAO().markAll();

			for (String path : mPaths)
				traverse(new File(path));

			if (hasCanceled() == true)
			{
				/* We should probably unmarkAll on the main provider, but we
				 * currently don't have to. */
				return;
			}

			/* Delete every entry that hasn't been unmarked during traversal. */
			deleteAllMarked();

			mergeToMainProvider();
		}

		public void run()
		{
			try {
				crawlImpl();
			} catch (SQLException e) {
				/* TODO */
				e.printStackTrace();
			} finally {
				try {
					mTempProvider.close();
				} catch (SQLException e) {}
				synchronized (FileCrawler.this) {
					mThread = null;
				}
			}
		}
	}
}
