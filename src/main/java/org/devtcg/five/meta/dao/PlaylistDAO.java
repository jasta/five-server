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
import java.util.LinkedList;
import java.util.List;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.ColumnsMap;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;

public class PlaylistDAO extends AbstractDAO
{
	private static final String TABLE = "playlists";

	public interface Columns extends BaseColumns
	{
		public static final String FILENAME = "filename";

		public static final String NAME = "name";

		public static final String CREATED_DATE = "created_date";

		public static final String MARK = "mark";
	}

	public PlaylistDAO(Provider provider)
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
			Columns.FILENAME + " VARCHAR NOT NULL, " +
			Columns.NAME + " VARCHAR NOT NULL, " +
			Columns.CREATED_DATE + " BIGINT, " +
			Columns.MARK + " BOOLEAN DEFAULT 0, " +
			"UNIQUE (" + Columns.FILENAME + ") " +
		")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	public PlaylistEntryDAO getPlaylist(long id) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns._ID + " = ?",
			String.valueOf(id));

		return PlaylistEntryDAO.newInstance(set);
	}

	public PlaylistEntryDAO getPlaylist(String filename) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.FILENAME + " = ?",
			filename);

		return PlaylistEntryDAO.newInstance(set);
	}

	public long insert(String filename, String name, long createdDate) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = System.currentTimeMillis();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.FILENAME, filename);
		helper.bind(Columns.NAME, name);
		helper.bind(Columns.CREATED_DATE, createdDate);
		helper.bind(Columns.MARK, 0);

		return helper.insert();
	}

	public void unmark(long _id) throws SQLException
	{
		DatabaseUtils.execute(mProvider.getConnection().getWrappedConnection(),
			"UPDATE " + TABLE + " SET " + Columns.MARK + " = 0 " +
			"WHERE " + Columns._ID + " = ?", String.valueOf(_id));
	}

	public Playlist newPlaylist()
	{
		return new Playlist();
	}

	public static class Playlist
	{
		public long _id;
		public String filename;
		public boolean hasChanges;
		public final List<Long> songs = new LinkedList<Long>();

		private Playlist() {}
	}

	public static class PlaylistEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnName;
		private final int mColumnCreatedDate;

		private static final Creator<PlaylistEntryDAO> CREATOR = new Creator<PlaylistEntryDAO>()
		{
			@Override
			public PlaylistEntryDAO init(ResultSet set) throws SQLException
			{
				return new PlaylistEntryDAO(set);
			}
		};

		public static PlaylistEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private PlaylistEntryDAO(SyncableProvider provider) throws SQLException
		{
			this(getResultSet(provider, TABLE));
		}

		private PlaylistEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnName = map.getColumnIndex(Columns.NAME);
			mColumnCreatedDate = map.getColumnIndex(Columns.CREATED_DATE);
		}

		public long getId() throws SQLException
		{
			return mSet.getLong(mColumnId);
		}

		public long getSyncTime() throws SQLException
		{
			return mSet.getLong(mColumnSyncTime);
		}

		public String getName() throws SQLException
		{
			return mSet.getString(mColumnName);
		}

		public long getCreatedDate() throws SQLException
		{
			return mSet.getLong(mColumnCreatedDate);
		}

		public String getContentType()
		{
			return "application/vnd.five.playlist";
		}

		public Protos.Record getEntry() throws SQLException
		{
			Protos.Playlist.Builder builder = Protos.Playlist.newBuilder();
			builder.setId(getId());
			builder.setSyncTime(getSyncTime());
			builder.setName(getName());
			builder.setCreatedDate(getCreatedDate());

			return Protos.Record.newBuilder()
				.setType(Protos.Record.Type.PLAYLIST)
				.setPlaylist(builder.build()).build();
		}

		public String toString()
		{
			try {
				return "{id=" + getId() + ", name=" + getName() + "}";
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
			return new PlaylistEntryDAO(clientDiffs);
		}
	}
}
