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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.ColumnsMap;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;

public class PlaylistSongDAO extends AbstractDAO
{
	private static final String TABLE = "playlist_songs";

	public interface Columns extends BaseColumns
	{
		public static final String PLAYLIST_ID = "playlist_id";
		public static final String POSITION = "pos";
		public static final String SONG_ID = "song_id";
	}

	public PlaylistSongDAO(Provider provider)
	{
		super(provider);
	}

	@Override
	public String getTable()
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
			Columns.PLAYLIST_ID + " INTEGER NOT NULL, " +
			Columns.POSITION + " INTEGER NOT NULL, " +
			Columns.SONG_ID + " INTEGER NOT NULL " +
		")");
		DatabaseUtils.execute(conn, "CREATE UNIQUE INDEX " +
			"idx_" + Columns.PLAYLIST_ID + "_" +
				Columns.POSITION + "_" +
				Columns.SONG_ID + " ON " + TABLE +
			" (" +
				Columns.PLAYLIST_ID + ", " +
				Columns.POSITION + ", " +
				Columns.SONG_ID +
			")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	public long getSongAtPosition(long playlistId, int position) throws SQLException
	{
		return DatabaseUtils.longForQuery(mProvider.getConnection().getWrappedConnection(),
			-1,
			"SELECT " + Columns.SONG_ID + " FROM " + TABLE + " WHERE " + Columns.PLAYLIST_ID + " = ? AND " +
				Columns.POSITION + " = ?",
			String.valueOf(playlistId), String.valueOf(position));
	}

	public void deleteByPlaylist(long playlistId) throws SQLException
	{
		int numSongs = DatabaseUtils.integerForQuery(mProvider.getConnection().getWrappedConnection(),
			0,
			"SELECT COUNT(*) FROM " + TABLE + " WHERE " + Columns.PLAYLIST_ID + " = ?",
			String.valueOf(playlistId));

		if (numSongs > 0)
			throw new UnsupportedOperationException("Delete is not supported yet!");
	}

	public long insert(long playlistId, int position, long songId) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = System.currentTimeMillis();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.PLAYLIST_ID, playlistId);
		helper.bind(Columns.POSITION, position);
		helper.bind(Columns.SONG_ID, songId);

		return helper.insert();
	}

	public static class PlaylistSongEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnPlaylistId;
		private final int mColumnPosition;
		private final int mColumnSongId;

		private static final Creator<PlaylistSongEntryDAO> CREATOR = new Creator<PlaylistSongEntryDAO>()
		{
			@Override
			public PlaylistSongEntryDAO init(ResultSet set) throws SQLException
			{
				return new PlaylistSongEntryDAO(set);
			}
		};

		public static PlaylistSongEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private PlaylistSongEntryDAO(SyncableProvider provider) throws SQLException
		{
			this(getResultSet(provider, TABLE));
		}

		private PlaylistSongEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnPlaylistId = map.getColumnIndex(Columns.PLAYLIST_ID);
			mColumnPosition = map.getColumnIndex(Columns.POSITION);
			mColumnSongId = map.getColumnIndex(Columns.SONG_ID);
		}

		public long getId() throws SQLException
		{
			return mSet.getLong(mColumnId);
		}

		public long getSyncTime() throws SQLException
		{
			return mSet.getLong(mColumnSyncTime);
		}

		public long getPlaylistId() throws SQLException
		{
			return mSet.getLong(mColumnPlaylistId);
		}

		public int getPosition() throws SQLException
		{
			return mSet.getInt(mColumnPosition);
		}

		public long getSongId() throws SQLException
		{
			return mSet.getLong(mColumnSongId);
		}

		public String getContentType()
		{
			return "application/vnd.five.playlistsong";
		}

		public Protos.Record getEntry() throws SQLException
		{
			Protos.PlaylistSong.Builder builder = Protos.PlaylistSong.newBuilder();
			builder.setId(getId());
			builder.setSyncTime(getSyncTime());
			builder.setPlaylistId(getPlaylistId());
			builder.setPosition(getPosition());
			builder.setSongId(getSongId());

			return Protos.Record.newBuilder()
				.setType(Protos.Record.Type.PLAYLIST_SONG)
				.setPlaylistSong(builder.build()).build();
		}

		public String toString()
		{
			try {
				return "{id=" + getId() + ", playlistId=" + getPlaylistId() + ", songId=" + getSongId() + "}";
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
			return new PlaylistSongEntryDAO(clientDiffs);
		}
	}
}
