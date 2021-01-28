package com.datacollectorlocal;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OperatingSystem;

public class PortableActiveWindowMonitor
{
	
	private SystemInfo si;
	private HardwareAbstractionLayer hal;
	private OperatingSystem os;
	private PortableProcessMonitor myProcMonitor;
	
	public PortableActiveWindowMonitor()
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		myProcMonitor = new PortableProcessMonitor();
	}
	
	public ArrayList getTopWindow(long diff)
	{
		return getWindowList(diff);
	}
	
	public ArrayList getWindowList(long diff)
	{
		ArrayList myReturn = new ArrayList();
		
		HashMap childMap = new HashMap();
		List<OSDesktopWindow> windows = os.getDesktopWindows(true);
		int maxOrder = 0;
		HashMap activeMap = new HashMap();
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
			Rectangle windowRect = curWindow.getLocAndSize();
			
			HashMap windowMap = new HashMap();
			
			windowMap.put("WindowID", curWindow.getWindowId());
			windowMap.put("WindowTitle", curWindow.getTitle());
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
		while(topMap.containsKey("Parent"))
		{
			topMap = (HashMap) topMap.get("Parent");
		}
		topMap.put("IsFocus", "1");
		
		return myReturn;
	}
}
