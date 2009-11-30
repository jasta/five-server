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

package org.devtcg.five.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

public class Docklet
{
	private TrayItem mTrayItem;
	private Menu mMenu;

	public Docklet(Display display)
	{
		createContents(display);
		postCreate();
	}

	private void createContents(Display display)
	{
		Tray tray = display.getSystemTray();
		if (tray == null)
			throw new IllegalStateException("No system tray feature, must halt.");
		else
		{
			mTrayItem = new TrayItem(tray, SWT.NONE);

			Image image = new Image(display, 16, 16);
			mTrayItem.setImage(image);

			mMenu = new Menu(new Shell(display), SWT.POP_UP);
			addMenuItem(mMenu, "Preferences", mPreferencesClicked);
			addMenuItem(mMenu, "About", null);
			new MenuItem(mMenu, SWT.SEPARATOR);
			addMenuItem(mMenu, "Quit", mQuitClicked);
		}
	}

	private void postCreate()
	{
		mTrayItem.addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				System.out.println("default selection of tray icon...");
			}
		});

		mTrayItem.addListener(SWT.MenuDetect, new Listener() {
			public void handleEvent(Event event) {
				mMenu.setVisible(true);
			}
		});
	}

	private static void addMenuItem(Menu menu, String text, Listener listener)
	{
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(text);

		if (listener != null)
			item.addListener(SWT.Selection, listener);
	}

	private final Listener mPreferencesClicked = new Listener()
	{
		public void handleEvent(Event event)
		{
			System.out.println("preferences clicked");
		}
	};

	private final Listener mQuitClicked = new Listener()
	{
		public void handleEvent(Event event)
		{
			mTrayItem.dispose();
		}
	};

	public void open()
	{
		Display display = mTrayItem.getDisplay();
		while (!mTrayItem.isDisposed())
		{
			if (!display.readAndDispatch())
				display.sleep();
		}
	}
}
