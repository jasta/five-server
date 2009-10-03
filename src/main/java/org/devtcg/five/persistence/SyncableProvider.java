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

import java.sql.SQLException;

import org.devtcg.five.content.SyncAdapter;

public abstract class SyncableProvider extends Provider
{
	public void merge(SyncableProvider diffs) throws SQLException
	{
		throw new UnsupportedOperationException();
//		Connection conn = getConnection();
//		conn.setAutoCommit(false);
//		try {
//			Iterable<? extends AbstractTableMerger> mergers = getMergers();
//			for (AbstractTableMerger merger: mergers)
//				merger.merge(diffs, this);
//
//			conn.commit();
//		} catch (SQLException e) {
//			conn.rollback();
//			throw e;
//		} finally {
//			conn.setAutoCommit(true);
//		}
	}

//	protected Iterable<? extends AbstractTableMerger> getMergers()
//	{
//		return Collections.emptyList();
//	}

	public SyncAdapter<? extends SyncableProvider> getSyncAdapter()
	{
		return null;
	}
}
