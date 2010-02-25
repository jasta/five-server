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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.imageio.ImageIO;

import org.devtcg.five.content.ColumnsMap;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;
import org.devtcg.five.util.ImageUtils;

public class ImageDAO extends AbstractDAO
{
	private static final String TABLE = "images";

	public interface Columns extends BaseColumns
	{
		/** Whether this row refers to an artist photo or album artwork. */
		public static final String TABLE = "table";
		public static final String TABLE_ID = "table_id";

		public static final String WIDTH = "width";
		public static final String HEIGHT = "height";

		public static final String DATA = "data";
	}

	public ImageDAO(Provider provider)
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
		throw new UnsupportedOperationException();
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " BIGINT, " +
			Columns._SYNC_ID + " VARCHAR, " +
			Columns.TABLE + " VARCHAR NOT NULL, " +
			Columns.TABLE_ID + " INTEGER NOT NULL, " +
			Columns.WIDTH + " INTEGER NOT NULL, " +
			Columns.HEIGHT + " INTEGER NOT NULL, " +
			Columns.DATA + " BINARY NOT NULL " +
		")");
		DatabaseUtils.execute(conn, "CREATE INDEX " +
			"idx_" + Columns.TABLE + " ON " + TABLE +
			" (" + Columns.TABLE + ", " + Columns.TABLE_ID + ")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	private ImageEntryDAO getImage(long id) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns._ID + " = ?",
			String.valueOf(id));

		return ImageEntryDAO.newInstance(set);
	}

	public ImageEntryDAO getLargestImage(String table, long tableId) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.TABLE + " = ? AND " + Columns.TABLE_ID + " = ? " +
			"ORDER BY WIDTH*HEIGHT DESC LIMIT 1",
			table, String.valueOf(tableId));

		return ImageEntryDAO.newInstance(set);
	}

	public ImageEntryDAO getSmallestImage(String table, long tableId) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.TABLE + " = ? AND " + Columns.TABLE_ID + " = ? " +
			"ORDER BY WIDTH*HEIGHT ASC LIMIT 1",
			table, String.valueOf(tableId));

		return ImageEntryDAO.newInstance(set);
	}

	public ImageEntryDAO getImageAtSize(String table, long tableId, int width, int height)
			throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection().getWrappedConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.TABLE + " = ? AND " + Columns.TABLE_ID + " = ? AND " +
			Columns.WIDTH + " = ? AND " + Columns.HEIGHT + " = ?",
			table, String.valueOf(tableId), String.valueOf(width), String.valueOf(height));

		return ImageEntryDAO.newInstance(set);
	}

	/**
	 * Request a version of the image at the specified size. If this can't be
	 * satisfied within the database, the largest image will be used and scaled
	 * to match (even if that means scaling up).
	 */
	public ImageEntryDAO requestImageAtSize(String table, long tableId, int width, int height)
			throws SQLException
	{
		ImageEntryDAO exactMatch = getImageAtSize(table, tableId, width, height);
		if (exactMatch != null)
			return exactMatch;

		ImageEntryDAO largestAvailable = getLargestImage(table, tableId);
		if (largestAvailable == null)
			return null;

		try {
			byte[] scaledData = ImageUtils.getScaledInstance(largestAvailable.getData(), width, height);
			if (scaledData == null)
				return null;

			/*
			 * XXX: If upscaling occurs, we should mark that somehow in the
			 * database so that we know this isn't really the best quality image
			 * to use for future scaling.
			 */
			long id = insert(table, tableId, width, height, scaledData);
			return getImage(id);
		} finally {
			largestAvailable.close();
		}
	}

	public long insert(String table, long tableId, byte[] data) throws SQLException, IOException
	{
		BufferedImage buf = ImageIO.read(new ByteArrayInputStream(data));
		return insert(table, tableId, buf.getWidth(), buf.getHeight(), data);
	}

	public long insert(String table, long tableId, int width, int height, byte[] data) throws SQLException
	{
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = System.currentTimeMillis();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.TABLE, table);
		helper.bind(Columns.TABLE_ID, tableId);
		helper.bind(Columns.WIDTH, width);
		helper.bind(Columns.HEIGHT, height);
		helper.bind(Columns.DATA, data);

		return helper.insert();
	}

	public static class ImageEntryDAO extends AbstractSyncableEntryDAO
	{
		private final int mColumnId;
		private final int mColumnSyncTime;
		private final int mColumnTable;
		private final int mColumnTableId;
		private final int mColumnWidth;
		private final int mColumnHeight;
		private final int mColumnData;

		private static final Creator<ImageEntryDAO> CREATOR = new Creator<ImageEntryDAO>()
		{
			@Override
			public ImageEntryDAO init(ResultSet set) throws SQLException
			{
				return new ImageEntryDAO(set);
			}
		};

		public static ImageEntryDAO newInstance(ResultSet set) throws SQLException
		{
			return CREATOR.newInstance(set);
		}

		private ImageEntryDAO(SyncableProvider provider, String table) throws SQLException
		{
			this(getResultSet(provider, table));
		}

		private ImageEntryDAO(ResultSet set) throws SQLException
		{
			super(set);

			ColumnsMap map = ColumnsMap.fromResultSet(set);

			mColumnId = map.getColumnIndex(Columns._ID);
			mColumnSyncTime = map.getColumnIndex(Columns._SYNC_TIME);
			mColumnTable = map.getColumnIndex(Columns.TABLE);
			mColumnTableId = map.getColumnIndex(Columns.TABLE_ID);
			mColumnWidth = map.getColumnIndex(Columns.WIDTH);
			mColumnHeight = map.getColumnIndex(Columns.HEIGHT);
			mColumnData = map.getColumnIndex(Columns.DATA);
		}

		public long getId() throws SQLException
		{
			return mSet.getLong(mColumnId);
		}

		public long getSyncTime() throws SQLException
		{
			return mSet.getLong(mColumnSyncTime);
		}

		public String getTable() throws SQLException
		{
			return mSet.getString(mColumnTable);
		}

		public long getTableId() throws SQLException
		{
			return mSet.getLong(mColumnTableId);
		}

		public int getWidth() throws SQLException
		{
			return mSet.getInt(mColumnWidth);
		}

		public int getHeight() throws SQLException
		{
			return mSet.getInt(mColumnHeight);
		}

		public byte[] getData() throws SQLException
		{
			return mSet.getBytes(mColumnData);
		}

		public String getContentType()
		{
			throw new UnsupportedOperationException();
		}

		public Protos.Record getEntry() throws SQLException
		{
			throw new UnsupportedOperationException();
		}
	}
}
