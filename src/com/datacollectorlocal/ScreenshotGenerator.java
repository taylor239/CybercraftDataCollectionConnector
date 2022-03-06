package com.datacollectorlocal;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.swing.SwingUtilities;

public class ScreenshotGenerator implements Runnable
{
	private int sleepTime = 10000;
	private ArrayList myListeners = new ArrayList();
	private Robot myRobot;
	private Thread myThread;
	private boolean running = false;
	
	private ArrayList myMetricListeners = new ArrayList();
	
	public ScreenshotGenerator(int timeout)
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
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void takeScreenshot()
	{
		try
		{
			Date date = new Date();
			ArrayList deactivated = new ArrayList();
			HashMap autoFocus = new HashMap();
			HashMap opacity = new HashMap();
			HashMap undecorated = new HashMap();
			HashMap disposed = new HashMap();
			HashMap sizeX = new HashMap();
			HashMap sizeY = new HashMap();
			for(int x=0; x<myListeners.size(); x++)
			{
				ScreenshotListener curListen = (ScreenshotListener) myListeners.get(x);
				ArrayList curInvisList = curListen.getInvisibleComponents();
				for(int y=0; y<curInvisList.size(); y++)
				{
					Component curComponent = (Component) curInvisList.get(y);
					if(curComponent.isVisible())
					{
						if(curComponent instanceof Window)
						{
							autoFocus.put(curComponent, ((Window)curComponent).isAutoRequestFocus());
							((Window)curComponent).setAutoRequestFocus(false);
							//opacity.put(curComponent, ((Window)curComponent).getOpacity());
							//((Window)curComponent).setOpacity(0);
							if(curComponent instanceof Frame)
							{
								//undecorated.put(curComponent, ((Frame)curComponent).isUndecorated());
								//((Frame)curComponent).setUndecorated(true);
							}
						}
						if(curComponent instanceof Window)
						{
							//((Window)curComponent).dispose();
							if(curComponent instanceof Frame)
							{
								//undecorated.put(curComponent, ((Frame)curComponent).isUndecorated());
								//((Frame)curComponent).setUndecorated(true);
								//curComponent.setVisible(true);
							}
							sizeX.put(curComponent, curComponent.getWidth());
							sizeY.put(curComponent, curComponent.getHeight());
							curComponent.setSize(0, 0);
						}
						curComponent.setVisible(false);
						deactivated.add(curComponent);
						curComponent.revalidate();
						curComponent.repaint();
					}
				}
				
			}
			Runnable screenshotTaker = new Runnable()
			{

				@Override
				public void run()
				{
					try
					{
						Thread.sleep(1000);
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
					Image screenshot = myRobot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
					
					for(int x=0; x<myListeners.size(); x++)
					{
						((ScreenshotListener)myListeners.get(x)).getScreenshotEvent(date, screenshot);
					}
					
					for(int x=0; x<deactivated.size(); x++)
					{
						Component curComponent = (Component) deactivated.get(x);
						if(curComponent instanceof Frame)
						{
							//((Frame)curComponent).dispose();
							//((Frame)curComponent).setUndecorated((boolean) undecorated.get(curComponent));
						}
						curComponent.setVisible(true);
						if(curComponent instanceof Window)
						{
							curComponent.setSize((int)sizeX.get(curComponent), (int)sizeY.get(curComponent));
							((Window)curComponent).setAutoRequestFocus((boolean) autoFocus.get(curComponent));
							//((Window)curComponent).setOpacity((int) opacity.get(curComponent));
							if(curComponent instanceof Frame)
							{
								//((Frame)curComponent).setUndecorated((boolean) undecorated.get(curComponent));
							}
						}
					}
				}
		
			};
			
			SwingUtilities.invokeLater(screenshotTaker);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
