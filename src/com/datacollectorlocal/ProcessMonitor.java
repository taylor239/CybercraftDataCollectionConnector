package com.datacollectorlocal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class ProcessMonitor implements Runnable
{
	private Thread myThread = null;
	private boolean running = false;
	public static final String WIN_PROCESS_INFO = "ps -aux ";
	public int finalExitValue = 0;
	private int time = 0;
	private Start toFeed = null;
	
	public ProcessMonitor(int timeout)
	{
		time = timeout;
		myThread = new Thread(this);
		myThread.start();
	}
	
	public void setStart(Start nextStart)
	{
		toFeed = nextStart;
	}
	
	public synchronized String[] execShellCmd(String cmd)
	{
	    try
	    {  

	        Runtime runtime = Runtime.getRuntime();  
	        Process process = runtime.exec(new String[] { "/bin/bash", "-c", cmd });  
	        int exitValue = process.waitFor();  
	        finalExitValue = exitValue;
	        String[] outputArray = new String[2];
	        if(finalExitValue != 0)
	        {
	        	System.out.println(cmd);
	        	System.out.println("exit value: " + exitValue);
	        }
	        outputArray[0] = ((Integer)finalExitValue).toString();
	        BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));  
	        String line = "";  
	        String output = "";
	        String lineSeparator = "";
	        while ((line = buf.readLine()) != null) {
	            output += lineSeparator + line;
	            lineSeparator = "\n";
	        }
	        outputArray[1] = output;
	        return outputArray;
	    }
	    catch (Exception e)
	    {  
	        System.out.println(e);
	        return null;
	    }  
	}
	
	public HashMap separatePS(String input)
	{
		//System.out.println(input);
		HashMap myReturn = new HashMap();
		
		int wordCount = 0;
		
		String tmpString = "";
		for(int x=0; x<=input.length(); x++)
		{
			if(x >= input.length() || input.charAt(x) == ' ')
			{
				//while(input.charAt(x) == ' ' && x<input.length())
				//{
				//	x++;
				//}
				if(wordCount == 0)
				{
					myReturn.put("USER", tmpString);
				}
				else if(wordCount == 1)
				{
					myReturn.put("PID", tmpString);
				}
				else if(wordCount == 2)
				{
					myReturn.put("%CPU", tmpString);
				}
				else if(wordCount == 3)
				{
					myReturn.put("%MEM", tmpString);
				}
				else if(wordCount == 4)
				{
					myReturn.put("VSZ", tmpString);
				}
				else if(wordCount == 5)
				{
					myReturn.put("RSS", tmpString);
				}
				else if(wordCount == 6)
				{
					myReturn.put("TTY", tmpString);
				}
				else if(wordCount == 7)
				{
					myReturn.put("STAT", tmpString);
				}
				else if(wordCount == 8)
				{
					myReturn.put("START", tmpString);
				}
				else if(wordCount == 9)
				{
					myReturn.put("TIME", tmpString);
				}
				else if(wordCount == 10)
				{
					myReturn.put("COMMAND", tmpString);
				}
				else if(wordCount >= 11)
				{
					ArrayList args;
					if(myReturn.containsKey("ARGS"))
					{
						args = (ArrayList) myReturn.get("ARGS");
					}
					else
					{
						args = new ArrayList();
					}
					args.add(tmpString);
					myReturn.put("ARGS", args);
						
				}
				
				while(x + 1 < input.length() && input.charAt(x + 1) == ' ')
				{
					x++;
				}
				
				wordCount++;
				tmpString = "";
			}
			else
			{
				if(x <= input.length())
				{
					tmpString += input.charAt(x);
				}
			}
		}
		
		return myReturn;
	}

	@Override
	public void run()
	{
		running = true;
		while(running)
		{
			//System.out.println("Getting window info");
			String[] psOutput = execShellCmd(WIN_PROCESS_INFO);
			//for(int x=0; x<psOutput.length; x++)
			//{
				//System.out.println(psOutput[x]);
			//}
			Scanner myScanner = new Scanner(psOutput[1]);
			ArrayList output = new ArrayList();
			while(myScanner.hasNextLine())
			{
				String curLine = myScanner.nextLine();
				//System.out.println(curLine);
				HashMap processInfo = separatePS(curLine);
				//System.out.println(processInfo);
				output.add(processInfo);
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
		ProcessMonitor myMonitor = new ProcessMonitor(2000);
	}
}
