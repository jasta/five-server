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
import java.security.MessageDigest;
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
	private static final int DB_VERSION = 4;

	private static final String TABLE = "config";

	private final DatabaseOpenHelper mDatabase;

	private static final String VALUE_QUERY =
		"SELECT " + Columns.VALUE + " FROM " + TABLE + " WHERE " + Columns.KEY + " = ?";

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
		public static final String KEY = "key";
		public static final String VALUE = "value";
	}

	public interface Keys
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
			DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
					Columns.KEY + " VARCHAR PRIMARY KEY, " +
					Columns.VALUE + " VARCHAR NOT NULL " +
					")");
		}

		private void onDrop(Connection conn) throws SQLException
		{
			DatabaseUtils.execute(conn, "DROP TABLE " + TABLE);
		}

		@Override
		public void onUpgrade(Connection conn, int oldVersion, int newVersion)
			throws SQLException
		{
			if (LOG.isInfoEnabled())
			{
				LOG.info("Upgrading database from version " + oldVersion +
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

	public static String sha1Hash(String data)
	{
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(data.getBytes("UTF-8"));
			return StringUtils.byteArrayToHexString(md.digest());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Initializes configured settings after the setup wizard exists successfully.
	 */
	public synchronized void initFirstTime(String libraryPath, String plaintextPassword,
			boolean useUPnP) throws SQLException
	{
		Connection conn = getConnection();

		setValue(conn, Keys.FIRST_TIME, "FALSE");
		setValue(conn, Keys.LIBRARY_PATH, libraryPath);
		setValue(conn, Keys.PASSWORD, sha1Hash(plaintextPassword));
		setValue(conn, Keys.USE_UPNP, useUPnP ? "TRUE" : "FALSE");
	}

	private static void setValue(Connection conn, String key, String value) throws SQLException
	{
		DatabaseUtils.insertOrReplace(conn, TABLE, Columns.KEY, key, Columns.VALUE, value);
	}

	private Connection getConnection() throws SQLException
	{
		return mDatabase.getConnection().getWrappedConnection();
	}

	public synchronized String getHashedPassword() throws SQLException
	{
		return DatabaseUtils.stringForQuery(getConnection(),
				VALUE_QUERY, new String[] { Keys.PASSWORD });
	}

	public synchronized void setPassword(String plaintextPassword) throws SQLException
	{
		setHashedPassword(sha1Hash(plaintextPassword));
	}

	public synchronized void setHashedPassword(String hashedPassword) throws SQLException
	{
		setValue(getConnection(), Keys.PASSWORD, hashedPassword);
	}

	public synchronized boolean useUPnP() throws SQLException
	{
		return DatabaseUtils.booleanForQuery(getConnection(), true,
				VALUE_QUERY, Keys.USE_UPNP);
	}

	public synchronized void setUseUPnP(boolean useUPnP) throws SQLException
	{
		setValue(getConnection(), Keys.USE_UPNP, useUPnP ? "TRUE" : "FALSE");
	}

	public synchronized boolean isFirstTime() throws SQLException
	{
		return DatabaseUtils.booleanForQuery(getConnection(), true,
				VALUE_QUERY, Keys.FIRST_TIME);
	}

	public synchronized int getServerPort() throws SQLException
	{
		return DatabaseUtils.integerForQuery(getConnection(), DEFAULT_PORT,
				VALUE_QUERY, Keys.PORT);
	}

	public synchronized void setServerPort(int port) throws SQLException
	{
		setValue(getConnection(), Keys.PORT, String.valueOf(port));
	}

	public synchronized List<String> getLibraryPaths() throws SQLException
	{
		String pathsValue = DatabaseUtils.stringForQuery(getConnection(),
			VALUE_QUERY, new String[] { Keys.LIBRARY_PATH });

		ArrayList<String> paths = new ArrayList<String>();

		if (pathsValue != null)
		{
			for (String path: pathsValue.split(File.pathSeparator))
				paths.add(path);
		}

		return paths;
	}

	public synchronized void setLibraryPaths(List<String> paths) throws SQLException
	{
		if (paths == null || paths.size() == 0)
			throw new IllegalArgumentException("paths must not be null or empty");

		StringBuilder string = new StringBuilder();
		for (String path: paths)
			string.append(path).append(File.pathSeparator);

		string.setLength(string.length() - File.pathSeparator.length());

		setValue(getConnection(), Keys.LIBRARY_PATH, string.toString());
	}
}
