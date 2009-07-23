package org.devtcg.five.util;

public class TimeUtils
{
	public static long asUnixTimestamp(long millis)
	{
		return millis / 1000;
	}

	public static long getUnixTimestamp()
	{
		return System.currentTimeMillis() / 1000;
	}
}
