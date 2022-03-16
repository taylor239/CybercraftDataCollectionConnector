package com.datacollectorlocal;

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Date;

public class SimplerScreenshotGenerator implements Runnable
{
	private int sleepTime = 10000;
	private ArrayList myListeners = new ArrayList();
	private Robot myRobot;
	private Thread myThread;
	private boolean running = false;
	
	private ArrayList myMetricListeners = new ArrayList();
	
	public SimplerScreenshotGenerator(int timeout)
	{
		sleepTime = timeout;
		try
		{
			myRobot = new Robot();
			myThread = new Thread(this, "screenshotGenerator");
			myThread.start();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void addMetricListener(MetricListener toAdd)
	{
		myMetricListeners.add(toAdd);
	}
	
	public void addScreenshotListener(ScreenshotListener newListener)
	{
		myListeners.add(newListener);
	}
	
	public void stop()
	{
		running = false;
	}
	
	@Override
	public void run()
	{
		running = true;
		while(running)
		{
			//long metricTime = System.currentTimeMillis();
			takeScreenshot();
			//metricTime = metricTime - System.currentTimeMillis();
			for(int x = 0; x < myMetricListeners.size(); x++)
			{
				MetricListener curListener = (MetricListener) myMetricListeners.get(x);
				//curListener.recordMetric("Screenshot", metricTime, "ms");
			}
			try
			{
				Thread.sleep(sleepTime);
			}
			catch (InterruptedException e)
			{
				//e.printStackTrace();
			}
		}
	}
	
	public void interruptSleepScreenshot()
	{
		if(myThread.getState() == Thread.State.WAITING || myThread.getState() == Thread.State.TIMED_WAITING)
		{
			myThread.interrupt();
		}
	}
	
	public synchronized void takeScreenshot()
	{
		//running = true;
		//while(running)
		{
			Image screenshot = myRobot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
			Date date = new Date();
			for(int x=0; x<myListeners.size(); x++)
			{
				((ScreenshotListener)myListeners.get(x)).getScreenshotEvent(date, screenshot);
			}
		}
	}
}
