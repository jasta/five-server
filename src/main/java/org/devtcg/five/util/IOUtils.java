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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

	public static void copyStream(InputStream in, OutputStream out)
		throws IOException
	{
		copyStream(in, out, 4096);
	}

	public static void copyStream(InputStream in, OutputStream out, int bufferSize)
		throws IOException
	{
		byte[] b = new byte[bufferSize];
		int n;

		while ((n = in.read(b)) >= 0)
			out.write(b, 0, n);
	}
}
