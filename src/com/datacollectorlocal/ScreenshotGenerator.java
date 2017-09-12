package com.datacollectorlocal;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Date;

public class ScreenshotGenerator implements Runnable
{
	private int sleepTime = 10000;
	private ArrayList myListeners = new ArrayList();
	private Robot myRobot;
	private Thread myThread;
	private boolean running = false;
	
	public ScreenshotGenerator(int timeout)
	{
		sleepTime = timeout;
		try
		{
			myRobot = new Robot();
			myThread = new Thread(this);
			myThread.start();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void addScreenshotListener(ScreenshotListener newListener)
	{
		myListeners.add(newListener);
	}
	
	@Override
	public void run()
	{
		running = true;
		while(running)
		{
			try
			{
				Date date = new Date();
				Image screenshot = myRobot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
				
				for(int x=0; x<myListeners.size(); x++)
				{
					((ScreenshotListener)myListeners.get(x)).getScreenshotEvent(date, screenshot);
				}
				
				Thread.sleep(sleepTime);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
