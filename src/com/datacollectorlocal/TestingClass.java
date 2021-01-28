package com.datacollectorlocal;

import java.lang.ProcessHandle.Info;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OperatingSystem;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;


@Platform(include="winuser.h")

public class TestingClass
{
	static SystemInfo si;
	static HardwareAbstractionLayer hal;
	static OperatingSystem os;
	
	public static void main(String[] args)
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		
		
		PortableProcessMonitor myProcMonitor = new PortableProcessMonitor();
		System.out.println(os);
		
		HashMap parentWindow = new HashMap();
		//System.out.println(hal.getComputerSystem());
		List<OSDesktopWindow> windows = os.getDesktopWindows(true);
		int maxOrder = 0;
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
			System.out.println(windows.get(x).getOrder());
			if(parentWindow.containsKey(windows.get(x).getOwningProcessId()))
			{
				System.out.println("Has child");
				System.out.println(windows.get(x));
				System.out.println(myProcMonitor.getProcessInfo((int)windows.get(x).getOwningProcessId()));
			}
			parentWindow.put(windows.get(x).getOwningProcessId(), null);
			//System.out.println(windows.get(x));
			//System.out.println(windows.get(x).getCommand());
			//System.out.println(myProcMonitor.getProcessInfo((int)windows.get(x).getOwningProcessId()));
			
		}
		System.out.println(maxOrder);
	}
}
