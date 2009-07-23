package org.devtcg.five.util;

import java.io.Closeable;
import java.io.IOException;

public class IOUtils
{
	public static void closeQuietly(Closeable closeable)
	{
		try {
			closeable.close();
		} catch (IOException e) {}
	}

	public static void closeQuietlyNullSafe(Closeable closeable)
	{
		if (closeable != null)
			closeQuietly(closeable);
	}
}
