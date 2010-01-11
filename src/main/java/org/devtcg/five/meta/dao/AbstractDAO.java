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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.content.AbstractTableMerger.SyncableColumns;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.persistence.SyncableProvider;

public abstract class AbstractDAO
{
	protected final Provider mProvider;

	private InsertHelper mInserter;

	public AbstractDAO(Provider provider)
	{
		mProvider = provider;
	}

	public interface BaseColumns extends SyncableColumns
	{
		/** Autoincrement id. */
		public static final String _ID = "_id";

		/**
		 * Timestamp of last modification. Not currently considered during
		 * merge as this implementation does not do conflict resolution.
		 */
		public static final String _SYNC_TIME = "_sync_time";

		/** Sync id corresponding to the main (non-temporary) provider. */
		public static final String _SYNC_ID = "_sync_id";
	}

	public Provider getProvider()
	{
		return mProvider;
	}

	public abstract void createTables(Connection conn) throws SQLException;
	public abstract void dropTables(Connection conn) throws SQLException;

	public abstract String getTable();

	protected synchronized InsertHelper getInsertHelper() throws SQLException
	{
		if (mInserter == null)
		{
			mInserter = new InsertHelper(mProvider.getConnection().getWrappedConnection(),
				getTable());
		}

		return mInserter;
	}

	protected void updateColumn(long id, String column, String value) throws SQLException
	{
		long now = System.currentTimeMillis();

		DatabaseUtils.execute(mProvider.getConnection().getWrappedConnection(),
				"UPDATE " + getTable() + " SET " +
				column + " = ?, " +
				BaseColumns._SYNC_TIME + " = ? " +
				"WHERE " + BaseColumns._ID + " = ?", value, String.valueOf(now), String.valueOf(id));
	}

	protected static abstract class AbstractSyncableEntryDAO implements SyncableEntryDAO
	{
		protected final ResultSet mSet;

		protected static ResultSet getResultSet(SyncableProvider provider, String table)
			throws SQLException
		{
			return DatabaseUtils.executeForResult(provider.getConnection().getWrappedConnection(),
				"SELECT * FROM " + table, (String[])null);
		}

		public AbstractSyncableEntryDAO(ResultSet set) throws SQLException
		{
			if (set == null)
				throw new IllegalArgumentException("ResultSet must not be null.");

			mSet = set;
		}

		public void close() throws SQLException
		{
			mSet.close();
		}

		public boolean moveToNext() throws SQLException
		{
			return mSet.next();
		}

		protected static abstract class Creator<T extends AbstractSyncableEntryDAO>
		{
			public T newInstance(ResultSet set) throws SQLException
			{
				if (set == null)
					return null;

				try {
					if (set.next() == true)
						return init(set);
				} catch (SQLException e) {
					set.close();
					return null;
				}

				set.close();
				return null;
			}

			public abstract T init(ResultSet set) throws SQLException;
		}
	}
}
