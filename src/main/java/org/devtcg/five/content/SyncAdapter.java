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

package org.devtcg.five.content;

import org.devtcg.five.persistence.SyncableProvider;

public abstract class SyncAdapter<T extends SyncableProvider>
{
	protected volatile T mProvider;

	public SyncAdapter(T provider)
	{
		mProvider = provider;
	}

//	public abstract void getServerDiffs(SyncableProvider tempProvider);
//	public abstract void sendClientDiffs(SyncableProvider clientDiffs,
//		SyncableProvider serverDiffs);
}
