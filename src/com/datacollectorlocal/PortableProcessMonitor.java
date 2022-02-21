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
import java.util.Map.Entry;
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
	
	private HashMap prevProcSet = new HashMap();
	
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
			//System.out.println("Running a triggered round");
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
			HashMap nextProcSet = new HashMap(); //(HashSet) prevProcSet.clone();
			
			int newCount = 0;
			int updateCount = 0;
			int neutralCount = 0;
			
			finalOutput = new ArrayList();
			
			// This gets all new processes, more efficient to just
			// loop rather than do the more elegant set compare
			// (requires constructing new set, maybe worth converting
			// this entire method to sets at some point)
			for(int x = 0; x < output.size(); x++)
			{
				// Prev map does not even have the key for this
				// process, so we know it is absolutely new.
				if(!prevProcSet.containsKey(((ProcessMap)output.get(x)).getKey()))
				{
					//System.out.println("New:");
					//System.out.println(output.get(x));
					//finalOutputMap.put(((ProcessMap) output.get(x)).getKey(), output.get(x));
					finalOutput.add(((ProcessMap)output.get(x)).clone());
					nextProcSet.put(((ProcessMap)output.get(x)).getKey(), output.get(x));
					
					newCount++;
				}
				// We know that this process is equal to the prev entry within
				// margin of error so we save the initial process so that we
				// calculate significance from the last changed value. This
				// is not a new entry so it does not need to be written.
				else if(prevProcSet.get(((ProcessMap)output.get(x)).getKey()).equals(output.get(x)))
				{
					nextProcSet.put(((ProcessMap)output.get(x)).getKey(), prevProcSet.get(((ProcessMap)output.get(x)).getKey()));
					
					neutralCount++;
				}
				// We know that there was a previous process entry, but the new
				// entry is significantly different CPU or MEM so we add it to
				// update.
				else
				{
					//finalOutputMap.put(((ProcessMap) output.get(x)).getKey(), output.get(x));
					finalOutput.add(((ProcessMap)output.get(x)).clone());
					nextProcSet.put(((ProcessMap)output.get(x)).getKey(), output.get(x));
					
					updateCount++;
				}
			}
			
			// Final output now has all new and changed values, but we still
			// need to figure out which processes have died.  We iterate
			// through the previous processes to see what is missing.
			
			int deathCount = 0;
			Iterator prevProcIter = prevProcSet.entrySet().iterator();
			while(prevProcIter.hasNext())
			{
				Entry nextEntry = (Entry) prevProcIter.next();
				ProcessMap curProc = (ProcessMap) nextEntry.getValue();
				String curKey = (String) nextEntry.getKey();
				
				if(!nextProcSet.containsKey(curKey))
				{
					// The new, updated, or no-change set does not
					// have this process, so it must have died.
					curProc = (ProcessMap) curProc.clone();
					curProc.put("STAT", "DIED");
					finalOutput.add(curProc);
					
					deathCount++;
				}
			}
			
			//System.out.println("Total Count: " + output.size());
			
			//System.out.println("New Count: " + newCount);
			
			//System.out.println("Update Count: " + updateCount);
			
			//System.out.println("No Change Count: " + neutralCount);
			
			//System.out.println("Death Count: " + deathCount);
			
			//System.out.println("Final Count: " + finalOutput.size());
			
			//System.out.println();
			
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
		PortableProcessMonitor myMonitor = new PortableProcessMonitor(100, true, true);
	}
}
