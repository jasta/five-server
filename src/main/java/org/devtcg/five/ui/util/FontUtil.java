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

package org.devtcg.five.ui.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class FontUtil
{
	private final Display mDisplay;
	private final FontData[] mBaseFontData;

	private Font mBigBoldFont;
	private Font mBoldFont;

	public FontUtil(Shell window)
	{
		this(window.getDisplay(), window.getFont());
	}

	public FontUtil(Display display, Font baseFont)
	{
		mDisplay = display;
		mBaseFontData = baseFont.getFontData();
	}

	public Font getBiggerBoldFont()
	{
		if (mBigBoldFont == null)
		{
			mBigBoldFont = new Font(mDisplay, mBaseFontData[0].getName(),
					mBaseFontData[0].getHeight() + 1, SWT.BOLD);
		}

		return mBigBoldFont;
	}

	public Font getBoldFont()
	{
		if (mBoldFont == null)
		{
			mBoldFont = new Font(mDisplay, mBaseFontData[0].getName(),
					mBaseFontData[0].getHeight(), SWT.BOLD);
		}

		return mBoldFont;
	}
}
