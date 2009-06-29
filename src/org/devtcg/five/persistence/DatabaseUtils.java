package org.devtcg.five.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseUtils {
	public static Connection getNewConnection(String dbName) throws SQLException
	{
		return DriverManager.getConnection("jdbc:hsqldb:file:" +
				Configuration.getDatabasePath(dbName));
	}

	private static PreparedStatement createPreparedStatement(Connection conn,
		String sql, String[] args) throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(sql);

		int n = args.length;
		for (int i = 0; i < n; i++)
			stmt.setString(i + 1, args[i]);

		return stmt;
	}

	public static void execute(Connection conn, String sql, String... args)
		throws SQLException
	{
		if (args == null || args.length == 0)
			conn.createStatement().execute(sql);
		else
		{
			PreparedStatement stmt = createPreparedStatement(conn, sql, args);
			stmt.execute();
		}
	}

	public static long getLastInsertId(Connection conn) throws SQLException
	{
		return longForQuery(conn, "CALL IDENTITY()");
	}

	public static ResultSet executeForResult(Connection conn,
		String query, String... args) throws SQLException
	{
		if (args == null || args.length == 0)
			return conn.createStatement().executeQuery(query);
		else
		{
			PreparedStatement stmt = createPreparedStatement(conn, query, args);
			return stmt.executeQuery();
		}
	}

	public static String stringForQuery(Connection conn, String defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return stringForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static String stringForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false)
			throw new RowNotFoundException();

		return set.getString(1);
	}

	public static int integerForQuery(Connection conn, int defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return integerForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static int integerForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false)
			throw new RowNotFoundException();

		return set.getInt(1);
	}

	public static long longForQuery(Connection conn, long defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return longForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static long longForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false)
			throw new RowNotFoundException();

		return set.getLong(1);
	}

	public static boolean booleanForQuery(Connection conn, boolean defaultValue,
		String query, String... args) throws SQLException
	{
		try {
			return booleanForQuery(conn, query, args);
		} catch (RowNotFoundException e) {
			return defaultValue;
		}
	}

	public static boolean booleanForQuery(Connection conn, String query,
		String... args) throws SQLException
	{
		ResultSet set = executeForResult(conn, query, args);

		if (set.next() == false)
			throw new RowNotFoundException();

		return set.getBoolean(1);
	}

	public static class RowNotFoundException extends SQLException
	{
		public RowNotFoundException()
		{
			super();
		}

		public RowNotFoundException(String message)
		{
			super(message);
		}
	}
}
