package org.devtcg.five.meta;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.devtcg.five.persistence.Configuration;
import org.devtcg.five.util.StoppableThread;

public class FileCrawler
{
	private static final Log LOG = LogFactory.getLog(FileCrawler.class);

	private static FileCrawler INSTANCE;

	private CrawlerThread mThread;
	private List<String> mPaths;

	private FileCrawler()
	{
		try {
			setPaths(Configuration.getInstance().getLibraryPaths());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public static synchronized FileCrawler getInstance()
	{
		if (INSTANCE == null)
			INSTANCE = new FileCrawler();

		return INSTANCE;
	}

	private void setPaths(List<String> paths)
	{
		mPaths = paths;
	}

	public synchronized void startScan()
	{
		if (mThread == null)
		{
			mThread = new CrawlerThread(MetaProvider.getTemporaryInstance());
			mThread.setName("FileCrawler");
			mThread.setPriority(Thread.MIN_PRIORITY);
			mThread.start();
		}
	}

	public synchronized void stopAbruptly()
	{
		if (mThread != null) {
			mThread.stopAbruptly();
			mThread.joinUninterruptibly();

			/* Should have been set to null by the thread itself. */
			assert mThread == null;
		}
	}

	public synchronized boolean isActive()
	{
		return mThread != null;
	}

	private class CrawlerThread extends StoppableThread
	{
		private volatile boolean mStop = false;

		private MetaProvider mTempProvider;

		public CrawlerThread(MetaProvider tempProvider)
		{
			mTempProvider = tempProvider;
		}

		private void handleFile(File file)
		{
			System.out.println("file=" + file.getAbsolutePath());
		}

		private void traverse(File path)
		{
			File[] files = path.listFiles();
			for (File file: files)
			{
				if (mStop == true)
					return;

				if (file.isDirectory() == true)
					traverse(file);
				else if (file.isFile() == true)
					handleFile(file);
			}
		}

		private void crawlImpl()
		{
			for (String path: mPaths)
				traverse(new File(path));

			if (mStop == true)
				return;

			MetaProvider provider = MetaProvider.getInstance();
			try {
				provider.lock();
				try {
					/* do merge work... */
				} finally {
					provider.unlock();
				}
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		public void run()
		{
			try {
				crawlImpl();
			} finally {
				synchronized (FileCrawler.this) {
					mThread = null;
				}
			}
		}

		public void stopAbruptly()
		{
			mStop = true;
		}
	}
}
