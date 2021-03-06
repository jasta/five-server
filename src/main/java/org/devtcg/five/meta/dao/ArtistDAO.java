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
import org.devtcg.five.util.StringUtils;

public class ArtistDAO extends AbstractDAO
{
	private static final String TABLE = "artists";
	private static final String DELETED_TABLE = "artists_deleted";

	public interface Columns extends BaseColumns
	{
		/** MusicBrainz ID. */
		public static final String MBID = "mbid";

		public static final String NAME = "name";

		/** Canonicalized name used for comparison. Excludes articles,
		 *  punctuation, etc. */
		public static final String NAME_MATCH = "name_match";

		/** Timestamp this row was created (i.e. when Five first noticed). */
		public static final String DISCOVERY_DATE = "discovery_date";
	}

	public ArtistDAO(Provider provider)
	{
		super(provider);
	}

	@Override
	public String getTable()
	{
		return TABLE;
	}

	@Override
	public String getDeletedTable()
	{
		return DELETED_TABLE;
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " BIGINT, " +
			Columns._SYNC_ID + " VARCHAR, " +
			Columns.MBID + " CHAR(36), " +
			Columns.NAME + " VARCHAR NOT NULL, " +
			Columns.NAME_MATCH + " VARCHAR NOT NULL, " +
			Columns.DISCOVERY_DATE + " BIGINT, " +
			"UNIQUE (" + Columns.NAME_MATCH + ") " +
		")");

		createDeletedTable(conn);
	}

	public ArtistEntryDAO getArtist(long id) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns._ID + " = ?",
			String.valueOf(id));

		return ArtistEntryDAO.newInstance(set);
	}

	public ArtistEntryDAO getArtist(String name) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.NAME_MATCH + " = ?",
			name);

		return ArtistEntryDAO.newInstance(set);
	}

	public ArtistEntryDAO getEmptyArtists() throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
				"SELECT " + fullColumn("*") + " FROM " + TABLE +
				" LEFT JOIN " + AlbumDAO.TABLE + " ON " +
				fullColumn(AlbumDAO.TABLE, AlbumDAO.Columns.ARTIST_ID) + " = " +
					fullColumn(Columns._ID) +
				" LEFT JOIN " + SongDAO.TABLE + " ON " +
				fullColumn(SongDAO.TABLE, SongDAO.Columns.ARTIST_ID) + " = " +
					fullColumn(Columns._ID) +
				" WHERE " + fullColumn(AlbumDAO.TABLE, AlbumDAO.Columns._ID) + " IS NULL AND " +
				fullColumn(SongDAO.TABLE, SongDAO.Columns._ID) + " IS NULL");

		return new ArtistEntryDAO(set);
	}

	public long insert(String name) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = System.currentTimeMillis();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.NAME, name);
		helper.bind(Columns.NAME_MATCH, StringUtils.getNameMatch(name));
		helper.bind(Columns.DISCOVERY_DATE, now);

		return helper.insert();
	}

	public void updateMbid(long id, String mbid) throws SQLException
	{
		updateColumn(id, Columns.MBID, mbid);
	}

	public static class Artist
	{
		public long _id;
		public String mbid;
		public String name;
		public String nameMatch;
		public long discoveryDate;

		private Artist() {}
	}

	public static class ArtistEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnMbid;
		private final int mColumnName;
		private final int mColumnNameMatch;
		private final int mColumnDiscoveryDate;

		private static final Creator<ArtistEntryDAO> CREATOR = new Creator<ArtistEntryDAO>()
		{
			@Override
			public ArtistEntryDAO init(ResultSet set) throws SQLException
			{
				return new ArtistEntryDAO(set);
			}
		};

		public static ArtistEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private ArtistEntryDAO(SyncableProvider provider, String table) throws SQLException
		{
			this(getResultSet(provider, table));
		}

		private ArtistEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnMbid = map.getColumnIndex(Columns.MBID);
			mColumnName = map.getColumnIndex(Columns.NAME);
			mColumnNameMatch = map.getColumnIndex(Columns.NAME_MATCH);
			mColumnDiscoveryDate = map.getColumnIndex(Columns.DISCOVERY_DATE);
		}

		public long getId() throws SQLException
		{
			return mSet.getLong(mColumnId);
		}

		public long getSyncTime() throws SQLException
		{
			return mSet.getLong(mColumnSyncTime);
		}

		public String getMbid() throws SQLException
		{
			return mSet.getString(mColumnMbid);
		}

		public String getName() throws SQLException
		{
			return mSet.getString(mColumnName);
		}

		public String getNameMatch() throws SQLException
		{
			return mSet.getString(mColumnNameMatch);
		}

		public long getDiscoveryDate() throws SQLException
		{
			return mSet.getLong(mColumnDiscoveryDate);
		}

		public String getContentType()
		{
			return "application/vnd.five.artist";
		}

		public Protos.Record getEntry() throws SQLException
		{
			Protos.Artist.Builder builder = Protos.Artist.newBuilder();
			builder.setId(getId());
			builder.setSyncTime(getSyncTime());
			String mbid = getMbid();
			if (mbid != null)
				builder.setMbid(getMbid());
			builder.setName(getName());
			builder.setDiscoveryDate(getDiscoveryDate());

			return Protos.Record.newBuilder()
				.setType(Protos.Record.Type.ARTIST)
				.setArtist(builder.build()).build();
		}

		public String toString()
		{
			try {
				return "{id=" + getId() + ", name=" + getName() + ", name_match=" + getNameMatch() + "}";
			} catch (SQLException e) {
				return super.toString();
			}
		}
	}

	public class TableMerger extends AbstractTableMerger
	{
		public TableMerger()
		{
			super((SyncableProvider)getProvider(), TABLE, DELETED_TABLE);
		}

		@Override
		public SyncableEntryDAO getEntryDAO(SyncableProvider clientDiffs) throws SQLException
		{
			return new ArtistEntryDAO(clientDiffs, TABLE);
		}

		@Override
		public SyncableEntryDAO getDeletedEntryDAO(SyncableProvider clientDiffs) throws SQLException
		{
			return new ArtistEntryDAO(clientDiffs, DELETED_TABLE);
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
//			DatabaseUtils.cursorStringToHelper(Columns.NAME, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.NAME_MATCH, diffsCursor, helper);
//			DatabaseUtils.cursorStringToHelper(Columns.DISCOVERY_DATE, diffsCursor, helper);
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
