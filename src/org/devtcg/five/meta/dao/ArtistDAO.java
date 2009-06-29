package org.devtcg.five.meta.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.util.StringUtils;
import org.devtcg.five.util.TimeUtils;

public class ArtistDAO extends AbstractDAO
{
	private static final String TABLE = "artists";

	private InsertHelper mInserter;

	public interface Columns extends BaseColumns
	{
		/** MusicBrainz ID. */
		public static final String MBID = "mbid";

		public static final String NAME = "name";

		/** Canonicalized name used for comparison. Excludes articles,
		 *  punctuation, etc. */
		public static final String NAME_MATCH = "name_match";

		/** Timestamp this row was created (i.e. when Five first noticed). */
		public static final String DISCOVERY_DATE = "discovery_date";
	}

	public ArtistDAO(Provider provider)
	{
		super(provider);
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " INTEGER, " +
			Columns.MBID + " CHAR(36), " +
			Columns.NAME + " VARCHAR NOT NULL, " +
			Columns.NAME_MATCH + " VARCHAR NOT NULL, " +
			Columns.DISCOVERY_DATE + " INTEGER, " +
			"UNIQUE (" + Columns.NAME_MATCH + ") " +
		")");
	}

	@Override
	public void dropTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "DROP TABLE IF EXISTS " + TABLE);
	}

	public Artist getArtist(String name) throws SQLException
	{
		ResultSet set = DatabaseUtils.executeForResult(mProvider.getConnection(),
			"SELECT * FROM " + TABLE + " WHERE " + Columns.NAME_MATCH + " = ?",
			name);

		try {
			if (set.next() == false)
				return null;

			return new Artist(set);
		} finally {
			if (set != null)
				set.close();
		}
	}

	public long insert(String name) throws SQLException
	{
		synchronized (this) {
			if (mInserter == null)
			{
				mInserter = new InsertHelper(mProvider.getConnection(),
					"INSERT INTO " + TABLE + "(" +
						Columns._SYNC_TIME + ", " +
						Columns.NAME + ", " +
						Columns.NAME_MATCH + ", " +
						Columns.DISCOVERY_DATE + ") " +
					"VALUES (?, ?, ?, ?)");
			}
		}

		long now = TimeUtils.getUnixTimestamp();
		return mInserter.insert(now, name, StringUtils.getNameMatch(name), now);
	}

	public class Artist
	{
		public long _id;
		public String mbid;
		public String name;
		public String nameMatch;
		public long discoveryDate;

		private Artist() {}

		private Artist(ResultSet set) throws SQLException
		{
			ResultSetMetaData meta = set.getMetaData();
			int n = meta.getColumnCount();

			for (int i = 1; i <= n; i++)
			{
				String columnName = meta.getColumnName(i);
				if (columnName.equals(Columns._ID))
					_id = set.getLong(i);
				else if (columnName.equals(Columns.MBID))
					mbid = set.getString(i);
				else if (columnName.equals(Columns.NAME))
					name = set.getString(i);
				else if (columnName.equals(Columns.NAME_MATCH))
					nameMatch = set.getString(i);
				else if (columnName.equals(Columns.DISCOVERY_DATE))
					discoveryDate = set.getLong(i);
			}
		}
	}
}
