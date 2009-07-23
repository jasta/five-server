package org.devtcg.five.meta.dao;

import java.sql.Connection;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger.SyncableColumns;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;

public abstract class AbstractDAO
{
	protected final Provider mProvider;

	private InsertHelper mInserter;

	public AbstractDAO(Provider provider)
	{
		mProvider = provider;
	}

	public interface BaseColumns extends SyncableColumns
	{
		/* Nothing to add... */
	}

	public Provider getProvider()
	{
		return mProvider;
	}

	public abstract void createTables(Connection conn) throws SQLException;
	public abstract void dropTables(Connection conn) throws SQLException;

	protected abstract String getTable();

	protected synchronized InsertHelper getInsertHelper() throws SQLException
	{
		if (mInserter == null)
			mInserter = new InsertHelper(mProvider.getConnection(), getTable());

		return mInserter;
	}
}
