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

import java.io.File;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.Main;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.server.UPnPService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class Setup
{
	private static final Log LOG = LogFactory.getLog(Setup.class);

	private Shell mWindow;
	private Font mBigBoldFont;
	private Font mBoldFont;

	private Button mUseUPnP;
	private Text mPathLabel;
	private Text mPasswordText;
	private Button mBrowseButton;
	private Button mOkButton;
	private Button mCancelButton;

	public static void show(Display display)
	{
		Setup setup = new Setup(display);
		setup.open();
	}

	public Setup(Display display)
	{
		createContents(display);
		postCreate();
	}

	private void createContents(Display display)
	{
		mWindow = new Shell(display);
		mWindow.setText("Five server setup");
		mWindow.setMinimumSize(500, 350);
		mWindow.setSize(500, 350);

		GridLayout mainLayout = new GridLayout(1, false);
		mainLayout.marginWidth = 10;
		mainLayout.marginHeight = 10;
		mainLayout.verticalSpacing = 10;
		mWindow.setLayout(mainLayout);

		Label title = new Label(mWindow, SWT.NONE);
		title.setText("Five media distribution setup");
		title.setFont(getBiggerBoldFont());
		title.setData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		Label desc = new Label(mWindow, SWT.WRAP);
		desc.setText("This setup will configure the server component, allowing you to access your music anywhere you go!");

		final GridData descData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		descData.widthHint = mWindow.getClientArea().width;
		desc.setLayoutData(descData);

		Group group = new Group(mWindow, SWT.NONE);

		final GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, true);
		group.setLayoutData(groupData);

		GridLayout groupLayout = new GridLayout(3, false);
		groupLayout.marginWidth = 10;
		groupLayout.marginHeight = 0;
		groupLayout.verticalSpacing = 5;
		group.setLayout(groupLayout);

		createGroupContents(display, group);

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

		mWindow.addListener(SWT.Resize, new Listener() {
			public void handleEvent(Event event)
			{
				descData.widthHint = groupData.widthHint = mWindow.getClientArea().width;
				mWindow.layout();
			}
		});
	}

	private void createGroupContents(Display display, Group group)
	{
		newSectionLabel(group, "Library:");

		Text pathLabel = mPathLabel = new Text(group, SWT.SINGLE | SWT.BORDER);
		pathLabel.setEditable(false);
		pathLabel.setEnabled(false);
		pathLabel.setText("");
		GridData pathLabelData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1);
		pathLabelData.widthHint = 240;
		pathLabelData.horizontalIndent = 6;
		pathLabel.setLayoutData(pathLabelData);

		Button browse = mBrowseButton = new Button(group, SWT.PUSH);
		browse.setText("Browse...");
		browse.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

		newSectionLabel(group, "Server:");

		Button useUPnP = mUseUPnP = new Button(group, SWT.CHECK);
		useUPnP.setText("Use UPnP to automatically configure router");
		useUPnP.setSelection(true);
		useUPnP.setLayoutData(newSectionItemData(SWT.BEGINNING, SWT.BEGINNING, true, false));

		Label passwordLabel = new Label(group, SWT.NONE);
		passwordLabel.setText("Password: ");
		GridData passwordLabelData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		passwordLabelData.horizontalIndent = 6;
		passwordLabel.setLayoutData(passwordLabelData);

		Text password = mPasswordText = new Text(group, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD);
		GridData passwordData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false, 2, 1);
		passwordData.widthHint = 140;
		password.setLayoutData(passwordData);
	}

	private Label newSectionLabel(Composite parent, String text)
	{
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		label.setFont(getBoldFont());
		label.setLayoutData(newSectionLabelGridData());
		return label;
	}

	private static GridData newSectionLabelGridData()
	{
		GridData data = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 3, 1);
		data.verticalIndent = 6;
		return data;
	}

	private static GridData newSectionItemData(int horizAlign, int vertAlign,
			boolean grabWidth, boolean grabHeight)
	{
		GridData data = new GridData(horizAlign, vertAlign, grabWidth, grabHeight, 3, 1);
		data.horizontalIndent = 6;
		return data;
	}

	private void postCreate()
	{
		mBrowseButton.addListener(SWT.Selection, mBrowseClicked);
		mOkButton.addListener(SWT.Selection, mOkClicked);
		mCancelButton.addListener(SWT.Selection, mCancelClicked);

		mUseUPnP.addListener(SWT.Selection, mUseUPnPClicked);

		final UPnPService upnp = UPnPService.getInstance();
		upnp.addMappingListener(mUPnPMappingListener);
		upnp.enableUPnP();

		mWindow.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e)
			{
				upnp.removeMappingListener(mUPnPMappingListener);
			}
		});
	}

	private final Listener mBrowseClicked = new Listener()
	{
		public void handleEvent(Event e)
		{
			DirectoryDialog dialog = new DirectoryDialog(mWindow);
			dialog.setFilterPath(Configuration.getHomePath());
			dialog.setText("Select your music library");
			dialog.setMessage("Choose a directory which contains your music library.");

			String selection = dialog.open();
			if (selection != null)
				mPathLabel.setText(selection);
		}
	};

	private final Listener mOkClicked = new Listener()
	{
		private boolean checkPath()
		{
			String pathString = mPathLabel.getText().trim();
			if (pathString == null)
				return false;

			File path = new File(mPathLabel.getText().trim());

			return (path.exists() && path.isDirectory());
		}

		private void showError(String title, String message)
		{
			MessageBox box = new MessageBox(mWindow, SWT.ICON_ERROR | SWT.OK);
			box.setText(title);
			box.setMessage(message);
			box.open();
		}

		public void handleEvent(Event event)
		{
			if (!checkPath())
			{
				showError("Library path does not exist",
						"The library path you've chosen does not exist or cannot be read.");
				return;
			}

			if (mPasswordText.getText().trim().length() == 0)
			{
				showError("Missing password",
						"You must provide a password to secure access to your media.  This password will be used when setting up the phone client.");
				return;
			}

			/*
			 * Save the results to the config database, never showing this setup
			 * wizard again.
			 */
			try {
				Configuration.getInstance().initFirstTime(
						new File(mPathLabel.getText().trim()).getAbsolutePath(),
						mPasswordText.getText().trim(), mUseUPnP.getSelection());
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}

			/*
			 * Start the docklet and core services (file crawler, http server,
			 * etc).
			 */
			Docklet docklet = new Docklet(mWindow.getDisplay());
			Main.startServices();

			mWindow.close();
			docklet.open();
		}
	};

	private final Listener mCancelClicked = new Listener()
	{
		public void handleEvent(Event e)
		{
			mWindow.close();
		}
	};

	private final UPnPService.MappingListener mUPnPMappingListener = new UPnPService.MappingListener()
	{
		public void onMappingReady(boolean success)
		{
			if (LOG.isInfoEnabled())
			{
				if (success)
					LOG.info("UPnP configuration succeeded");
				else
					LOG.info("UPnP configuration failure!");
			}
		}
	};

	private final Listener mUseUPnPClicked = new Listener()
	{
		public void handleEvent(Event e)
		{
			/*
			 * Impose a slight delay to prevent resource exhaustion if the user
			 * madly toggles.
			 */
			mWindow.getDisplay().timerExec(500, mToggleUPnPEvent);
		}

		private final Runnable mToggleUPnPEvent = new Runnable()
		{
			public void run()
			{
				/* We will be notified when the state changes to update the UI. */
				if (mUseUPnP.getSelection())
					UPnPService.getInstance().enableUPnP();
				else
					UPnPService.getInstance().disableUPnP();
			}
		};
	};

	public void open()
	{
		mWindow.pack();
		mWindow.open();

		Display display = mWindow.getDisplay();
		while (!mWindow.isDisposed())
		{
			if (!display.readAndDispatch())
				display.sleep();
		}
	}

	private Font getBiggerBoldFont()
	{
		if (mBigBoldFont == null)
		{
			FontData[] data = mWindow.getFont().getFontData();
			mBigBoldFont = new Font(mWindow.getDisplay(), data[0].getName(),
					data[0].getHeight() + 1, SWT.BOLD);
		}

		return mBigBoldFont;
	}

	private Font getBoldFont()
	{
		if (mBoldFont == null)
		{
			FontData[] data = mWindow.getFont().getFontData();
			mBoldFont = new Font(mWindow.getDisplay(), data[0].getName(),
					data[0].getHeight(), SWT.BOLD);
		}

		return mBoldFont;
	}
}
