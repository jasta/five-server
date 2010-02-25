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

package org.devtcg.five.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.devtcg.five.meta.dao.ImageDAO.Columns;

/**
 * Prepared statement helper for deleting rows from a primary table and
 * inserting them into the deleted transaction log.
 */
public class DeleteHelper
{
	private final Connection mConnection;
	private final String mMainTable;
	private final String mDeletedTable;
	private final String mIdColumn;

	private long mId;

	private PreparedStatement mDeleteStatement;
	private PreparedStatement mCheckLogStatement;
	private InsertHelper mLogInserter;

	public DeleteHelper(Connection conn, String mainTable, String deletedTable)
	{
		this(conn, mainTable, deletedTable, Columns._ID);
	}

	public DeleteHelper(Connection conn, String mainTable, String deletedTable, String idColumn)
	{
		mConnection = conn;
		mMainTable = mainTable;
		mDeletedTable = deletedTable;
		mIdColumn = idColumn;
	}

	private PreparedStatement getDeleteStatement() throws SQLException
	{
		if (mDeleteStatement == null)
		{
			String sql = "DELETE FROM " + mMainTable + " WHERE " + mIdColumn + " = ?";
			mDeleteStatement = mConnection.prepareStatement(sql);
		}

		return mDeleteStatement;
	}

	private PreparedStatement getCheckLogStatement() throws SQLException
	{
		if (mCheckLogStatement == null)
		{
			String sql = "SELECT " + mIdColumn + " FROM " + mDeletedTable + " WHERE " + mIdColumn + " = ?";
			mCheckLogStatement = mConnection.prepareStatement(sql);
		}

		return mCheckLogStatement;
	}

	private InsertHelper getLogInserter() throws SQLException
	{
		if (mLogInserter == null)
			mLogInserter = new InsertHelper(mConnection, mDeletedTable, mIdColumn);

		return mLogInserter;
	}

	/**
	 * Called before setting up a record for deletion.
	 */
	public void prepareForDelete() throws SQLException
	{
		getDeleteStatement().clearParameters();
	}

	/**
	 * Set the id that is to be deleted for {@link #delete}.
	 */
	public void setId(long id) throws SQLException
	{
		mId = id;
	}

	public void delete(long id) throws SQLException
	{
		prepareForDelete();
		setId(id);
		delete();
	}

	private boolean existsInDeletedLog(long id) throws SQLException
	{
		PreparedStatement checkLog = getCheckLogStatement();
		checkLog.clearParameters();
		checkLog.setLong(1, mId);
		ResultSet set = checkLog.executeQuery();
		try {
			if (set.next())
				return true;
			return false;
		} finally {
			set.close();
		}
	}

	public void delete() throws SQLException
	{
		if (mId < 0)
			throw new IllegalStateException("You must call setId with a non-negative id before delete.");

		DatabaseUtils.beginTransaction(mConnection);
		try {
			deleteLocked();
			DatabaseUtils.setTransactionSuccessful(mConnection);
		} finally {
			DatabaseUtils.endTransaction(mConnection);
		}
	}

	private void deleteLocked() throws SQLException
	{
		long now = System.currentTimeMillis();

		mDeleteStatement.setLong(1, mId);
		mDeleteStatement.execute();

		InsertHelper logInserter = getLogInserter();

		if (existsInDeletedLog(mId))
			logInserter.prepareForReplace();
		else
			logInserter.prepareForInsert();

		logInserter.bind(mIdColumn, mId);
		logInserter.bind(Columns._SYNC_TIME, now);
		logInserter.execute();
	}
}
