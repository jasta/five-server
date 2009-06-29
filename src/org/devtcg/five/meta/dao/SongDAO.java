package org.devtcg.five.meta.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.util.FileUtils;
import org.devtcg.five.util.TimeUtils;

public class SongDAO extends AbstractDAO
{
	private static final String TABLE = "songs";

	private InsertHelper mInserter;
	private InsertHelper mUpdater;

	private interface Columns extends BaseColumns
	{
		/** MusicBrainz ID. */
		public static final String MBID = "mbid";

		/** Path to this song on disk; unique. */
		public static final String FILENAME = "filename";

		/** Crude type designation used to distinguish between various audio
		 * formats such as MP3 and Ogg Vorbis. */
		public static final String MIME_TYPE = "mime_type";

		/** Modification time of this file on disk, used to detect changes. */
		public static final String MTIME = "mtime";

		/** Computed or prescribed average bitrate. */
		public static final String BITRATE = "bitrate";

		public static final String FILESIZE = "filesize";

		/** Duration of song (i.e. running length). */
		public static final String LENGTH = "length";

		public static final String TITLE = "title";

		/** Track number (if applicable).  Can be null. */
		public static final String TRACK = "track";

		public static final String ARTIST_ID = "artist_id";

		public static final String ALBUM_ID = "album_id";

		/** Flag used to detect deleted files during a sweep. */
		public static final String MARK = "mark";
	}

	public SongDAO(Provider provider)
	{
		super(provider);
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " INTEGER, " +
			Columns.MBID + " CHAR(36), " +
			Columns.FILENAME + " VARCHAR NOT NULL, " +
			Columns.MIME_TYPE + " VARCHAR, " +
			Columns.MTIME + " INTEGER NOT NULL, " +
			Columns.BITRATE + " INTEGER, " +
			Columns.FILESIZE + " INTEGER NOT NULL, " +
			Columns.LENGTH + " INTEGER, " +
			Columns.TITLE + " VARCHAR NOT NULL, " +
			Columns.TRACK + " INTEGER, " +
			Columns.ARTIST_ID + " INTEGER NOT NULL, " +
			Columns.ALBUM_ID + " INTEGER NOT NULL, " +
			Columns.MARK + " BOOLEAN, " +
			"UNIQUE (" + Columns.FILENAME + ") " +
		")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	public void markAll() throws SQLException
	{
		DatabaseUtils.execute(mProvider.getConnection(),
			"UPDATE " + TABLE + " SET " + Columns.MARK + "=1");
	}

	public void unmark(long _id) throws SQLException
	{
		DatabaseUtils.execute(mProvider.getConnection(),
			"UPDATE " + TABLE + " SET " + Columns.MARK + " = 0 " +
			"WHERE " + Columns._ID + " = ?", String.valueOf(_id));
	}

	public Song getSong(String filename) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.MARK + " = ? LIMIT 1", filename);

		try {
			if (set.next() == false)
				return null;

			return new Song(set);
		} finally {
			if (set != null)
				set.close();
		}
	}

	public long insert(Song song) throws SQLException
	{
		synchronized (this) {
			if (mInserter == null)
			{
				mInserter = new InsertHelper(mProvider.getConnection(),
					"INSERT INTO " + TABLE + "(" +
						Columns._SYNC_TIME + ", " +
						Columns.MBID + ", " +
						Columns.FILENAME + ", " +
						Columns.MIME_TYPE + ", " +
						Columns.MTIME + ", " +
						Columns.BITRATE + ", " +
						Columns.FILESIZE + ", " +
						Columns.LENGTH + ", " +
						Columns.TITLE + ", " +
						Columns.TRACK + ", " +
						Columns.ARTIST_ID + ", " +
						Columns.ALBUM_ID + ", " +
						Columns.MARK + ") " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			}
		}

		return mInserter.insert(TimeUtils.getUnixTimestamp(), song.mbid, song.filename,
			song.mimeType, song.mtime, song.bitrate, song.filesize, song.length,
			song.title, song.track, song.artistId, song.albumId, song.mark);
	}

	public long update(long _id, Song song) throws SQLException
	{
		synchronized (this) {
			if (mUpdater == null)
			{
				mUpdater = new InsertHelper(mProvider.getConnection(),
					"UPDATE " + TABLE + " SET " +
						Columns._SYNC_TIME + " = ?, " +
						Columns.MBID + " = ?, " +
						Columns.FILENAME + " = ?, " +
						Columns.MIME_TYPE + " = ?, " +
						Columns.MTIME + " = ?, " +
						Columns.BITRATE + " = ?, " +
						Columns.FILESIZE + " = ?, " +
						Columns.LENGTH + " = ?, " +
						Columns.TITLE + " = ?, " +
						Columns.TRACK + " = ?, " +
						Columns.ARTIST_ID + " = ?, " +
						Columns.ALBUM_ID + " = ?, " +
						Columns.MARK + " = ? " +
					"WHERE " + Columns._ID + " = ?");
			}
		}

		mUpdater.execute(TimeUtils.getUnixTimestamp(), song.mbid, song.filename,
			song.mimeType, song.mtime, song.bitrate, song.filesize, song.length,
			song.title, song.track, song.artistId, song.albumId, song.mark, _id);

		return _id;
	}

	public Song newSong(File file, long artistId, long albumId, String title,
		int bitrate, long length, int track)
	{
		Song song = new Song();

		song.mtime = TimeUtils.asUnixTimestamp(file.lastModified());
		song.filename = file.getAbsolutePath();
		song.mark = false;
		song.filesize = file.length();
		song.mimeType = FileUtils.getMimeType(file);

		song.artistId = artistId;
		song.albumId = albumId;
		song.title = title;
		song.bitrate = bitrate;
		song.length = length;
		song.track = track;

		return song;
	}

	public class Song
	{
		public long _id;
		public String mbid;
		public String filename;
		public String mimeType;
		public long mtime;
		public int bitrate;
		public long filesize;
		public long length;
		public String title;
		public int track;
		public long artistId;
		public long albumId;
		public boolean mark;

		private Song() {}

		private Song(ResultSet set) throws SQLException
		{
			ResultSetMetaData meta = set.getMetaData();
			int n = meta.getColumnCount();

			for (int i = 1; i <= n; i++)
			{
				String columnName = meta.getColumnName(i);
				if (columnName.equals(Columns._ID))
					_id = set.getLong(i);
				else if (columnName.equals(Columns.MBID))
					mbid = set.getString(i);
				else if (columnName.equals(Columns.FILENAME))
					filename = set.getString(i);
				else if (columnName.equals(Columns.TITLE))
					title = set.getString(i);
				else if (columnName.equals(Columns.MTIME))
					mtime = set.getLong(i);
			}
		}

		public void unmark() throws SQLException
		{
			SongDAO.this.unmark(_id);
		}
	}
}
