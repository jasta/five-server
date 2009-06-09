package org.devtcg.five.meta;

import java.sql.Connection;
import java.sql.SQLException;

import org.devtcg.five.persistence.DatabaseOpenHelper;
import org.devtcg.five.persistence.LockableConnection;
import org.devtcg.five.persistence.Provider;

public class MetaProvider extends Provider
{
	private final DatabaseOpenHelper mHelper;

	private static final String DB_NAME = "meta";
	private static final int DB_VERSION = 1;

	private static final MetaProvider INSTANCE = new MetaProvider(DB_NAME);

	protected MetaProvider(String name)
	{
		mHelper = new OpenHelper(name, DB_VERSION);
	}

	public static MetaProvider getInstance()
	{
		return INSTANCE;
	}

	public static MetaProvider getTemporaryInstance()
	{
		return new MetaProvider(null);
	}

	private static class OpenHelper extends DatabaseOpenHelper
	{
		public OpenHelper(String name, int version)
		{
			super(name, version);
		}

		@Override
		public void onCreate(Connection conn) throws SQLException
		{
		}

		@Override
		public void onUpgrade(Connection conn, int oldVersion, int newVersion)
			throws SQLException
		{
		}
	}

	@Override
	public LockableConnection getConnection() throws SQLException
	{
		return mHelper.getConnection();
	}

//	public long insertArtist(String name) throws SQLException
//	{
//		LockableConnection conn = mHelper.getConnection();
//
//		conn.lock();
//		try {
//			DatabaseUtils.execute(conn, "INSERT INTO artists (name) VALUES (?)");
//			return DatabaseUtils.getLastInsertId(conn);
//		} finally {
//			conn.unlock();
//		}
//	}
//
//	public void removeArtist(long id) throws SQLException
//	{
//		LockableConnection conn = mHelper.getConnection();
//
//		conn.lock();
//		try {
//			DatabaseUtils.execute(conn, "DELETE FROM artists WHERE id = ?",
//				String.valueOf(id));
//		} finally {
//			conn.unlock();
//		}
//	}
//
//	public void updateArtist(long id, String name) throws SQLException
//	{
//		LockableConnection conn = mHelper.getConnection();
//
//		conn.lock();
//		try {
//			DatabaseUtils.execute(conn, "UPDATE artists SET name = ? WHERE id = ?",
//				name, String.valueOf(id));
//		} finally {
//			conn.unlock();
//		}
//	}
}
