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

package org.devtcg.five.meta;

import java.sql.Connection;
import java.sql.SQLException;

import org.devtcg.five.content.SyncAdapter;
import org.devtcg.five.meta.dao.AlbumDAO;
import org.devtcg.five.meta.dao.ArtistDAO;
import org.devtcg.five.meta.dao.PlaylistDAO;
import org.devtcg.five.meta.dao.PlaylistSongDAO;
import org.devtcg.five.meta.dao.SongDAO;
import org.devtcg.five.persistence.DatabaseOpenHelper;
import org.devtcg.five.persistence.LockableConnection;
import org.devtcg.five.persistence.SyncableProvider;

public class MetaProvider extends SyncableProvider
{
	private final DatabaseOpenHelper mHelper;

	private static final String DB_NAME = "meta";
	private static final int DB_VERSION = 12;

	private static final MetaProvider INSTANCE = new MetaProvider(DB_NAME);

	private ArtistDAO mArtistDAO;
	private AlbumDAO mAlbumDAO;
	private SongDAO mSongDAO;
	private PlaylistDAO mPlaylistDAO;
	private PlaylistSongDAO mPlaylistSongDAO;

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

	public synchronized ArtistDAO getArtistDAO()
	{
		if (mArtistDAO == null)
			mArtistDAO = new ArtistDAO(this);

		return mArtistDAO;
	}

	public synchronized AlbumDAO getAlbumDAO()
	{
		if (mAlbumDAO == null)
			mAlbumDAO = new AlbumDAO(this);

		return mAlbumDAO;
	}

	public synchronized SongDAO getSongDAO()
	{
		if (mSongDAO == null)
			mSongDAO = new SongDAO(this);

		return mSongDAO;
	}

	public synchronized PlaylistDAO getPlaylistDAO()
	{
		if (mPlaylistDAO == null)
			mPlaylistDAO = new PlaylistDAO(this);

		return mPlaylistDAO;
	}

	public synchronized PlaylistSongDAO getPlaylistSongDAO()
	{
		if (mPlaylistSongDAO == null)
			mPlaylistSongDAO = new PlaylistSongDAO(this);

		return mPlaylistSongDAO;
	}

	private class OpenHelper extends DatabaseOpenHelper
	{
		public OpenHelper(String name, int version)
		{
			super(name, version);
		}

		@Override
		public void onCreate(Connection conn) throws SQLException
		{
			getArtistDAO().createTables(conn);
			getAlbumDAO().createTables(conn);
			getSongDAO().createTables(conn);
			getPlaylistDAO().createTables(conn);
			getPlaylistSongDAO().createTables(conn);
		}

		@Override
		public void onUpgrade(Connection conn, int oldVersion, int newVersion)
			throws SQLException
		{
			getArtistDAO().dropTables(conn);
			getAlbumDAO().dropTables(conn);
			getSongDAO().dropTables(conn);
			getPlaylistDAO().dropTables(conn);
			getPlaylistSongDAO().dropTables(conn);

			getArtistDAO().createTables(conn);
			getAlbumDAO().createTables(conn);
			getSongDAO().createTables(conn);
			getPlaylistDAO().createTables(conn);
			getPlaylistSongDAO().createTables(conn);
		}
	}

	@Override
	public LockableConnection getConnection() throws SQLException
	{
		return mHelper.getConnection();
	}

	@Override
	public void close() throws SQLException
	{
		mHelper.close();
	}

	@Override
	public SyncAdapter<? extends SyncableProvider> getSyncAdapter()
	{
		return new MetaSyncAdapter(this);
	}
}
