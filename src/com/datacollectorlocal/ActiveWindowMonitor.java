package com.datacollectorlocal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

public class ActiveWindowMonitor
{
	public static final String WIN_ID_CMD = "xprop -root | grep " + "\"_NET_ACTIVE_WINDOW(WINDOW)\"" + "|cut -d ' ' -f 5";
	public static final String WIN_INFO_CMD_PREFIX = "xwininfo -id ";
	public static final String WIN_INFO_CMD_MID = " |awk \'BEGIN {FS=\"\\\"\"}/xwininfo: Window id/{print $2}\' | sed \'s/-[^-]*$//g\'";

	public static final String WIN_INFO_GET_X = " -shape)";
	
	public static final String WIN_INFO_START = "xprop -id ";
	public static final String WIN_PID_FROM_XID_END = " _NET_WM_PID";
	public static final String WIN_CLASS_FROM_XID_END = " WM_CLASS";
	public static final String WIN_PROCESS_INFO = "ps -up ";
	public int finalExitValue = 0;
	
	
	public ActiveWindowMonitor()
	{
		
	}
	
	public HashMap getTopWindow()
	{
		//System.out.println("Getting window info");
		HashMap myReturn = new HashMap();
		long initTime = System.currentTimeMillis();
		String[] tmpOutput = execShellCmd(WIN_ID_CMD);
		//for(int x=0; x<tmpOutput.length; x++)
		{
			//System.out.println(tmpOutput[x]);
		}
		String winId = tmpOutput[1];
		if(winId.equals("0x0") || winId.length() <= 8)
		{
			winId = "0x1000010";
		}
	    String winInfoMcd = windowInfoCmd(winId);
	    String windowTitle = "";
	    if(winInfoMcd != null)
	    {
	    	windowTitle = execShellCmd(winInfoMcd)[1];
	    }
	    
	    //System.out.println(windowTitle);
	    
	    String windowInfo = execShellCmd("echo $(" + WIN_INFO_CMD_PREFIX + winId + WIN_INFO_GET_X)[1];
	    int[] windowCoords = getCoords(windowInfo);
	    //System.out.println(windowCoords[0] + ", " + windowCoords[1] + ", " + windowCoords[2] + ", " + windowCoords[3] + ", ");
	    //System.out.println(windowInfo);
	    
	    tmpOutput = execShellCmd(WIN_INFO_START + winId + WIN_CLASS_FROM_XID_END);
	    if(!tmpOutput[0].equals("0"))
	    {
	    	return null;
	    }
	    String[] windowClass = separateClass(tmpOutput[1]);
	    String tmpStr = execShellCmd(WIN_INFO_START + winId + WIN_PID_FROM_XID_END)[1];
	    String windowPID = "";
	    if(tmpStr != null && !tmpStr.isEmpty() && tmpStr.length() > 23)
	    {
	    	windowPID = tmpStr.substring(24);
	    }
	    HashMap processInfo = separatePS(execShellCmd(WIN_PROCESS_INFO + windowPID)[1]);
	    /*System.out.println("process time: " + (System.currentTimeMillis() - initTime));
	    System.out.println("window title is: "+ windowTitle);
	    System.out.println("window info is: " + winInfoMcd);
	    System.out.println("window id: "+ winId);
	    System.out.println("window first class: " + windowClass[0]);
	    System.out.println("window second class: " + windowClass[1]);
	    System.out.println("window pid: " + windowPID);
	    System.out.println("window process info: " + processInfo);
	    */
	    myReturn.put("WindowID", winId);
	    myReturn.put("WindowTitle", windowTitle);
	    myReturn.put("WindowFirstClass", windowClass[0]);
	    myReturn.put("WindowSecondClass", windowClass[1]);
	    myReturn.put("WindowPID", windowPID);
	    myReturn.put("ProcessInfo", processInfo);
	    myReturn.put("x", windowCoords[0]);
	    myReturn.put("y", windowCoords[1]);
	    myReturn.put("width", windowCoords[2]);
	    myReturn.put("height", windowCoords[3]);
	    
	    //System.out.println("process time: " + (System.currentTimeMillis() - initTime));
	    return myReturn;
	}
	
	
	public int[] getCoords(String input)
	{
		//System.out.println(input);
		int[] myReturn = new int[4];
		
		int mode = 0;
		
		String tmp = "";
		for(int x=0; x<input.length(); x++)
		{
			if(mode == 0)
			{
				if(input.charAt(x) == ' ' && input.charAt(x - 1) == ':' && input.charAt(x - 2) == 'X')
				{
					mode = 1;
				}
			}
			else if(mode == 1)
			{
				if(input.charAt(x) != ' ')
				{
					tmp += input.charAt(x);
				}
				else
				{
					myReturn[0] = new Integer(tmp);
					tmp = "";
					mode = 2;
				}
			}
			else if(mode == 2)
			{
				if(input.charAt(x) == ' ' && input.charAt(x - 1) == ':' && input.charAt(x - 2) == 'Y')
				{
					mode = 3;
				}
			}
			else if(mode == 3)
			{
				if(input.charAt(x) != ' ')
				{
					tmp += input.charAt(x);
				}
				else
				{
					myReturn[1] = new Integer(tmp);
					tmp = "";
					mode = 4;
				}
			}
			else if(mode == 4)
			{
				if(input.charAt(x) == ' ' && input.charAt(x - 1) == ':' && input.charAt(x - 2) == 'h')
				{
					mode = 5;
				}
			}
			else if(mode == 5)
			{
				if(input.charAt(x) != ' ')
				{
					tmp += input.charAt(x);
				}
				else
				{
					myReturn[2] = new Integer(tmp);
					tmp = "";
					mode = 6;
				}
			}
			else if(mode == 6)
			{
				if(input.charAt(x) == ' ' && input.charAt(x - 1) == ':' && input.charAt(x - 2) == 't')
				{
					mode = 7;
				}
			}
			else if(mode == 7)
			{
				if(input.charAt(x) != ' ')
				{
					tmp += input.charAt(x);
				}
				else
				{
					myReturn[3] = new Integer(tmp);
					tmp = "";
					mode = 8;
					break;
				}
			}
		}
		
		return myReturn;
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
	
	
	
	public String[] separateClass(String input)
	{
		String myReturn[] = new String[2];
		
		input = input.substring(20, input.length() - 1);
		
		boolean onFirst = true;
		boolean inMiddle = true;
		myReturn[0] = "";
		myReturn[1] = "";
		for(int x=0; x<input.length(); x++)
		{
			if(onFirst)
			{
				if(input.charAt(x) == '"')
				{
					onFirst = false;
				}
				else
				{
					myReturn[0] += input.charAt(x);
				}
			}
			else if(inMiddle)
			{
				if(input.charAt(x) == '"')
				{
					inMiddle = false;
				}
			}
			else
			{
				myReturn[1] += input.charAt(x);
			}
		}
		
		//myReturn[0] = input;
		
		return myReturn;
	}
	public synchronized String[] execShellCmd(String cmd){
		//System.out.println("Executing " + cmd);
	    try {  

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
	        while ((line = buf.readLine()) != null) {
	            output = line;
	        }
	        //System.out.println("Output: " + output);
	        outputArray[1] = output;
	        return outputArray;
	    } catch (Exception e) {  
	        System.out.println(e);
	        return null;
	    }  
	}

	public String windowInfoCmd(String winId){
	    if(null!=winId && !"".equalsIgnoreCase(winId)){
	        return WIN_INFO_CMD_PREFIX+winId +WIN_INFO_CMD_MID;
	    }
	    return null;
	}
}
