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
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.ColumnsMap;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.meta.dao.AbstractDAO.AbstractSyncableEntryDAO;
import org.devtcg.five.meta.dao.AbstractDAO.AbstractSyncableEntryDAO.Creator;
import org.devtcg.five.meta.dao.AlbumDAO.AlbumEntryDAO;
import org.devtcg.five.meta.dao.AlbumDAO.Columns;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.meta.data.Protos.Record;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;
import org.devtcg.five.util.FileUtils;

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
			Columns._SYNC_TIME + " BIGINT, " +
			Columns._SYNC_ID + " VARCHAR, " +
			Columns.MBID + " CHAR(36), " +
			Columns.FILENAME + " VARCHAR NOT NULL, " +
			Columns.MIME_TYPE + " VARCHAR, " +
			Columns.MTIME + " BIGINT NOT NULL, " +
			Columns.BITRATE + " INTEGER, " +
			Columns.FILESIZE + " BIGINT NOT NULL, " +
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
		DatabaseUtils.execute(mProvider.getConnection().getWrappedConnection(),
			"UPDATE " + TABLE + " SET " + Columns.MARK + "=1");
	}

	public void unmark(long _id) throws SQLException
	{
		DatabaseUtils.execute(mProvider.getConnection().getWrappedConnection(),
			"UPDATE " + TABLE + " SET " + Columns.MARK + " = 0 " +
			"WHERE " + Columns._ID + " = ?", String.valueOf(_id));
	}

	public SongEntryDAO getSong(long id) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns._ID + " = ? LIMIT 1",
			String.valueOf(id));

		return SongEntryDAO.newInstance(set);
	}

	public SongEntryDAO getSong(String filename) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.FILENAME + " = ? LIMIT 1", filename);

		return SongEntryDAO.newInstance(set);
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
		helper.bind(Columns._SYNC_TIME, System.currentTimeMillis());
		helper.bind(Columns._SYNC_ID, existingId);
		copySongToInsertHelper(helper, song);
		return helper.insert();
	}

	public long update(long _id, Song song) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForReplace();
		helper.bind(Columns._ID, _id);
		helper.bind(Columns._SYNC_TIME, System.currentTimeMillis());
		copySongToInsertHelper(helper, song);
		helper.execute();

		return _id;
	}

	public Song newSong(File file, long artistId, long albumId, String title,
		int bitrate, long length, int track)
	{
		Song song = new Song();

		song.mtime = file.lastModified();
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

	public static class Song
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
	}

	public static class SongEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnArtistId;
		private final int mColumnAlbumId;
		private final int mColumnMbid;
		private final int mColumnFilename;
		private final int mColumnTitle;
		private final int mColumnMtime;
		private final int mColumnBitrate;
		private final int mColumnMimeType;
		private final int mColumnLength;
		private final int mColumnTrack;

		private static final Creator<SongEntryDAO> CREATOR = new Creator<SongEntryDAO>()
		{
			@Override
			public SongEntryDAO init(ResultSet set) throws SQLException
			{
				return new SongEntryDAO(set);
			}
		};

		public static SongEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private SongEntryDAO(SyncableProvider provider) throws SQLException
		{
			this(getResultSet(provider, TABLE));
		}

		private SongEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnArtistId = map.getColumnIndex(Columns.ARTIST_ID);
			mColumnAlbumId = map.getColumnIndex(Columns.ALBUM_ID);
			mColumnMbid = map.getColumnIndex(Columns.MBID);
			mColumnFilename = map.getColumnIndex(Columns.FILENAME);
			mColumnTitle = map.getColumnIndex(Columns.TITLE);
			mColumnMtime = map.getColumnIndex(Columns.MTIME);
			mColumnBitrate = map.getColumnIndex(Columns.BITRATE);
			mColumnMimeType = map.getColumnIndex(Columns.MIME_TYPE);
			mColumnLength = map.getColumnIndex(Columns.LENGTH);
			mColumnTrack = map.getColumnIndex(Columns.TRACK);
		}

		public long getId() throws SQLException
		{
			return mSet.getLong(mColumnId);
		}

		public long getSyncTime() throws SQLException
		{
			return mSet.getLong(mColumnSyncTime);
		}

		public long getArtistId() throws SQLException
		{
			return mSet.getLong(mColumnArtistId);
		}

		public long getAlbumId() throws SQLException
		{
			return mSet.getLong(mColumnAlbumId);
		}

		public String getMbid() throws SQLException
		{
			return mSet.getString(mColumnMbid);
		}

		public String getTitle() throws SQLException
		{
			return mSet.getString(mColumnTitle);
		}

		public String getFilename() throws SQLException
		{
			return mSet.getString(mColumnFilename);
		}

		public long getMtime() throws SQLException
		{
			return mSet.getLong(mColumnMtime);
		}

		public int getBitrate() throws SQLException
		{
			return mSet.getInt(mColumnBitrate);
		}

		public String getMimeType() throws SQLException
		{
			return mSet.getString(mColumnMimeType);
		}

		public int getLength() throws SQLException
		{
			return mSet.getInt(mColumnLength);
		}

		public int getTrack() throws SQLException
		{
			return mSet.getInt(mColumnTrack);
		}

		public String getContentType()
		{
			return "application/vnd.five.song";
		}

		public Record getEntry() throws SQLException
		{
			Protos.Song.Builder builder = Protos.Song.newBuilder();
			builder.setId(getId());
			builder.setSyncTime(getSyncTime());
			builder.setArtistId(getArtistId());
			builder.setAlbumId(getAlbumId());
			String mbid = getMbid();
			if (mbid != null)
				builder.setMbid(getMbid());
			String mimeType = getMimeType();
			if (mimeType != null)
				builder.setMimeType(mimeType);
			builder.setBitrate(getBitrate());
			builder.setFilesize(new File(getFilename()).length());
			builder.setLength(getLength());
			builder.setTitle(getTitle());
			builder.setTrack(getTrack());

			return Protos.Record.newBuilder()
				.setType(Protos.Record.Type.SONG)
				.setSong(builder.build()).build();
		}

		public String toString()
		{
			try {
				return "{id=" + getId() + ", artistId=" + getArtistId() + ", albumId=" + getAlbumId() + ", filename=" + getFilename() + ", title=" + getTitle() + ", bitrate=" + getBitrate() + "}";
			} catch (SQLException e) {
				return super.toString();
			}
		}
	}

	public class TableMerger extends AbstractTableMerger
	{
		public TableMerger()
		{
			super((SyncableProvider)getProvider(), TABLE);
		}

		@Override
		public SyncableEntryDAO getEntryDAO(SyncableProvider clientDiffs) throws SQLException
		{
			return new SongEntryDAO(clientDiffs);
		}
	}

//	public class TableMerger extends AbstractTableMerger
//	{
//		public TableMerger()
//		{
//			super((SyncableProvider)getProvider(), TABLE);
//		}
//
//		@Override
//		public void deleteRow(Provider main, Cursor diffsCursor) throws SQLException
//		{
//		}
//
//		private long insertOrUpdateRow(Provider main, Long id, Cursor diffsCursor)
//			throws SQLException
//		{
//			InsertHelper helper = ((MetaProvider)main).getArtistDAO().getInsertHelper();
//
//			if (id == null)
//				helper.prepareForInsert();
//			else
//			{
//				helper.prepareForReplace();
//				helper.bind(Columns._ID, id);
//			}
//
//			DatabaseUtils.cursorStringToHelper(Columns._SYNC_TIME, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns._SYNC_ID, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.MBID, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.FILENAME, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.MIME_TYPE, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.MTIME, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.BITRATE, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.FILESIZE, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.LENGTH, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.TITLE, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.TRACK, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.ARTIST_ID, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.ALBUM_ID, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.MARK, diffsCursor, helper);
//
//			if (id == null)
//				return helper.insert();
//			else
//			{
//				helper.execute();
//				return id;
//			}
//		}
//
//		@Override
//		public long insertRow(Provider main, Cursor diffsCursor) throws SQLException
//		{
//			return insertOrUpdateRow(main, null, diffsCursor);
//		}
//
//		@Override
//		public void updateRow(Provider main, long id, Cursor diffsCursor) throws SQLException
//		{
//			insertOrUpdateRow(main, id, diffsCursor);
//		}
//	}
}
