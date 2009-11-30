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
	private static final File FIVE_HOME;
	private static final File DATABASE_DIR;

	private static final int DEFAULT_PORT = 5545;

	private static final String DB_NAME = "prefs";
	private static final int DB_VERSION = 3;

	private final DatabaseOpenHelper mDatabase;

	static {
		String home = System.getProperty("user.home");
		if (StringUtils.isEmpty(home))
			home = System.getenv("HOME");

		USER_HOME = new File(home);

		FIVE_HOME = new File(home, ".five");
		ensureDirectoryExists(FIVE_HOME);

		DATABASE_DIR = new File(FIVE_HOME, "databases");
		ensureDirectoryExists(DATABASE_DIR);
	}

	private static void ensureDirectoryExists(File path)
	{
		if (!path.exists())
		{
			if (path.mkdirs())
				return;
		}
		else
		{
			if (path.isDirectory() && path.canWrite())
				return;
		}

		throw new RuntimeException("Cannot access or create " + FIVE_HOME);
	}

	private Configuration()
	{
		mDatabase = new OpenHelper();
	}

	private interface Columns
	{
		public static final String FIRST_TIME = "first_time";

		public static final String LIBRARY_PATH = "library_path";

		public static final String PORT = "port";

		public static final String PASSWORD = "password";

		public static final String USE_UPNP = "use_upnp";
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
				Columns.FIRST_TIME + " BOOLEAN NOT NULL, " +
				Columns.LIBRARY_PATH + " VARCHAR DEFAULT NULL, " +
				Columns.PORT + " INTEGER DEFAULT NULL, " +
				Columns.PASSWORD + " VARCHAR DEFAULT NULL, " +
				Columns.USE_UPNP + " BOOLEAN DEFAULT NULL" +
				")");
			DatabaseUtils.execute(conn, "INSERT INTO configuration (" +
				Columns.FIRST_TIME +
				") VALUES (?)", new String[] { "TRUE" });
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
		return FIVE_HOME.getAbsolutePath();
	}

	public static String getHomePath()
	{
		return USER_HOME.getAbsolutePath();
	}

	/**
	 * Initializes configured settings after the setup wizard exists successfully.
	 */
	public synchronized void initFirstTime(String libraryPath, String password,
			boolean useUPnP) throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		DatabaseUtils.execute(conn, "UPDATE configuration SET " +
				Columns.FIRST_TIME + " = ?, " +
				Columns.LIBRARY_PATH + " = ?, " +
				Columns.PASSWORD + " = ?, " +
				Columns.USE_UPNP + " = ?",
			"FALSE", libraryPath, password, useUPnP ? "TRUE" : "FALSE");
	}

	public synchronized boolean isFirstTime() throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		return DatabaseUtils.booleanForQuery(conn, true,
			"SELECT " + Columns.FIRST_TIME + " FROM configuration");
	}

	public synchronized int getServerPort() throws SQLException
	{
		Connection conn = mDatabase.getConnection().getWrappedConnection();

		return DatabaseUtils.integerForQuery(conn, DEFAULT_PORT,
				"SELECT " + Columns.PORT + " FROM configuration");
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
