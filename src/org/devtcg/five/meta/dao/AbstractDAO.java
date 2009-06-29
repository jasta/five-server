package org.devtcg.five.meta.dao;

import java.sql.Connection;
import java.sql.SQLException;

import org.devtcg.five.persistence.Provider;

public abstract class AbstractDAO
{
	protected final Provider mProvider;

	public AbstractDAO(Provider provider)
	{
		mProvider = provider;
	}

	public interface BaseColumns
	{
		/** Autoincrement id. */
		public static final String _ID = "_id";

		/** Timestamp of last modification. */
		public static final String _SYNC_TIME = "_sync_time";
	}

	public Provider getProvider()
	{
		return mProvider;
	}

	public abstract void createTables(Connection conn) throws SQLException;
	public abstract void dropTables(Connection conn) throws SQLException;
}
