package org.devtcg.five.util;

public abstract class StoppableThread extends Thread
{
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
