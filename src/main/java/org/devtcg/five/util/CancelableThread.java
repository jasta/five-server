package org.devtcg.five.util;

public abstract class CancelableThread extends Thread
{
	private volatile boolean mCanceled = false;

	public void requestCancel()
	{
		if (mCanceled == true)
			return;

		interrupt();
		onRequestCancel();

		mCanceled = true;
	}

	protected void onRequestCancel() {}

	public void requestCancelAndWait()
	{
		requestCancel();
		joinUninterruptibly();
	}

	public boolean hasCanceled()
	{
		return mCanceled;
	}

	public void joinUninterruptibly()
	{
		while (true)
		{
			try {
				join();
				break;
			} catch (InterruptedException e) {}
		}
	}
}
