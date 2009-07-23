package org.devtcg.five.meta.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.devtcg.five.content.AbstractTableMerger;
import org.devtcg.five.content.Cursor;
import org.devtcg.five.meta.MetaProvider;
import org.devtcg.five.persistence.DatabaseUtils;
import org.devtcg.five.persistence.InsertHelper;
import org.devtcg.five.persistence.Provider;
import org.devtcg.five.util.StringUtils;
import org.devtcg.five.util.TimeUtils;

public class ArtistDAO extends AbstractDAO
{
	private static final String TABLE = "artists";

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
	protected String getTable()
	{
		return TABLE;
	}

	@Override
	public void createTables(Connection conn) throws SQLException
	{
		DatabaseUtils.execute(conn, "CREATE TABLE " + TABLE + " (" +
			Columns._ID + " INTEGER IDENTITY, " +
			Columns._SYNC_TIME + " INTEGER, " +
			Columns._SYNC_ID + " VARCHAR, " +
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
		InsertHelper helper = getInsertHelper();

		helper.prepareForInsert();

		long now = TimeUtils.getUnixTimestamp();
		helper.bind(Columns._SYNC_TIME, now);
		helper.bind(Columns.NAME, name);
		helper.bind(Columns.NAME_MATCH, StringUtils.getNameMatch(name));
		helper.bind(Columns.DISCOVERY_DATE, now);

		return helper.insert();
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
				if (columnName.equalsIgnoreCase(Columns._ID))
					_id = set.getLong(i);
				else if (columnName.equalsIgnoreCase(Columns.MBID))
					mbid = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.NAME))
					name = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.NAME_MATCH))
					nameMatch = set.getString(i);
				else if (columnName.equalsIgnoreCase(Columns.DISCOVERY_DATE))
					discoveryDate = set.getLong(i);
			}
		}
	}

	public static class TableMerger extends AbstractTableMerger
	{
		public TableMerger()
		{
			super(TABLE);
		}

		@Override
		public void deleteRow(Provider main, Cursor diffsCursor) throws SQLException
		{
		}

		private long insertOrUpdateRow(Provider main, Long id, Cursor diffsCursor)
			throws SQLException
		{
			InsertHelper helper = ((MetaProvider)main).getArtistDAO().getInsertHelper();

			if (id == null)
				helper.prepareForInsert();
			else
			{
				helper.prepareForReplace();
				helper.bind(Columns._ID, id);
			}

			DatabaseUtils.cursorStringToHelper(Columns._SYNC_TIME, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns._SYNC_ID, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.NAME, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.NAME_MATCH, diffsCursor, helper);
			DatabaseUtils.cursorStringToHelper(Columns.DISCOVERY_DATE, diffsCursor, helper);

			if (id == null)
				return helper.insert();
			else
			{
				helper.execute();
				return id;
			}
		}

		@Override
		public long insertRow(Provider main, Cursor diffsCursor) throws SQLException
		{
			return insertOrUpdateRow(main, null, diffsCursor);
		}

		@Override
		public void updateRow(Provider main, long id, Cursor diffsCursor) throws SQLException
		{
			insertOrUpdateRow(main, id, diffsCursor);
		}
	}
}
