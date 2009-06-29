package org.devtcg.five.meta;

import java.sql.Connection;
import java.sql.SQLException;

import org.devtcg.five.meta.dao.AlbumDAO;
import org.devtcg.five.meta.dao.ArtistDAO;
import org.devtcg.five.meta.dao.SongDAO;
import org.devtcg.five.persistence.DatabaseOpenHelper;
import org.devtcg.five.persistence.LockableConnection;
import org.devtcg.five.persistence.Provider;

public class MetaProvider extends Provider
{
	private final DatabaseOpenHelper mHelper;

	private static final String DB_NAME = "meta";
	private static final int DB_VERSION = 5;

	private static final MetaProvider INSTANCE = new MetaProvider(DB_NAME);

	private SongDAO mSongDAO;
	private ArtistDAO mArtistDAO;
	private AlbumDAO mAlbumDAO;

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

	public synchronized SongDAO getSongDAO()
	{
		if (mSongDAO == null)
			mSongDAO = new SongDAO(this);

		return mSongDAO;
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

//	/**
//	 * Links artist and album photos.
//	 */
//	public interface ImageColumns extends BaseColumns
//	{
//		/** Table where {@link #TABLE_ID} is found. */
//		public static final String TABLE = "table";
//
//		public static final String TABLE_ID = "table_id";
//
//		public static final String DATA = "data";
//
//		public static final String WIDTH = "width";
//
//		public static final String HEIGHT = "height";
//
//		/** Timestamp this row was created (i.e. when Five first noticed). */
//		public static final String DISCOVERY_DATE = "discovery_date";
//	}
//
//	public interface PlaylistColumns extends BaseColumns
//	{
//		public static final String NAME = "name";
//
//		/** Filename on disk (if applicable). */
//		public static final String FILENAME = "filename";
//
//		public static final String CREATED_DATE = "created_date";
//
//		/** Flag used to detect deleted files during a sweep. */
//		public static final String MARK = "mark";
//	}

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
		}

		@Override
		public void onUpgrade(Connection conn, int oldVersion, int newVersion)
			throws SQLException
		{
			getArtistDAO().dropTables(conn);
			getAlbumDAO().dropTables(conn);
			getSongDAO().dropTables(conn);

			getArtistDAO().createTables(conn);
			getAlbumDAO().createTables(conn);
			getSongDAO().createTables(conn);
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
