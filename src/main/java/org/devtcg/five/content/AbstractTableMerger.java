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

package org.devtcg.five.content;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.devtcg.five.content.SyncableEntryDAO;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.SyncableProvider;

/**
 * This code and design is largely based on Android's AbstractTableMerger, so
 * the copyright header above is probably void. Should be licensed under Apache
 * as per the original.
 */
public abstract class AbstractTableMerger
{
	private final SyncableProvider mDb;
	private final String mTable;

	public interface SyncableColumns
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

	public AbstractTableMerger(SyncableProvider mainDb, String table)
	{
		mDb = mainDb;
		mTable = table;
	}

	public String getTableName()
	{
		return mTable;
	}

//	public void merge(SyncableProvider serverDiffs, SyncableProvider clientDiffs)
//		throws SQLException
//	{
//		if (serverDiffs != null)
//			mergeServerDiffs(serverDiffs);
//
//		if (clientDiffs != null)
//			findLocalChanges(clientDiffs);
//
//		if (LOG.isInfoEnabled())
//			LOG.info("merge complete.");
//	}
//
//	void mergeServerDiffs(SyncableProvider serverDiffs)
//		throws SQLException
//	{
//		Connection tempConn = serverDiffs.getConnection();
//		Connection mainConn = mDb.getConnection();
//
//		/* Set containing all local entries, to compare against diff set while processing. */
//		ResultSet localSet = DatabaseUtils.executeForResult(mainConn,
//			"SELECT " + SyncableColumns._ID + ", " +
//				SyncableColumns._SYNC_TIME + ", " + SyncableColumns._SYNC_ID + " " +
//				"FROM " + mTable + " ORDER BY " + SyncableColumns._SYNC_ID);
//
//		/* Set containing all diffed entries (to be merged into main provider). */
//		ResultSet diffsSet = DatabaseUtils.executeForResult(tempConn,
//			"SELECT * FROM " + mTable + " ORDER BY " + SyncableColumns._SYNC_ID);
//
//		if (LOG.isDebugEnabled())
//			LOG.debug("Beginning sync of " + mTable);
//
//		try {
//			int localCount = 0;
//			int diffsCount = 0;
//
//			/*
//			 * Move it to the first record; our loop below expects it to keep
//			 * pace with diffsSet.
//			 */
//			boolean localSetHasRows = localSet.next();
//
//			Cursor diffsCursor = new Cursor(diffsSet);
//			ColumnsMap columnsMap = diffsCursor.getColumnsMap();
//			int diffsIdColumn = columnsMap.getColumnIndex(SyncableColumns._ID);
//			int diffsSyncTimeColumn = columnsMap.getColumnIndex(SyncableColumns._SYNC_TIME);
//			int diffsSyncIdColumn = columnsMap.getColumnIndex(SyncableColumns._SYNC_ID);
//
//			/*
//			 * Walk the diffs cursor, replaying each change onto the local
//			 * cursor. This is a merge.
//			 */
//			while (diffsSet.next() == true)
//			{
//				String id = diffsSet.getString(diffsIdColumn);
//				long syncTime = diffsSet.getLong(diffsSyncTimeColumn);
//				String syncId = diffsSet.getString(diffsSyncIdColumn);
//
//				diffsCount++;
//
//				if (LOG.isDebugEnabled())
//					LOG.debug("processing entry #" + diffsCount + ", syncId=" + syncId);
//
//				/* TODO: conflict is not handled yet. */
//				MergeOp mergeOp = MergeOp.NONE;
//
//				long localRowId = 0;
//				long localSyncTime = -1;
//				String localSyncId = null;
//
//				/*
//				 * Position the local cursor to align with the diff cursor. The
//				 * two cursor "walk together" to determine if entries are new,
//				 * updating, or conflicting.
//				 */
//				while (localSetHasRows == true && localSet.isAfterLast() == false)
//				{
//					localCount++;
//					long localId = localSet.getLong(1);
//					localSyncId = localSet.getString(3);
//
//					/*
//					 * If the local record doesn't have a _sync_id, then it is
//					 * new locally. No need to merge it now.
//					 */
//					if (StringUtils.isEmpty(localSyncId))
//					{
//						if (LOG.isDebugEnabled())
//							LOG.debug("local record " + localId + " has no _sync_id, ignoring");
//
//						localSyncId = null;
//						localSet.next();
//						continue;
//					}
//
//					/*
//					 * The local side is authoritative and the the diff side
//					 * contains a record we don't have yet. Insert it out of
//					 * this loop.
//					 */
//					if (StringUtils.isEmpty(syncId))
//					{
//						localSyncId = null;
//						break;
//					}
//
//					int comp = syncId.compareTo(localSyncId);
//
//					/* The local database has a record the server doesn't have. */
//					if (comp > 0)
//					{
//						if (LOG.isDebugEnabled())
//							LOG.debug("local record " + localId + " has _sync_id < server _sync_id " + syncId);
//
//						localSyncId = null;
//						localSet.next();
//						continue;
//					}
//
//					/* The server has a record that the local database doesn't have. */
//					if (comp < 0)
//					{
//						if (LOG.isDebugEnabled())
//							LOG.debug("local record " + localId + " has _sync_id > server _sync_id " + syncId);
//
//						localSyncId = null;
//					}
//					/* The server and the local database both have this record. */
//					else /* if (comp == 0) */
//					{
//						if (LOG.isDebugEnabled())
//							LOG.debug("local record " + localId + " has _sync_id that matches server _sync_id " + syncId);
//
//						localRowId = localId;
//						localSyncTime = localSet.getLong(2);
//						localSet.next();
//					}
//
//					/* We're positioned along with the diffSet. */
//					break;
//				}
//
//				if (syncId != null && localSyncId != null)
//					/* An existing item has changed... */
//					mergeOp = MergeOp.UPDATE;
//				else
//				{
//					/* The local database doesn't know about this record yet. */
//					if (LOG.isDebugEnabled())
//						LOG.debug("remote record " + syncId + " is new, inserting");
//
//					mergeOp = MergeOp.INSERT;
//				}
//
//				switch (mergeOp)
//				{
//					case INSERT:
//						long newId = insertRow(mDb, diffsCursor);
//
//						/*
//						 * If the local side is authoritative, assign the newly
//						 * created row sync id to the local id.
//						 */
//						if (syncId == null)
//							setSyncId(mDb, newId, newId);
//						break;
//					case UPDATE:
//						updateRow(mDb, localRowId, diffsCursor);
//						break;
//					default:
//						throw new RuntimeException("TODO");
//				}
//			}
//
//			if (LOG.isDebugEnabled())
//				LOG.debug("processed " + diffsCount + " server entries");
//		} catch (Exception e) {
//			if (LOG.isErrorEnabled())
//				LOG.error("Sync failed.", e);
//		} finally {
//			diffsSet.close();
//
//			if (LOG.isDebugEnabled())
//				LOG.debug("Sync complete.");
//		}
//	}
//
//	private void setSyncId(Provider main, long id, long syncId) throws SQLException
//	{
//		DatabaseUtils.execute(main.getConnection(),
//			"UPDATE " + mTable + " SET " + SyncableColumns._SYNC_ID + " = ? " +
//			"WHERE " + SyncableColumns._ID + " = ?",
//			String.valueOf(syncId), String.valueOf(id));
//	}

	public void findLocalChanges(SyncableProvider clientDiffs, long lastModified)
		throws SQLException
	{
		/* Find all local changes the other side hasn't seen before. */
		ResultSet set = DatabaseUtils.executeForResult(mDb.getConnection().getWrappedConnection(),
			"SELECT * FROM " + mTable + " WHERE " + SyncableColumns._SYNC_TIME + " > " +
				lastModified, (String[])null);

		InsertHelper inserter = new InsertHelper(clientDiffs.getConnection().getWrappedConnection(),
			mTable, SyncableColumns._ID);

		try {
			int columnCount = set.getMetaData().getColumnCount();

			/* Copy all local changes into the temporary client diffs provider. */
			while (set.next())
			{
				inserter.prepareForInsert();

				/*
				 * The _ID column got moved to the end of the inserter, so we
				 * start our index at 1 here and just avoid copying it in the loop.
				 */
				for (int i = 1; i < columnCount; i++)
					inserter.bind(i, set.getObject(i + 1));

				/* Now copy the _ID. */
				inserter.bind(columnCount, set.getObject(1));

				inserter.execute();
			}
		} finally {
			set.close();
			inserter.close();
		}
	}

	public abstract SyncableEntryDAO getEntryDAO(SyncableProvider clientDiffs)
		throws SQLException;

//	public abstract long insertRow(Provider main, Cursor diffsCursor) throws SQLException;
//	public abstract void deleteRow(Provider main, Cursor diffsCursor) throws SQLException;
//	public abstract void updateRow(Provider main, long id, Cursor diffsCursor) throws SQLException;
//
//	public void resolveRow(Provider main, long id, String syncId, Cursor diffsCursor)
//	{
//		throw new RuntimeException("This table merger does not handle conflicts, but one was detected with id=" + id + ", syncId=" + syncId);
//	}
//
//	private enum MergeOp
//	{
//		NONE, INSERT, UPDATE, CONFLICTED, DELETE
//	}
}
