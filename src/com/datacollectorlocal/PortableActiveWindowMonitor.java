package com.datacollectorlocal;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OperatingSystem;

public class PortableActiveWindowMonitor implements Runnable
{
	
	private SystemInfo si;
	private HardwareAbstractionLayer hal;
	private OperatingSystem os;
	private PortableProcessMonitor myProcMonitor;
	
	private ArrayList consumerList = new ArrayList();
	
	private int pollPeriod = 10000;
	
	private Thread myThread;
	
	private boolean running = false;
	
	private long usualDiff = 0;
	
	//private LinuxActiveWindowMonitor backupMonitor;
	
	public PortableActiveWindowMonitor()
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		myProcMonitor = new PortableProcessMonitor();
		//backupMonitor = new LinuxActiveWindowMonitor(myProcMonitor);
		myThread = new Thread(this);
		myThread.setName("WindowMonitor");
		myThread.start();
	}
	
	public PortableActiveWindowMonitor(PortableProcessMonitor passedMonitor)
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		myProcMonitor = passedMonitor;
		//backupMonitor = new LinuxActiveWindowMonitor(myProcMonitor);
		myThread = new Thread(this);
		myThread.setName("WindowMonitor");
		myThread.start();
	}
	
	public void addWindowConsumer(ActiveWindowConsumer toAdd)
	{
		consumerList.add(toAdd);
	}
	
	public void interruptSleep(long diff)
	{
		usualDiff = diff;
		if(running && (myThread.getState() == Thread.State.WAITING || myThread.getState() == Thread.State.TIMED_WAITING))
		{
			myThread.interrupt();
		}
	}
	
	public ArrayList getTopWindow(long diff)
	{
		return getWindowList(diff);
	}
	
	public synchronized ArrayList getWindowList(long diff)
	{
		//System.out.println("Getting top window");
		ArrayList myReturn = new ArrayList();
		
		HashMap childMap = new HashMap();
		List<OSDesktopWindow> windows = os.getDesktopWindows(true);
		int maxOrder = 0;
		HashMap activeMap = new HashMap();
		
		//System.out.println("Number of windows detected: " + windows.size());
		
		for(int x = 0; x < windows.size(); x++)
		{
			if(windows.get(x).getTitle().isEmpty() || windows.get(x).getLocAndSize().getWidth() == 0 || windows.get(x).getLocAndSize().getHeight() == 0)
			{
				continue;
			}
			if(windows.get(x).getOrder() > maxOrder)
			{
				maxOrder = windows.get(x).getOrder();
			}
			
			OSDesktopWindow curWindow = windows.get(x);
			
			HashMap processInfo = myProcMonitor.getProcessInfo((int)curWindow.getOwningProcessId());
			
			if(processInfo == null)
			{
				System.out.println("Warning: Got window with no process: " + curWindow.getTitle());
				continue;
			}
			
			Rectangle windowRect = curWindow.getLocAndSize();
			
			HashMap windowMap = new HashMap();
			
			windowMap.put("WindowID", curWindow.getWindowId());
			windowMap.put("WindowTitle", curWindow.getTitle());
			
			//System.out.println(curWindow.getTitle());
			
			windowMap.put("WindowFirstClass", processInfo.get("COMMAND"));
			windowMap.put("WindowSecondClass", processInfo.get("ARGS"));
			windowMap.put("WindowPID", "" + curWindow.getOwningProcessId());
			windowMap.put("ProcessInfo", processInfo);
			windowMap.put("x", windowRect.getX());
			windowMap.put("y", windowRect.getY());
			windowMap.put("width", windowRect.getWidth());
			windowMap.put("height", windowRect.getHeight());
			
			windowMap.put("IsFocus", "0");
		    
			activeMap.put(curWindow.getOrder(), windowMap);
			
			if(childMap.containsKey(curWindow.getOwningProcessId()))
			{
				HashMap childWindow = (HashMap)childMap.get(curWindow.getOwningProcessId());
				childWindow.put("Parent", windowMap);
			}
			childMap.put(curWindow.getOwningProcessId(), windowMap);
			
			
		    myReturn.add(windowMap);
		}
		
		HashMap topMap = ((HashMap)activeMap.get(maxOrder));
		if(topMap != null)
		{
		while(topMap.containsKey("Parent"))
		{
			topMap = (HashMap) topMap.get("Parent");
		}
		topMap.put("IsFocus", "1");
		}
		
		
		//System.out.println("Returning:");
		//System.out.println(myReturn.size());
		
		//System.out.println("Comparing to cmd line monitor:");
		//HashMap backupMap = backupMonitor.getTopWindow(diff);
		//if(backupMap != null)
		//{
		//	System.out.println(backupMap.get("WindowTitle"));
		//}
		
		return myReturn;
	}

	@Override
	public void run()
	{
		running = true;
		
		while(running)
		{
			try
			{
				//System.out.println("Getting windows...");
				ArrayList toConsume = getWindowList(usualDiff);
				
				for(int x = 0; x < consumerList.size(); x++)
				{
					((ActiveWindowConsumer) consumerList.get(x)).consumeWindowList(toConsume);
				}
				//System.out.println("Done windows.");
				myThread.sleep(pollPeriod);
			}
			catch (InterruptedException e)
			{
				
			}
		}
	}
}
