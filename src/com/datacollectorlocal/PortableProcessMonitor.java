package com.datacollectorlocal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ProcessHandle.Info;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

public class PortableProcessMonitor implements Runnable
{
	private Thread myThread = null;
	private boolean running = false;
	private int time = 0;
	private Start toFeed = null;
	private String osName = "";
	//private LinuxProcessMonitor linuxMonitor;
	
	private SystemInfo si;
	private HardwareAbstractionLayer hal;
	private OperatingSystem os;
	
	public PortableProcessMonitor(int timeout)
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		//osName = System.getProperty("os.name").toLowerCase();
		//if(osName.contains("linux"))
		//{
		//	linuxMonitor = new LinuxProcessMonitor();
		//}
		time = timeout;
		myThread = new Thread(this);
		myThread.start();
	}
	
	public PortableProcessMonitor()
	{
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
	}
	
	public HashMap getProcessInfo(int pid)
	{
		HashMap toReturn = null;
		
		OSProcess myProc = os.getProcess(pid);
		toReturn = toProcMap(myProc);
		
		return toReturn;
	}
	
	public void setStart(Start nextStart)
	{
		toFeed = nextStart;
	}
	
	public HashMap toProcMap(OSProcess curProc)
	{
		
		
		HashMap myReturn = new HashMap();
		
		myReturn.put("USER", curProc.getUser());
		myReturn.put("PID", curProc.getProcessID());
		myReturn.put("%CPU", 100d * (curProc.getKernelTime() + curProc.getUserTime()) / curProc.getUpTime());
		myReturn.put("%MEM", 100d * curProc.getResidentSetSize() / hal.getMemory().getTotal());
		myReturn.put("VSZ", FormatUtil.formatBytes(curProc.getVirtualSize()));
		myReturn.put("RSS", FormatUtil.formatBytes(curProc.getResidentSetSize()));
		myReturn.put("TTY", "");
		myReturn.put("STAT", curProc.getState().name());
		myReturn.put("START", curProc.getStartTime());
		myReturn.put("TIME", curProc.getUpTime());
		String commandLine = curProc.getCommandLine();
		String[] splited = commandLine.split("\\s+");
		myReturn.put("COMMAND", splited[0]);
		ArrayList args = new ArrayList();
		Collections.addAll(args, splited);
		args.remove(0);
		if(args.size() > 0)
		{
			myReturn.put("ARGS", args);
		}
		
		/*
		myReturn.put("PID", "" + curProc.pid());
		Info info = curProc.info();
		
		String command = "";
		if(info.command().isPresent())
		{
			command = info.command().get();
		}
		myReturn.put("COMMAND", command);
		
		String[] arguments = null;
		ArrayList args = new ArrayList();
		if(info.arguments().isPresent())
		{
			arguments = info.arguments().get();
			Collections.addAll(args, arguments);
		}
		myReturn.put("ARGS", args);
		
		String user = "";
		if(info.user().isPresent())
		{
			user = info.user().get();
		}
		myReturn.put("USER", user);
		
		Instant now = Instant.now();
		Instant startInstant = null;
		long diff = 0;
		if(info.startInstant().isPresent())
		{
			startInstant = info.startInstant().get();
			diff = now.toEpochMilli() - startInstant.toEpochMilli();
		}
		myReturn.put("START", "" + startInstant.toEpochMilli());
		
		Duration totalCpuDuration = null;
		double cpuUse = 0;
		if(info.totalCpuDuration().isPresent())
		{
			totalCpuDuration = info.totalCpuDuration().get();
			cpuUse = (100 * ((double)totalCpuDuration.toMillis() / (double)diff));
		}
		myReturn.put("%CPU", "" + cpuUse);
		
		*/
		return myReturn;
	}

	@Override
	public void run()
	{
		running = true;
		while(running)
		{
			Stream<ProcessHandle> procStream = ProcessHandle.allProcesses();
			
			ArrayList output = new ArrayList();
			
			List<OSProcess> procs = os.getProcesses();
			for(int x=0; x < procs.size(); x++)
			{
				OSProcess curProc = procs.get(x);
				if(curProc != null)
				{
					HashMap curProcMap = toProcMap(curProc);
					output.add(curProcMap);
				}
			}
			//HashMap procLookupMap = new HashMap();
			
			/*Iterable<ProcessHandle> procIterable = procStream::iterator;
			for (ProcessHandle curProc : procIterable)
			{
				HashMap curProcMap = toProcMap(curProc);
				output.add(curProcMap);
				HashMap id = new HashMap();
				id.put(curProcMap.get("USER"), "USER");
				id.put(curProcMap.get("PID"), "PID");
				procLookupMap.put(id, curProcMap);
				if(osName.contains("linux"))
				{
					ArrayList linuxResults = linuxMonitor.getProcesses();
				}
			}*/
			
			toFeed.monitorProcesses(output);
			try
			{
				Thread.currentThread().sleep(time);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public void stop()
	{
		running = false;
	}
	
	public static void main(String[] args)
	{
		PortableProcessMonitor myMonitor = new PortableProcessMonitor(2000);
	}
}
