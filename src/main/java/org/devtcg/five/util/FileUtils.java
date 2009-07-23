package org.devtcg.five.util;

import java.io.File;

public class FileUtils
{
	public static String getExtension(String filename)
	{
		int extIndex = filename.lastIndexOf('.') + 1;
		if (extIndex == 0)
			return null;

		if (extIndex == filename.length())
			return "";

		return filename.substring(extIndex);
	}

	public static String getExtension(File file)
	{
		return getExtension(file.getName());
	}

	public static boolean hasExtension(File file, String ext)
	{
		String fileExt = getExtension(file);
		if (fileExt == null && ext == null)
			return true;
		return (fileExt != null && fileExt.equalsIgnoreCase(ext));
	}

	public static String getMimeType(String filename)
	{
		/* XXX: Do this right. */
		String ext = getExtension(filename);
		if (ext == null)
			return null;

		if (ext.equalsIgnoreCase("mp3"))
			return "audio/mpeg";
		else if (ext.equalsIgnoreCase("ogg"))
			return "application/ogg";

		return null;
	}

	public static String getMimeType(File file)
	{
		return getMimeType(file.getName());
	}
}
