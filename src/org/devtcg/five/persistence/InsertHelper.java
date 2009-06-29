package org.devtcg.five.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class InsertHelper
{
	private final PreparedStatement mStatement;

	public InsertHelper(Connection conn, String sql) throws SQLException
	{
		mStatement = conn.prepareStatement(sql);
	}

	public void setArguments(Object... args) throws SQLException
	{
		int n = args.length;
		for (int i = 0; i < n; i++)
		{
			if (args[i] == null)
				mStatement.setNull(i + 1, java.sql.Types.NULL);
			else if (args[i] instanceof String)
				mStatement.setString(i + 1, (String)args[i]);
			else if (args[i] instanceof Integer)
				mStatement.setInt(i + 1, (Integer)args[i]);
			else if (args[i] instanceof Long)
				mStatement.setLong(i + 1, (Long)args[i]);
			else if (args[i] instanceof Boolean)
				mStatement.setBoolean(i + 1, (Boolean)args[i]);
			else
				throw new IllegalArgumentException("Type not supported yet.");
		}
	}

	public void execute(Object... args) throws SQLException
	{
		setArguments(args);
		execute();
	}

	public long insert(Object... args) throws SQLException
	{
		execute(args);
		return DatabaseUtils.getLastInsertId(mStatement.getConnection());
	}

	public void execute() throws SQLException
	{
		mStatement.execute();
	}

	public void close() throws SQLException
	{
		mStatement.close();
	}
}
