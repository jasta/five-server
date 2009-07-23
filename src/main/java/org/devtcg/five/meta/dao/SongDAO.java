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

package org.devtcg.five.meta.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.Cursor;
import org.devtcg.five.meta.MetaProvider;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.util.FileUtils;
import org.devtcg.five.util.TimeUtils;

public class SongDAO extends AbstractDAO
{
	private static final String TABLE = "songs";

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
	protected String getTable()
	{
		return TABLE;
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " INTEGER, " +
			Columns._SYNC_ID + " VARCHAR, " +
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

	private static void copySongToInsertHelper(InsertHelper helper, Song song)
		throws SQLException
	{
		helper.bind(Columns.MBID, song.mbid);
		helper.bind(Columns.FILENAME, song.filename);
		helper.bind(Columns.MIME_TYPE, song.mimeType);
		helper.bind(Columns.MTIME, song.mtime);
		helper.bind(Columns.BITRATE, song.bitrate);
		helper.bind(Columns.FILESIZE, song.filesize);
		helper.bind(Columns.LENGTH, song.length);
		helper.bind(Columns.TITLE, song.title);
		helper.bind(Columns.TRACK, song.track);
		helper.bind(Columns.ARTIST_ID, song.artistId);
		helper.bind(Columns.ALBUM_ID, song.albumId);
		helper.bind(Columns.MARK, song.mark);
	}

	public long insert(Song song) throws SQLException
	{
		return insertDiff(song, null);
	}

	public long insertDiff(Song song, String existingId) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();
		helper.bind(Columns._SYNC_TIME, TimeUtils.getUnixTimestamp());
		helper.bind(Columns._SYNC_ID, existingId);
		copySongToInsertHelper(helper, song);
		return helper.insert();
	}

	public long update(long _id, Song song) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForReplace();
		helper.bind(Columns._ID, _id);
		helper.bind(Columns._SYNC_TIME, TimeUtils.getUnixTimestamp());
		copySongToInsertHelper(helper, song);
		helper.execute();

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
				if (columnName.equalsIgnoreCase(Columns._ID))
					_id = set.getLong(i);
				else if (columnName.equalsIgnoreCase(Columns.MBID))
					mbid = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.FILENAME))
					filename = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.TITLE))
					title = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.MTIME))
					mtime = set.getLong(i);
			}
		}

		public void unmark() throws SQLException
		{
			SongDAO.this.unmark(_id);
		}
	}

	public static class TableMerger extends AbstractTableMerger
	{
		public TableMerger()
		{
			super(TABLE);
		}

		@Override
		public void deleteRow(Provider main, Cursor diffsCursor) throws SQLException
		{
		}

		private long insertOrUpdateRow(Provider main, Long id, Cursor diffsCursor)
			throws SQLException
		{
			InsertHelper helper = ((MetaProvider)main).getArtistDAO().getInsertHelper();

			if (id == null)
				helper.prepareForInsert();
			else
			{
				helper.prepareForReplace();
				helper.bind(Columns._ID, id);
			}

			DatabaseUtils.cursorStringToHelper(Columns._SYNC_TIME, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns._SYNC_ID, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.MBID, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.FILENAME, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.MIME_TYPE, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.MTIME, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.BITRATE, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.FILESIZE, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.LENGTH, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.TITLE, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.TRACK, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.ARTIST_ID, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.ALBUM_ID, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.MARK, diffsCursor, helper);

			if (id == null)
				return helper.insert();
			else
			{
				helper.execute();
				return id;
			}
		}

		@Override
		public long insertRow(Provider main, Cursor diffsCursor) throws SQLException
		{
			return insertOrUpdateRow(main, null, diffsCursor);
		}

		@Override
		public void updateRow(Provider main, long id, Cursor diffsCursor) throws SQLException
		{
			insertOrUpdateRow(main, id, diffsCursor);
		}
	}
}
