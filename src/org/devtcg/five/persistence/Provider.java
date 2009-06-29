package org.devtcg.five.persistence;

import java.sql.SQLException;

public abstract class Provider
{
	public abstract LockableConnection getConnection() throws SQLException;

	public void lock() throws SQLException
	{
		getConnection().lock();
	}

	public void unlock() throws SQLException
	{
		getConnection().unlock();
	}

	public void yieldIfContended() throws SQLException
	{
		getConnection().yieldIfContended();
	}

	public void close() throws SQLException
	{
		getConnection().close();
	}
}
