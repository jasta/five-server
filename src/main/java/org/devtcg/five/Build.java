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

package org.devtcg.five;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Build
{
	private Build() {}

	public static String VERSION = readLineFromResource("version.prop");

	private static String readLineFromResource(String resourceName)
	{
		try {
			InputStream in = Build.class.getResourceAsStream(resourceName);
			if (in == null)
				throw new ResourceNotFoundException(resourceName);

			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			return reader.readLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class ResourceNotFoundException extends RuntimeException
	{
		public ResourceNotFoundException(String resourceName)
		{
			super("Resource not found '" + resourceName + "'");
		}
	}
}
