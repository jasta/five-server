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
import java.util.HashMap;

public class InsertHelper
{
	private final Connection mConnection;
	private final String mTableName;
	private final String mIdColumn;

	private PreparedStatement mReplaceStatement;
	private PreparedStatement mInsertStatement;
	private PreparedStatement mPreparedStatement;

	private HashMap<String, Integer> mColumnsMap =
		new HashMap<String, Integer>();

	public InsertHelper(Connection conn, String table)
	{
		this(conn, table, "_id");
	}

	public InsertHelper(Connection conn, String table, String idColumn)
	{
		mConnection = conn;
		mTableName = table;
		mIdColumn = idColumn;
	}

	private String buildSQL(boolean isInsert) throws SQLException
	{
		StringBuilder columns = new StringBuilder(128);
		if (isInsert)
			columns.append("INSERT INTO ").append(mTableName).append(" (");
		else
			columns.append("UPDATE ").append(mTableName).append(" SET ");

		StringBuilder values;

		if (isInsert)
		{
			values = new StringBuilder(64);
			values.append("VALUES (");
		}
		else
		{
			/*
			 * This little gem is here to allow us to keep the code tidy in the
			 * loop below. The way we add value clauses is the same for insert
			 * and update, they just are placed in different locations.
			 */
			values = columns;
		}

		int i = 1;
		mColumnsMap.clear();

		ResultSet columnsSet = mConnection.getMetaData().getColumns(null, null,
			mTableName.toUpperCase(), null);
		try {
			int columnNameIdx = columnsSet.findColumn("COLUMN_NAME");
			int columnDefIdx = columnsSet.findColumn("COLUMN_DEF");

			while (columnsSet.next() == true)
			{
				String columnName = columnsSet.getString(columnNameIdx);
				String columnDef = columnsSet.getString(columnDefIdx);

				/*
				 * Ignore the id column, we'll make sure it gets added at the
				 * end of the statement always. This is to keep parameter
				 * indices aligned for both insert and update statements.
				 */
				if (columnName.equalsIgnoreCase(mIdColumn))
					continue;

				mColumnsMap.put(columnName.toLowerCase(), i);

				if (isInsert)
					columns.append(columnName);
				else
					columns.append(columnName).append(" = ");

				if (columnDef == null)
					values.append('?');
				else
					values.append("COALESCE(?, ").append(columnDef).append(')');

				columns.append(", ");

				if (isInsert)
					values.append(", ");

				i++;
			}

			if (i == 1)
				throw new IllegalArgumentException("Table " + mTableName + " contains no rows?");
		} finally {
			columnsSet.close();
		}

		mColumnsMap.put(mIdColumn, i);

		if (isInsert)
		{
			columns.append(mIdColumn).append(") ");
			values.append("?)");

			return columns.append(values).toString();
		}
		else
		{
			columns.replace(columns.length() - 2, columns.length(),
				" WHERE ");
			columns.append(mIdColumn);
			columns.append(" = ?");

			return columns.toString();
		}
	}

	private PreparedStatement getInsertStatement() throws SQLException
	{
		if (mInsertStatement == null)
		{
			String sql = buildSQL(true);
			mInsertStatement = mConnection.prepareStatement(sql);
		}

		return mInsertStatement;
	}

	private PreparedStatement getReplaceStatement() throws SQLException
	{
		if (mReplaceStatement == null)
		{
			String sql = buildSQL(false);
			mReplaceStatement = mConnection.prepareStatement(sql);
		}

		return mReplaceStatement;
	}

	private int getColumnIndex(String columnName)
	{
		Integer index = mColumnsMap.get(columnName);
		if (index == null)
			throw new IllegalArgumentException("Column " + columnName + " not found");

		return index;
	}

	public void bind(String columnName, Object value) throws SQLException
	{
		bind(getColumnIndex(columnName), value);
	}

	public void bind(int index, Object value) throws SQLException
	{
		if (value == null)
			mPreparedStatement.setNull(index, java.sql.Types.NULL);
		else if (value instanceof String)
			mPreparedStatement.setString(index, (String)value);
		else if (value instanceof Integer)
			mPreparedStatement.setInt(index, (Integer)value);
		else if (value instanceof Long)
			mPreparedStatement.setLong(index, (Long)value);
		else if (value instanceof Boolean)
			mPreparedStatement.setBoolean(index, (Boolean)value);
		else
			throw new IllegalArgumentException("Type not supported yet.");
	}

	public void prepareForInsert() throws SQLException
	{
		mPreparedStatement = getInsertStatement();
		mPreparedStatement.clearParameters();
	}

	public void prepareForReplace() throws SQLException
	{
		mPreparedStatement = getReplaceStatement();
		mPreparedStatement.clearParameters();
	}

	/** @deprecated */
	public void setArguments(Object... args) throws SQLException
	{
		int n = args.length;
		for (int i = 0; i < n; i++)
			bind(i + 1, args[i]);
	}

	/** deprecated */
	public void execute(Object... args) throws SQLException
	{
		setArguments(args);
		execute();
	}

	/** deprecated */
	public long insert(Object... args) throws SQLException
	{
		execute(args);
		return DatabaseUtils.getLastInsertId(mConnection);
	}

	public long insert() throws SQLException
	{
		if (mPreparedStatement != mInsertStatement)
			throw new IllegalStateException("Must call prepareForInsert prior to insert");

		execute();
		return DatabaseUtils.getLastInsertId(mConnection);
	}

	public void execute() throws SQLException
	{
		mPreparedStatement.execute();
	}

	public void close() throws SQLException
	{
		if (mInsertStatement != null)
		{
			mInsertStatement.close();
			mInsertStatement = null;
		}

		if (mReplaceStatement != null)
		{
			mReplaceStatement.close();
			mReplaceStatement = null;
		}

		mColumnsMap = null;
	}
}
