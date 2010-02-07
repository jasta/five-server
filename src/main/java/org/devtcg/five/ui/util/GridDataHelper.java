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

import org.eclipse.swt.layout.GridData;

public class GridDataHelper
{
	private final GridData mGridData;

	public GridDataHelper(int arg0) { mGridData = new GridData(arg0); }
	public GridDataHelper(int arg0, int arg1) { mGridData = new GridData(arg0, arg1); }
	public GridDataHelper(int arg0, int arg1, boolean arg2, boolean arg3) { mGridData = new GridData(arg0, arg1, arg2, arg3); }
	public GridDataHelper(int arg0, int arg1, boolean arg2, boolean arg3, int arg4, int arg5) { mGridData = new GridData(arg0, arg1, arg2, arg3, arg4, arg5); }

	public GridData getGridData()
	{
		return mGridData;
	}

	public GridDataHelper setHorizontalIndent(int horizIndent)
	{
		mGridData.horizontalIndent = horizIndent;
		return this;
	}

	public GridDataHelper setWidthHint(int widthHint)
	{
		mGridData.widthHint = widthHint;
		return this;
	}
}
