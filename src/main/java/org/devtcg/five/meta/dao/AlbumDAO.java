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

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.ColumnsMap;
import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.meta.dao.AbstractDAO.AbstractSyncableEntryDAO.Creator;
import org.devtcg.five.meta.dao.ArtistDAO.ArtistEntryDAO;
import org.devtcg.five.meta.dao.ArtistDAO.Columns;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.meta.data.Protos.Record;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;
import org.devtcg.five.util.StringUtils;

public class AlbumDAO extends AbstractDAO
{
	private static final String TABLE = "albums";

	public interface Columns extends BaseColumns
	{
		public static final String ARTIST_ID = "artist_id";

		/** MusicBrainz ID. */
		public static final String MBID = "mbid";

		public static final String NAME = "name";

		/** Canonicalized name used for comparison. Excludes articles,
		 *  punctuation, etc. */
		public static final String NAME_MATCH = "name_match";

		/** Timestamp this row was created (i.e. when Five first noticed). */
		public static final String DISCOVERY_DATE = "discovery_date";

		public static final String RELEASE_DATE = "release_date";
	}

	public AlbumDAO(Provider provider)
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
			Columns.ARTIST_ID + " INTEGER NOT NULL, " +
			Columns.MBID + " CHAR(36), " +
			Columns.NAME + " VARCHAR NOT NULL, " +
			Columns.NAME_MATCH + " VARCHAR NOT NULL, " +
			Columns.DISCOVERY_DATE + " BIGINT, " +
			Columns.RELEASE_DATE + " BIGINT " +
		")");
		DatabaseUtils.execute(conn, "CREATE INDEX " +
			"idx_" + Columns.ARTIST_ID + " ON " + TABLE +
			" (" + Columns.ARTIST_ID + ")");
		DatabaseUtils.execute(conn, "CREATE UNIQUE INDEX " +
			"idx_" + Columns.ARTIST_ID + "_" + Columns.NAME_MATCH + " ON " + TABLE +
			" (" + Columns.ARTIST_ID + ", " + Columns.NAME_MATCH + ")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	public AlbumEntryDAO getAlbum(long artistId, String nameMatch) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.NAME_MATCH + " = ? AND " +
				Columns.ARTIST_ID + " = ?",
			nameMatch, String.valueOf(artistId));

		return AlbumEntryDAO.newInstance(set);
	}

	public long insert(long artistId, String name) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = System.currentTimeMillis();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.ARTIST_ID, artistId);
		helper.bind(Columns.NAME, name);
		helper.bind(Columns.NAME_MATCH, StringUtils.getNameMatch(name));
		helper.bind(Columns.DISCOVERY_DATE, now);

		return helper.insert();
	}

	public static class Album
	{
		public long _id;
		public long artistId;
		public String mbid;
		public String name;
		public String nameMatch;
		public long discoveryDate;
		public long releaseDate;

		private Album() {}
	}

	public static class AlbumEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnArtistId;
		private final int mColumnMbid;
		private final int mColumnName;
		private final int mColumnNameMatch;
		private final int mColumnDiscoveryDate;
		private final int mColumnReleaseDate;

		private static final Creator<AlbumEntryDAO> CREATOR = new Creator<AlbumEntryDAO>()
		{
			@Override
			public AlbumEntryDAO init(ResultSet set) throws SQLException
			{
				return new AlbumEntryDAO(set);
			}
		};

		public static AlbumEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private AlbumEntryDAO(SyncableProvider provider) throws SQLException
		{
			this(getResultSet(provider, TABLE));
		}

		private AlbumEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnArtistId = map.getColumnIndex(Columns.ARTIST_ID);
			mColumnMbid = map.getColumnIndex(Columns.MBID);
			mColumnName = map.getColumnIndex(Columns.NAME);
			mColumnNameMatch = map.getColumnIndex(Columns.NAME_MATCH);
			mColumnDiscoveryDate = map.getColumnIndex(Columns.DISCOVERY_DATE);
			mColumnReleaseDate = map.getColumnIndex(Columns.RELEASE_DATE);
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

		public String getContentType()
		{
			return "application/vnd.five.album";
		}

		public Record getEntry() throws SQLException
		{
			Protos.Album.Builder builder = Protos.Album.newBuilder();
			builder.setId(getId());
			builder.setSyncTime(getSyncTime());
			builder.setArtistId(getArtistId());
			String mbid = getMbid();
			if (mbid != null)
				builder.setMbid(getMbid());
			builder.setName(getName());

			return Protos.Record.newBuilder()
				.setType(Protos.Record.Type.ALBUM)
				.setAlbum(builder.build()).build();
		}

		public String toString()
		{
			try {
				return "{id=" + getId() + ", artistId=" + getArtistId() + ", name=" + getName() + ", name_match=" + getNameMatch() + "}";
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
			return new AlbumEntryDAO(clientDiffs);
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
//			DatabaseUtils.cursorStringToHelper(Columns.RELEASE_DATE, diffsCursor, helper);
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
