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

package org.devtcg.five.util;

import java.io.File;

public class FileUtils
{
	public static String removeExtension(String filename)
	{
		int extIndex = filename.lastIndexOf('.');
		if (extIndex <= 0)
			return filename;

		return filename.substring(0, extIndex);
	}

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
		else if (ext.equalsIgnoreCase("m4a") || ext.equalsIgnoreCase("m4p"))
			return "audio/mp4a-latm";
		else if (ext.equalsIgnoreCase("mp4"))
			return "audio/mp4";

		return null;
	}

	public static String getMimeType(File file)
	{
		return getMimeType(file.getName());
	}
}
