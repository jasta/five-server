package org.devtcg.five.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import org.devtcg.five.content.AbstractTableMerger;

public abstract class SyncableProvider extends Provider
{
	public void merge(SyncableProvider diffs) throws SQLException
	{
		Connection conn = getConnection();
		conn.setAutoCommit(false);
		try {
			Iterable<? extends AbstractTableMerger> mergers = getMergers();
			for (AbstractTableMerger merger: mergers)
				merger.merge(diffs, this);

			conn.commit();
		} catch (SQLException e) {
			conn.rollback();
			throw e;
		} finally {
			conn.setAutoCommit(true);
		}
	}

	protected Iterable<? extends AbstractTableMerger> getMergers()
	{
		return Collections.emptyList();
	}
}
