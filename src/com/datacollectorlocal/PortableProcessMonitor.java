package com.datacollectorlocal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;

public class PortableProcessMonitor implements Runnable
{
	private Thread myThread = null;
	private boolean running = false;
	public static final String WIN_PROCESS_INFO = "ps -aux ";
	public int finalExitValue = 0;
	private int time = 0;
	private Start toFeed = null;
	
	public PortableProcessMonitor(int timeout)
	{
		time = timeout;
		myThread = new Thread(this);
		myThread.start();
	}
	
	public void setStart(Start nextStart)
	{
		toFeed = nextStart;
	}
	
	public HashMap toProcMap(ProcessHandle curProc)
	{
		HashMap myReturn = new HashMap();
		
		myReturn.put("USER", curProc.info().user().get());
		myReturn.put("PID", curProc.pid());
		myReturn.put("ALIVE", curProc.isAlive());
		myReturn.put("START_UNIVERSAL", curProc.info().startInstant().get());
		myReturn.put("CPU_DUR", curProc.info().totalCpuDuration().get());
		myReturn.put("COMMAND", curProc.info().command().get());
		myReturn.put("COMMAND_LINE", curProc.info().commandLine().get());
		ArrayList argList = new ArrayList(Arrays.asList(curProc.info().arguments().get()));
		myReturn.put("ARGS", argList);
		if(curProc.parent() != null && !curProc.parent().isEmpty())
		{
			ProcessHandle parent = curProc.parent().get();
			myReturn.put("PARENT", parent.pid());
		}
		
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
			
			Iterable<ProcessHandle> procIterable = procStream::iterator;
			for (ProcessHandle curProc : procIterable)
			{
				HashMap curProcMap = toProcMap(curProc);
				output.add(curProcMap);
			}
			
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
