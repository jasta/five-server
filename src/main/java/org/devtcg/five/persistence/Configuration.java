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

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.util.StringUtils;

/**
 * Centralized access to all server preferences.
 */
public class Configuration
{
	private static final Log LOG = LogFactory.getLog(Configuration.class);

	private static class ConfigurationHolder
	{
		private static final Configuration INSTANCE = new Configuration();
	}

	private static final File USER_HOME;
	private static final File DATABASE_DIR;

	private static final int DEFAULT_PORT = 5546;

	private static final String DB_NAME = "prefs";
	private static final int DB_VERSION = 1;

	private final DatabaseOpenHelper mDatabase;

	static {
		String home = System.getProperty("user.home");
		if (StringUtils.isEmpty(home))
			home = System.getenv("HOME");

		USER_HOME = new File(home, ".five");
		if (USER_HOME.isDirectory() == false || USER_HOME.canWrite() == false)
			throw new RuntimeException("Cannot access " + USER_HOME);

		DATABASE_DIR = new File(USER_HOME, "databases");
	}

	private Configuration()
	{
		mDatabase = new OpenHelper();
	}

	private interface Columns
	{
		public static final String FIRST_TIME = "first_time";

		public static final String LIBRARY_PATH = "library_path";
	}

	private static class OpenHelper extends DatabaseOpenHelper
	{
		public OpenHelper()
		{
			super(DB_NAME, DB_VERSION);
		}

		@Override
		public void onCreate(Connection conn) throws SQLException
		{
			DatabaseUtils.execute(conn, "CREATE TABLE configuration (" +
				Columns.FIRST_TIME + " BOOLEAN, " +
				Columns.LIBRARY_PATH + " VARCHAR " +
				")");
			DatabaseUtils.execute(conn, "INSERT INTO configuration (" +
				Columns.FIRST_TIME + ", " +
				Columns.LIBRARY_PATH +
				") VALUES (?, ?)", new String[] { "TRUE", null });
		}

		private void onDrop(Connection conn) throws SQLException
		{
			DatabaseUtils.execute(conn, "DROP TABLE configuration");
		}

		@Override
		public void onUpgrade(Connection conn, int oldVersion, int newVersion)
			throws SQLException
		{
			if (LOG.isInfoEnabled())
			{
				LOG.info("Upgrading table from version " + oldVersion +
					" to version " + newVersion);
			}

			onDrop(conn);
			onCreate(conn);
		}
	}

	public static Configuration getInstance()
	{
		return ConfigurationHolder.INSTANCE;
	}

	public static String getDatabasePath(String databaseName)
	{
		return (new File(DATABASE_DIR, databaseName)).getAbsolutePath();
	}

	public static String getStoragePath()
	{
		return USER_HOME.getAbsolutePath();
	}

	/**
	 * @deprecated
	 */
	public synchronized void setDebugConfiguration() throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		DatabaseUtils.execute(conn, "UPDATE configuration SET " +
				Columns.FIRST_TIME + " = ?, " +
				Columns.LIBRARY_PATH + " = ?",
			"0", "/music/A");
	}

	public synchronized boolean isFirstTime() throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		return DatabaseUtils.booleanForQuery(conn, true,
			"SELECT " + Columns.FIRST_TIME + " FROM configuration");
	}

	public synchronized int getServerPort()
	{
		return DEFAULT_PORT;
	}

	public synchronized List<String> getLibraryPaths() throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		String pathsValue = DatabaseUtils.stringForQuery(conn,
			"SELECT " + Columns.LIBRARY_PATH + " FROM configuration");

		ArrayList<String> paths = new ArrayList<String>();

		if (pathsValue != null)
		{
			for (String path: pathsValue.split(":"))
				paths.add(path);
		}

		return paths;
	}
}
