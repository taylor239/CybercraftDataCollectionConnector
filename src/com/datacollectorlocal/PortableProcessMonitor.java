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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OSThread;
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
	
	private HashSet prevProcSet = new HashSet();
	
	private SystemInfo si;
	private HardwareAbstractionLayer hal;
	private OperatingSystem os;
	
	private HashMap prevParentMap = new HashMap();
	
	private boolean diff;
	
	private boolean threadGranularity;
	
	
	public PortableProcessMonitor(int timeout, boolean diffMode, boolean threads)
	{
		threadGranularity = threads;
		diff = diffMode;
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		//osName = System.getProperty("os.name").toLowerCase();
		//if(osName.contains("linux"))
		//{
		//	linuxMonitor = new LinuxProcessMonitor();
		//}
		time = timeout;
		myThread = new Thread(this, "processMonitorThread");
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
		
		
		HashMap myReturn = new ProcessMap();
		
		myReturn.put("USER", curProc.getUser());
		myReturn.put("PID", curProc.getProcessID());
		myReturn.put("%CPU", 100d * (curProc.getKernelTime() + curProc.getUserTime()) / curProc.getUpTime());
		myReturn.put("%MEM", 100d * curProc.getResidentSetSize() / hal.getMemory().getTotal());
		myReturn.put("VSZ", (curProc.getVirtualSize()));
		myReturn.put("RSS", (curProc.getResidentSetSize()));
		myReturn.put("TTY", "");
		myReturn.put("STAT", curProc.getState().name());
		myReturn.put("START", curProc.getStartTime());
		myReturn.put("TIME", curProc.getUpTime());
		String commandLine = curProc.getCommandLine();
		myReturn.put("PARENTPID", curProc.getParentProcessID());
		String[] splited = commandLine.split(" (?=\")|(?<=\")\\s");
		myReturn.put("COMMAND", curProc.getName());
		ArrayList args = new ArrayList();
		Collections.addAll(args, splited);
		//args.remove(0);
		if(args.size() > 0)
		{
			myReturn.put("ARGS", args);
		}
		if(threadGranularity)
		{
			ArrayList procThreadList = new ArrayList();
			List<OSThread> curThreadList = curProc.getThreadDetails();
			for(int x = 0; x < curThreadList.size(); x++)
			{
				OSThread curThread = curThreadList.get(x);
				HashMap curThreadMap = new ProcessMap();
				
				curThreadMap.put("NAME", curThread.getName());
				curThreadMap.put("TID", curThread.getThreadId());
				//This PID may be redundant
				//curThreadMap.put("PID", curThread.getOwningProcessId());
				curThreadMap.put("TSTATE", curThread.getState());
				curThreadMap.put("T%CPU", 100d * (curThread.getKernelTime() + curThread.getUserTime()) / curThread.getUpTime());
				curThreadMap.put("MINORFAULT", curThread.getMinorFaults());
				curThreadMap.put("MAJORFAULT", curThread.getMajorFaults());
				curThreadMap.put("TSTART", curThread.getStartTime());
				curThreadMap.put("PRIORITY", curThread.getPriority());
				
				curThreadMap.put("PARENT", myReturn);
				
				procThreadList.add(curThreadMap);
			}
			myReturn.put("THREADS", procThreadList);
		}
		
		if(!prevParentMap.containsKey(myReturn.get("PARENTPID")))
		{
			runRound();
		}
		if(prevParentMap.containsKey(myReturn.get("PARENTPID")))
		{
			HashMap parentProc = (HashMap) prevParentMap.get(myReturn.get("PARENTPID"));
			//System.out.println(parentProc.get("USER"));
			myReturn.put("PARENTUSER", parentProc.get("USER"));
			myReturn.put("PARENTSTART", parentProc.get("START"));
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
	
	public HashMap toProcMapNoCheck(OSProcess curProc)
	{
		
		
		HashMap myReturn = new ProcessMap();
		
		myReturn.put("USER", curProc.getUser());
		myReturn.put("PID", curProc.getProcessID());
		myReturn.put("%CPU", 100d * (curProc.getKernelTime() + curProc.getUserTime()) / curProc.getUpTime());
		myReturn.put("%MEM", 100d * curProc.getResidentSetSize() / hal.getMemory().getTotal());
		myReturn.put("VSZ", (curProc.getVirtualSize()));
		myReturn.put("RSS", (curProc.getResidentSetSize()));
		myReturn.put("TTY", "");
		myReturn.put("STAT", curProc.getState().name());
		myReturn.put("START", curProc.getStartTime());
		myReturn.put("TIME", curProc.getUpTime());
		String commandLine = curProc.getCommandLine();
		myReturn.put("PARENTPID", curProc.getParentProcessID());
		String[] splited = commandLine.split(" (?=\")|(?<=\")\\s");
		myReturn.put("COMMAND", curProc.getName());
		ArrayList args = new ArrayList();
		Collections.addAll(args, splited);
		//args.remove(0);
		if(args.size() > 0)
		{
			myReturn.put("ARGS", args);
		}
		if(threadGranularity)
		{
			ArrayList procThreadList = new ArrayList();
			List<OSThread> curThreadList = curProc.getThreadDetails();
			for(int x = 0; x < curThreadList.size(); x++)
			{
				OSThread curThread = curThreadList.get(x);
				HashMap curThreadMap = new ProcessMap();
				
				curThreadMap.put("NAME", curThread.getName());
				curThreadMap.put("TID", curThread.getThreadId());
				//This PID may be redundant
				//curThreadMap.put("PID", curThread.getOwningProcessId());
				curThreadMap.put("TSTATE", curThread.getState());
				curThreadMap.put("T%CPU", 100d * (curThread.getKernelTime() + curThread.getUserTime()) / curThread.getUpTime());
				curThreadMap.put("MINORFAULT", curThread.getMinorFaults());
				curThreadMap.put("MAJORFAULT", curThread.getMajorFaults());
				curThreadMap.put("TSTART", curThread.getStartTime());
				curThreadMap.put("PRIORITY", curThread.getPriority());
				
				curThreadMap.put("PARENT", myReturn);
				
				procThreadList.add(curThreadMap);
			}
			myReturn.put("THREADS", procThreadList);
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
	
	public void runRound()
	{
		//System.out.println("Start new proc round");
		
		long roundTime = System.currentTimeMillis();
		
		Stream<ProcessHandle> procStream = ProcessHandle.allProcesses();
		
		ArrayList output = new ArrayList();
		HashMap parentLookupMap = new HashMap();
		
		List<OSProcess> procs = os.getProcesses();
		for(int x=0; x < procs.size(); x++)
		//for(int x=0; x < 1; x++)
		{
			OSProcess curProc = procs.get(x);
			if(curProc != null)
			{
				HashMap curProcMap = toProcMapNoCheck(curProc);
				output.add(curProcMap);
				//HashMap keyMap = new HashMap();
				parentLookupMap.put(curProcMap.get("PID"), curProcMap);
			}
		}
		for(int x=0; x<output.size(); x++)
		{
			HashMap curProcMap = (HashMap)output.get(x);
			if(parentLookupMap.containsKey(curProcMap.get("PARENTPID")))
			{
				HashMap parentProc = (HashMap) parentLookupMap.get(curProcMap.get("PARENTPID"));
				//System.out.println(parentProc.get("USER"));
				curProcMap.put("PARENTUSER", parentProc.get("USER"));
				curProcMap.put("PARENTSTART", parentProc.get("START"));
			}
		}
		prevParentMap = parentLookupMap;
		
		ArrayList finalOutput = null;
		
		if(diff)
		{
			HashSet nextProcSet = (HashSet) prevProcSet.clone();
			//System.out.println("Total: " + output.size());
			//System.out.println("Old: " + prevProcSet.size());
			
			/*
			if(prevProcSet.iterator().hasNext())
			{
				HashMap firstMap = (HashMap)output.get(0);
				HashMap secondMap = (HashMap)(prevProcSet.iterator().next());
				System.out.println(firstMap.keySet());
				System.out.println(secondMap.keySet());
				System.out.println(firstMap.getClass());
				System.out.println(secondMap.getClass());
				Iterator curIter = firstMap.keySet().iterator();
				while(curIter.hasNext())
				{
					String nextKey = (String) curIter.next();
					System.out.println(nextKey);
					System.out.println(firstMap.get(nextKey));
					System.out.println(secondMap.get(nextKey));
				}
			}
			*/
			
			finalOutput = new ArrayList();
			HashMap finalOutputMap = new HashMap();
			
			// This gets all new processes, more efficient to just
			// loop rather than do the more elegant set compare
			// (requires constructing new set, maybe worth converting
			// this entire method to sets at some point)
			for(int x = 0; x < output.size(); x++)
			{
				if(!prevProcSet.contains(output.get(x)))
				{
					//System.out.println("New:");
					//System.out.println(output.get(x));
					finalOutputMap.put(((ProcessMap) output.get(x)).getKey(), output.get(x));
					finalOutput.add(output.get(x));
					nextProcSet.add(output.get(x));
				}
			}
			
			//System.out.println("New: " + finalOutput.size());
			
			// This gives a prevProcSet that is processes that died or
			// had significantly different CPU or MEM.
			//prevProcSet.removeAll(output);
			
			// Let's clone this here to keep older, unchanged values.
			// This prevents small CPU/MEM creep.
			
			for(int x=0; x < output.size(); x++)
			{
				if(prevProcSet.contains(output.get(x)))
				{
					prevProcSet.remove(output.get(x));
				}
			}
			
			
			//System.out.println("Died: " + prevProcSet.size());
			//System.out.println(prevProcSet);
			
			Iterator prevProcIter = prevProcSet.iterator();
			int diedCount = 0;
			while(prevProcIter.hasNext())
			{
				
				ProcessMap curProc = (ProcessMap) prevProcIter.next();
				
				if(finalOutputMap.containsKey(curProc.getKey()))
				{
					nextProcSet.remove(curProc);
					nextProcSet.add(curProc);
					//System.out.println("Updated: " + curProc.getKey() + " " + curProc.get("COMMAND"));
				}
				else
				{
					diedCount++;
					
					nextProcSet.remove(curProc);
					
					curProc = (ProcessMap) curProc.clone();
					
					curProc.put("STAT", "DIED");
					finalOutput.add(curProc);
					//System.out.println("Died: " + curProc.getKey() + " " + curProc.get("COMMAND"));
				}
			}
			
			//System.out.println("Died Count: " + diedCount);
			
			//System.out.println("Final: " + finalOutput.size());
			
			prevProcSet = nextProcSet;
		}
		else
		{
			finalOutput = output;
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
		if(toFeed != null)
		{
			toFeed.monitorProcesses(finalOutput);
		}
		
		//prevProcSet = new HashSet(output);
		
		roundTime = System.currentTimeMillis() - roundTime;
		if(toFeed != null)
		{
		if(diff)
		{
			toFeed.recordMetric("Process Monitor", roundTime, "ms", prevProcSet.size(), "count");
		}
		else
		{
			toFeed.recordMetric("Process Monitor No Diff", roundTime, "ms", output.size(), "count");
		}
		}
		
		try
		{
			Thread.currentThread().sleep(time);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		running = true;
		while(running)
		{
			runRound();
		}
	}
	
	public void stop()
	{
		running = false;
	}
	
	public static void main(String[] args)
	{
		PortableProcessMonitor myMonitor = new PortableProcessMonitor(5000, true, true);
	}
}
