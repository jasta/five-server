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

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.Main;
import org.devtcg.five.meta.FileCrawler;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.server.UPnPService;
import org.devtcg.five.ui.util.FontUtil;
import org.devtcg.five.ui.util.GridDataHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

public class Preferences
{
	private static final Log LOG = LogFactory.getLog(Preferences.class);

	/**
	 * Set non-null if the Preferences screen is currently displayed. Set null
	 * when disposed.
	 */
	private static Preferences sInstance;

	private Shell mWindow;
	private FontUtil mFontUtil;
	private ServerTab mServerTab;
	private LibraryTab mLibraryTab;
	private Button mOkButton;
	private Button mCancelButton;

	private PrefsTab[] mAllTabs;

	public static synchronized void show(Display display)
	{
		if (sInstance == null)
			sInstance = new Preferences(display);

		sInstance.show();
	}

	private Preferences(Display display)
	{
		createContents(display);
		bindData();
		postCreate();
	}

	private void show()
	{
		mWindow.pack();
		mWindow.open();
	}

	private void createContents(Display display)
	{
		mWindow = new Shell(display);
		mWindow.setText("Five settings");
		mWindow.setMinimumSize(510, 380);
		mWindow.setSize(510, 380);

		mFontUtil = new FontUtil(mWindow);

		GridLayout mainLayout = new GridLayout(1, false);
		mainLayout.marginWidth = 10;
		mainLayout.marginHeight = 10;
		mainLayout.verticalSpacing = 10;
		mWindow.setLayout(mainLayout);

		TabFolder tabs = new TabFolder(mWindow, SWT.BORDER);
		GridData tabsData = new GridData(SWT.FILL, SWT.FILL, true, true);
		tabs.setLayoutData(tabsData);

		mServerTab = new ServerTab(tabs);
		mLibraryTab = new LibraryTab(tabs);

		mAllTabs = new PrefsTab[] { mServerTab, mLibraryTab };

		Composite okCancelButtons = new Composite(mWindow, SWT.NONE);
		GridLayout okCancelLayout = new GridLayout(2, false);
		okCancelLayout.verticalSpacing = 0;
		okCancelLayout.marginHeight = 0;
		okCancelLayout.marginWidth = 0;
		okCancelButtons.setLayout(okCancelLayout);
		okCancelButtons.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		Button cancel = mCancelButton = new Button(okCancelButtons, SWT.PUSH);
		cancel.setText("Cancel");
		GridData cancelData = new GridData(SWT.RIGHT, SWT.BEGINNING, true, false);
		cancelData.widthHint = 85;
		cancel.setLayoutData(cancelData);

		Button ok = mOkButton = new Button(okCancelButtons, SWT.PUSH);
		ok.setText("OK");
		GridData okData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
		okData.widthHint = 85;
		ok.setLayoutData(okData);

		mWindow.setDefaultButton(ok);
	}

	private Label newSectionLabel(Composite parent, String text)
	{
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		label.setFont(mFontUtil.getBoldFont());
		label.setLayoutData(newSectionLabelGridData());
		return label;
	}

	private static GridData newSectionLabelGridData()
	{
		GridData data = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
		data.verticalIndent = 10;
		return data;
	}

	private static GridData newSectionItemData(int horizAlign, int vertAlign,
			boolean grabWidth, boolean grabHeight)
	{
		GridData data = new GridData(horizAlign, vertAlign, grabWidth, grabHeight, 2, 1);
		data.horizontalIndent = 6;
		return data;
	}

	private void bindData()
	{
		try {
			for (PrefsTab tab: mAllTabs)
				tab.bindData();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void postCreate()
	{
		mWindow.addListener(SWT.Traverse, new Listener() {
			public void handleEvent(Event e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE)
				{
					mWindow.close();
					e.detail = SWT.TRAVERSE_NONE;
					e.doit = false;
				}
			}
		});
		mWindow.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				synchronized(Preferences.class) {
					sInstance = null;
				}
			}
		});
		mOkButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					List<FieldError> errors = null;

					for (PrefsTab tab: mAllTabs)
					{
						List<FieldError> currentTabErrors = tab.commit();
						if (errors == null)
							errors = currentTabErrors;
						else if (currentTabErrors != null)
							errors.addAll(currentTabErrors);
					}

					if (errors == null || errors.size() == 0)
						mWindow.close();
					else
						System.out.println("TODO: Can't handle form errors, but found some.");
				} catch (SQLException exception) {
					/* TODO: Not sure how we should handle this yet. */
					throw new RuntimeException(exception);
				}
			}
		});
		mCancelButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				mWindow.close();
			}
		});

		for (PrefsTab tab: mAllTabs)
			tab.postCreate();
	}

	private class FieldError
	{
		private final String mFieldKey;
		private final String mErrorText;

		public FieldError(String fieldKey, String error)
		{
			mFieldKey = fieldKey;
			mErrorText = error;
		}
	}

	private interface PrefsTab
	{
		public void bindData() throws SQLException;
		public void postCreate();
		public List<FieldError> commit() throws SQLException;
	}

	private class ServerTab implements PrefsTab
	{
		public Button startAuto;
		public Text password;
		public Spinner port;
		public Button useUPnP;
		public Label status;

		public ServerTab(TabFolder tabs)
		{
			createContents(tabs);
		}

		private void createContents(TabFolder tabs)
		{
			TabItem tab = new TabItem(tabs, SWT.NONE);
			tab.setText("Server");

			Composite box = new Composite(tabs, SWT.NONE);
			GridLayout boxLayout = new GridLayout(2, false);
			boxLayout.marginWidth = 10;
			boxLayout.marginHeight = 0;
			boxLayout.verticalSpacing = 5;
			box.setLayout(boxLayout);

			newSectionLabel(box, "General:");

			startAuto = new Button(box, SWT.CHECK);
			startAuto.setText("Start automatically");
			startAuto.setSelection(true);
			startAuto.setLayoutData(newSectionItemData(SWT.BEGINNING, SWT.BEGINNING, true, false));

			Label passwordLabel = new Label(box, SWT.NONE);
			passwordLabel.setText("Password:");
			passwordLabel.setLayoutData(new GridDataHelper(SWT.BEGINNING, SWT.CENTER, false, false)
					.setHorizontalIndent(6).getGridData());

			password = new Text(box, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD);
			password.setLayoutData(new GridDataHelper(SWT.BEGINNING, SWT.BEGINNING, false, false)
					.setWidthHint(140).getGridData());

			newSectionLabel(box, "Network:");

			Label portLabel = new Label(box, SWT.NONE);
			portLabel.setText("Port:");
			portLabel.setLayoutData(new GridDataHelper(SWT.BEGINNING, SWT.CENTER, false, false)
					.setHorizontalIndent(6).getGridData());

			port = new Spinner(box, SWT.BORDER);
			port.setMinimum(1);
			port.setMaximum(65535);
			port.setIncrement(1);
			port.setPageIncrement(100);
			port.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));

			useUPnP = new Button(box, SWT.CHECK);
			useUPnP.setText("Use UPnP to automatically configure router");
			useUPnP.setLayoutData(newSectionItemData(SWT.BEGINNING, SWT.BEGINNING, true, false));

			newSectionLabel(box, "Status:");

			status = new Label(box, SWT.NONE);
			status.setText("Everything is fine.");
			status.setLayoutData(newSectionItemData(SWT.BEGINNING, SWT.CENTER, false, false));

			tab.setControl(box);
		}

		public void bindData() throws SQLException
		{
			Configuration config = Configuration.getInstance();
			password.setText("<hidden>");
			port.setSelection(config.getServerPort());
			useUPnP.setSelection(config.useUPnP());
		}

		public void postCreate()
		{
			password.addListener(SWT.FocusIn, new Listener() {
				public void handleEvent(Event e) {
					if (password.getText().equals("<hidden>"))
						password.setText("");
				}
			});
			password.addListener(SWT.FocusOut, new Listener() {
				public void handleEvent(Event e) {
					if (password.getText().trim().equals(""))
						password.setText("<hidden>");
				}
			});
		}

		public List<FieldError> commit() throws SQLException
		{
			Configuration config = Configuration.getInstance();

			List<FieldError> errors = new LinkedList<FieldError>();

			String plaintextPassword = password.getText().trim();
			if (plaintextPassword.length() > 0)
			{
				String hashedPassword = Configuration.sha1Hash(plaintextPassword);
				if (!config.getHashedPassword().equals(hashedPassword))
					config.setHashedPassword(hashedPassword);
			}

			if (config.useUPnP() != useUPnP.getSelection())
			{
				config.setUseUPnP(useUPnP.getSelection());
				if (useUPnP.getSelection())
					UPnPService.getInstance().enableUPnP();
				else
					UPnPService.getInstance().disableUPnP();
			}

			if (config.getServerPort() != port.getSelection())
			{
				config.setServerPort(port.getSelection());
				try {
					Main.mServer.rebind(port.getSelection());
				} catch (IOException e) {
					if (LOG.isWarnEnabled())
						LOG.warn("Failed to bind to port " + port.getSelection(), e);
					errors.add(new FieldError(Configuration.Keys.PORT,
							"Failed to bind to port " + port.getSelection()));
				}
			}

			return errors;
		}
	}

	private class LibraryTab implements PrefsTab
	{
		public Combo location;
		public Combo refresh;
		public Button useFsNotify;

		private final String[] mRefreshStrings = new String[] {
			"Every 2 minutes", "Every 12 hours", "Every day", "Twice a week", "Once a week", "Once a month",
		};

		private static final long mOneDayInMsec = 24 * 60 * 60 * 1000;

		private final long[] mRefreshIntervals = new long[] {
			2 * 60 * 1000, mOneDayInMsec / 2, mOneDayInMsec, (long)(mOneDayInMsec * 3.5), mOneDayInMsec * 7,
			(long)(mOneDayInMsec * 7 * (52f / 12f))
		};

		public LibraryTab(TabFolder tabs)
		{
			createContents(tabs);
		}

		private void createContents(TabFolder tabs)
		{
			TabItem tab = new TabItem(tabs, SWT.NONE);
			tab.setText("Library");

			Composite box = new Composite(tabs, SWT.NONE);
			GridLayout boxLayout = new GridLayout(2, false);
			boxLayout.marginWidth = 10;
			boxLayout.marginHeight = 0;
			boxLayout.verticalSpacing = 5;
			box.setLayout(boxLayout);

			newSectionLabel(box, "Location:");

			newSectionLabel(box, "Auto-refresh:");

			refresh = new Combo (box, SWT.READ_ONLY | SWT.DROP_DOWN);
			refresh.setItems(mRefreshStrings);
			refresh.setLayoutData(new GridDataHelper(SWT.BEGINNING, SWT.BEGINNING, false, false)
					.setHorizontalIndent(6).setWidthHint(200).getGridData());

			useFsNotify = new Button(box, SWT.CHECK);
			useFsNotify.setText("Watch filesystem for changes (NOT YET IMPLEMENTED)");
			useFsNotify.setSelection(true);
			useFsNotify.setLayoutData(newSectionItemData(SWT.BEGINNING, SWT.BEGINNING, false, false));

			tab.setControl(box);
		}

		private String getRefreshString(long refreshInterval)
		{
			int n = mRefreshIntervals.length;
			for (int i = 0; i < n; i++)
			{
				if (mRefreshIntervals[i] == refreshInterval)
					return mRefreshStrings[i];
			}

			if (LOG.isWarnEnabled())
				LOG.warn("Arbitrary refresh interval of " + refreshInterval + " cannot be represented");

			return mRefreshStrings[2];
		}

		public void bindData() throws SQLException
		{
			Configuration config = Configuration.getInstance();
			refresh.setText(getRefreshString(config.getRescanInterval()));
		}

		public void postCreate() {}

		public List<FieldError> commit() throws SQLException
		{
			Configuration config = Configuration.getInstance();

			long refreshInterval = mRefreshIntervals[refresh.getSelectionIndex()];

			if (config.getRescanInterval() != refreshInterval)
			{
				config.setRescanInterval(refreshInterval);
				FileCrawler.getInstance().updateRescanInterval();
			}

			return null;
		}
	}
}
