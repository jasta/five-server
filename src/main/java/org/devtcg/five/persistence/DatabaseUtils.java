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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.devtcg.five.content.Cursor;

public class DatabaseUtils {
	private static PreparedStatement createPreparedStatement(Connection conn,
		String sql, String[] args) throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(sql);

		int n = args.length;
		for (int i = 0; i < n; i++)
			stmt.setString(i + 1, args[i]);

		return stmt;
	}

	public static void execute(Connection conn, String sql, String... args)
		throws SQLException
	{
		if (args == null || args.length == 0)
			conn.createStatement().execute(sql);
		else
		{
			PreparedStatement stmt = createPreparedStatement(conn, sql, args);
			stmt.execute();
		}
	}

	public static int update(Connection conn, String sql, String... args)
			throws SQLException
	{
		if (args == null || args.length == 0)
			return conn.createStatement().executeUpdate(sql);
		else
		{
			PreparedStatement stmt = createPreparedStatement(conn, sql, args);
			return stmt.executeUpdate();
		}
	}

	public static long getLastInsertId(Connection conn) throws SQLException
	{
		return longForQuery(conn, "CALL IDENTITY()");
	}

	public static ResultSet executeForResult(Connection conn,
		String query, String... args) throws SQLException
	{
		if (args == null || args.length == 0)
			return conn.createStatement().executeQuery(query);
		else
		{
			PreparedStatement stmt = createPreparedStatement(conn, query, args);
			return stmt.executeQuery();
		}
	}

	public static String stringForQuery(Connection conn, String defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			String result = stringForQuery(conn, query, args);
			return result != null ? result : defaultValue;
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static String stringForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false)
			throw new RowNotFoundException();

		return set.getString(1);
	}

	public static int integerForQuery(Connection conn, int defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return integerForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static int integerForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false || set.getString(1) == null)
			throw new RowNotFoundException();

		return set.getInt(1);
	}

	public static long longForQuery(Connection conn, long defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return longForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static long longForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false || set.getString(1) == null)
			throw new RowNotFoundException();

		return set.getLong(1);
	}

	public static boolean booleanForQuery(Connection conn, boolean defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return booleanForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static boolean booleanForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false || set.getString(1) == null)
			throw new RowNotFoundException();

		return set.getBoolean(1);
	}

	public static void cursorStringToHelper(String columnName,
		Cursor cursor, InsertHelper helper) throws SQLException
	{
		String result = cursor.getResultSet().getString(cursor.getColumnIndex(columnName));
		helper.bind(columnName, result);
	}

	/**
	 * Implements semantics similar to INSERT OR REPLACE. Abstracted in this way
	 * to support database engines that do not understand this syntax.
	 */
	public static void insertOrReplace(Connection conn, String table, String primaryKey,
			String primaryKeyValue, String columnName, String value) throws SQLException
	{
		int rows = update(conn, "UPDATE " + table + " SET " + columnName + " = ? " +
				"WHERE " + primaryKey + " = ?", value, primaryKeyValue);
		if (rows == 0)
		{
			execute(conn, "INSERT INTO " + table + " (" + primaryKey + ", " + columnName + ") " +
					"VALUES (?, ?)", primaryKeyValue, value);
		}
	}

	public static class RowNotFoundException extends SQLException
	{
		public RowNotFoundException()
		{
			super();
		}

		public RowNotFoundException(String message)
		{
			super(message);
		}
	}
}
