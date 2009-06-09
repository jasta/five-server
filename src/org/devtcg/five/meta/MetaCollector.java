package org.devtcg.five.meta;

public abstract class MetaCollector
{
	protected StateListener mStateListener;

	/** One hour. */
	public static final int DEFAULT_PAUSE_LENGTH = 60 * 60 * 1000;

	public void setStateListener(StateListener listener)
	{
		mStateListener = listener;
	}

	public StateListener getStateListener()
	{
		return mStateListener;
	}

	public abstract void start();
	public abstract void stopAbruptly();

	public int getProgress()
	{
		int size = getCollectionSize();
		if (size >= 0)
		{
			int count = getCollectedCount();
			if (count >= 0)
				return count / size;
		}
		return -1;
	}

	public abstract int getCollectedCount();

	/**
	 * @return -1 if not known.
	 */
	public abstract int getCollectionSize();

	/**
	 * Pause for {@link #DEFAULT_PAUSE_LENGTH}.
	 */
	public void pause()
	{
		pause(DEFAULT_PAUSE_LENGTH);
	}

	public abstract void pause(long millis);

	public interface StateListener
	{
		public void onStart();
		public void onStop();
		public void onPaused();
		public void onResumed();
		public void onFinished();
		public void onProgressUpdate(int collectedCount, int collectedSize);
	}
}
