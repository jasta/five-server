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

	private static synchronized String generateTemporaryPath()
	{
		if (mRandom == null)
			mRandom = new SecureRandom();

		byte[] suffixBytes = new byte[16];
		String path;

		for (int i = 0;; i++)
		{
			mRandom.nextBytes(suffixBytes);
			String file = "tmp-" + StringUtils.byteArrayToHexString(suffixBytes);
			path = Configuration.getDatabasePath(file + File.separator + "database");

			if ((new File(path)).exists() == false)
				break;

			if (i > 100)
			{
				if (LOG.isErrorEnabled())
					LOG.error("Unable to acquire temporary storage within a reasonable number of tries.  Please check that " + Configuration.getDatabasePath("") + " is not full of temporary files.");
			}
		}

		return path;
	}

	public boolean isTemporary()
	{
		return mName == null;
	}

	private String getDatabaseUri()
	{
		if (mName != null)
			return "file:" + Configuration.getDatabasePath(mName);
		else
			return "mem:.";
	}

	public synchronized LockableConnection getConnection() throws SQLException
	{
		if (mConnection != null && mConnection.isClosed() == false)
			return mConnection;

		String databaseUri = getDatabaseUri();
		Connection conn = DriverManager.getConnection("jdbc:hsqldb:" + databaseUri,
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
					if (isTemporary() == false)
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
