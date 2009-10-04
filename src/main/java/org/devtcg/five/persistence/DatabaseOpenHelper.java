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
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.util.StringUtils;

public abstract class DatabaseOpenHelper
{
	private static final Log LOG = LogFactory.getLog(DatabaseOpenHelper.class);

	static {
		try {
			Class.forName("org.hsqldb.jdbcDriver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private final String mName;
	private final String mUri;
	private final int mVersion;

	private LockableConnection mConnection;

	/* Used only to generate temporary database path names. */
	private static SecureRandom mRandom;

	/**
	 * @param name Database filename, or null to allocate temporary storage.
	 * @param version Expected version number of this database's schema.
	 */
	public DatabaseOpenHelper(String name, int version)
	{
		if (version <= 0)
			throw new IllegalArgumentException("version must be greater than 0");

		mName = name;
		mVersion = version;
		mUri = makeDatabaseUri(name);
	}

	private static String makeDatabaseUri(String name)
	{
		if (name != null)
			return "file:" + Configuration.getDatabasePath(name);
		else
		{
			/**
			 * System.currentTimeMillis() is used to work around particularly
			 * stupid behaviour in HSQLDB where memory databases are tracked
			 * within the engine and returned to the caller by name.
			 *
			 * We need semantics that allow us to reliably create a new
			 * in-memory database instance on demand, not potentially re-use an
			 * existing one we have open.
			 *
			 * There is little chance of contention in using
			 * System.currentTimeMillis() based on our design, but in the future
			 * we may need to look at a properly synchronized approach to ensure
			 * we never pass the same alias twice.
			 */
			return "mem:" + System.currentTimeMillis();
		}
	}

	private int getVersion(Connection conn) throws SQLException
	{
		try {
			return DatabaseUtils.integerForQuery(conn, 0,
				"SELECT version FROM _meta");
		} catch (SQLException e) {
			return 0;
		}
	}

	private void setVersion(Connection conn, int version, boolean firstTime)
		throws SQLException
	{
		if (firstTime == true)
		{
			DatabaseUtils.execute(conn, "CREATE TABLE _meta (version INTEGER)");
			DatabaseUtils.execute(conn, "INSERT INTO _meta (version) VALUES (" + version + ")");
		}
		else
		{
			DatabaseUtils.execute(conn, "UPDATE _meta SET version = " + version);
		}
	}

	private static String generateTemporaryPath()
	{
		synchronized (DatabaseOpenHelper.class) {
			if (mRandom == null)
				mRandom = new SecureRandom();
		}

		byte[] suffixBytes = new byte[16];
		String path;

		for (int i = 0;; i++)
		{
			mRandom.nextBytes(suffixBytes);
			String file = "tmp-" + StringUtils.byteArrayToHexString(suffixBytes);
			path = Configuration.getDatabasePath(file + File.separator + "database");

			if ((new File(path)).exists() == false)
				return path;

			if (i > 100)
			{
				String msg = "Unable to acquire temporary storage within a reasonable number of tries.";
				if (LOG.isErrorEnabled())
				{
					LOG.error(msg + "  Please check that " + Configuration.getDatabasePath("") +
						" is not full of temporary files.");
				}

				throw new RuntimeException(msg);
			}
		}
	}

	public boolean isTemporary()
	{
		return mName == null;
	}

	public synchronized LockableConnection getConnection() throws SQLException
	{
		if (mConnection != null && mConnection.isClosed() == false)
			return mConnection;

		Connection conn = DriverManager.getConnection("jdbc:hsqldb:" + mUri,
			"sa", "");

		int version = getVersion(conn);

		if (version != mVersion)
		{
			conn.setAutoCommit(false);

			try {
				if (version == 0)
				{
					onCreate(conn);
					setVersion(conn, mVersion, true);
				}
				else if (version != mVersion)
				{
					onUpgrade(conn, version, mVersion);
					setVersion(conn, mVersion, false);
				}
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			}

			conn.commit();
			conn.setAutoCommit(true);
		}

		mConnection = new LockableConnection(conn);

		return mConnection;
	}

	public synchronized void close() throws SQLException
	{
		if (mConnection != null)
		{
			mConnection.lock();
			try {
				try {
					DatabaseUtils.execute(mConnection, "SHUTDOWN");
				} finally {
					mConnection.close();
				}
			} finally {
				mConnection.unlock();
				mConnection = null;
			}
		}
	}

	@Override
	protected void finalize()
	{
		try {
			close();
		} catch (SQLException e) {}
	}

	public abstract void onCreate(Connection conn) throws SQLException;

	public abstract void onUpgrade(Connection conn, int oldVersion, int newVersion)
		throws SQLException;
}
