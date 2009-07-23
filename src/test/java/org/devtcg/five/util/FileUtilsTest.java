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

import junit.framework.TestCase;

public class FileUtilsTest extends TestCase
{
	public void testNoExtension()
	{
		assertEquals(FileUtils.getExtension("foo"), null);
	}

	public void testGetExtension()
	{
		assertEquals(FileUtils.getExtension("/foo/bar.EXT"), "EXT");
		assertEquals(FileUtils.getExtension("bar.html"), "html");
		assertEquals(FileUtils.getExtension(new File("bar.html")), "html");
	}

	public void testHasExtension()
	{
		assertTrue(FileUtils.hasExtension(new File("bar.whatever"), "WHATever"));
		assertFalse(FileUtils.hasExtension(new File("bar.mp3"), "ogg"));
		assertFalse(FileUtils.hasExtension(new File("foo"), "foo"));
		assertTrue(FileUtils.hasExtension(new File("foo"), null));
	}
}
