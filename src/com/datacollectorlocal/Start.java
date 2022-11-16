package com.datacollectorlocal;


import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;




















import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
//import org.apache.tomcat.jni.Thread;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.google.gson.Gson;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;



public class Start implements NativeMouseInputListener, NativeKeyListener, Runnable, ScreenshotListener, PauseListener, MetricListener, ActiveWindowConsumer
{
	private VideoFrameCompressor myFrameCompressor;
	private int keyFrameRate = 10;
	
	private int shortPoll = 50;
	
	private boolean verbose = false;
	
	private boolean metrics = false;
	
	private int numCompareThreads = 4;
	
	private Thread myThread;
	private static ArrayList windowsToClose = new ArrayList();
	private PortableActiveWindowMonitor myMonitor;// = new PortableActiveWindowMonitor();
	private String windowID = "";
	private String windowName = "";
	private double windowX = -1; 
	private double windowY = -1;
	private double windowWidth = -1;
	private double windowHeight = -1;
	private HashMap currentWindowData = null;
	private ConcurrentLinkedQueue windowsToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue clicksToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue screenshotsToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue keysToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue processesToWrite = new ConcurrentLinkedQueue();
	private ConcurrentLinkedQueue metricsToWrite = new ConcurrentLinkedQueue();
	
	private String imageCompressionType = "jpg";
	private double imageCompressionFactor = .5;
	private String diffMethod = "none";
	
	private int screenshotTimeout = 10000;
	private SimplerScreenshotGenerator myGenerator;
	private int processTimeout = 20000;
	private PortableProcessMonitor myProcessMonitor;
	private boolean running = false;
	private TestingConnectionSource connectionSource = new TestingConnectionSource();
	private static TaskInputGUI myTaskGUI;
	private boolean paused = false;
	
	private boolean threads = false;
	
	private DataAggregator curAggregator = null;
	
	private String userName = "default";
	private String adminEmail = "default";
	private String sessionToken = "";
	private String eventName = "";
	
	private static String curVersion = "";
	
	private static String serverAddress = "localhost:8080";
	
	private static long timeDifference = 0;
	
	private static final String jarPath = (Start.class.getProtectionDomain().getCodeSource().getLocation().getPath()).toString();
	private static final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
	private static Start myStart = null;
	private boolean cleanedUp = false;
	
	private static String[] programArgs = new String[0];
    private static boolean hasBeenLaunched = false;
    
	
	private SystemInfo si;
	private HardwareAbstractionLayer hal;
	private OperatingSystem os;
	
	private boolean logging = false;
	
	public Start(String user, String event, String admin, int screenshot, int process, boolean toLog, boolean toMetric, boolean threadGranularity, String imageCompression, double compressionAmount, String diff)
	{
		imageCompressionType = imageCompression;
		imageCompressionFactor = compressionAmount;
		diffMethod = diff;
		
		if(diffMethod.equals("diff"))
		{
			myFrameCompressor = new DiffVideoCompressor(keyFrameRate, numCompareThreads, imageCompressionType, imageCompressionFactor);
		}
		if(diffMethod.equals("boundrect"))
		{
			myFrameCompressor = new BoundVideoCompressor(keyFrameRate, imageCompressionType, imageCompressionFactor);
		}
		
		threads = threadGranularity;
		metrics = toMetric;
		logging = toLog;
		si = new SystemInfo();
		hal = si.getHardware();
		os = si.getOperatingSystem();
		
		
		screenshotTimeout = screenshot;
		processTimeout = process;
		
		sessionToken = UUID.randomUUID().toString();
		userName = user;
		adminEmail = admin;
		if(event != null)
		{
			eventName = event;
		}
		
		
		try
		{
			Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
			if(!logging)
			{
				logger.setLevel(Level.OFF);
			}
			GlobalScreen.registerNativeHook();
			GlobalScreen.addNativeMouseListener(this);
			GlobalScreen.addNativeKeyListener(this);
		}
		catch(NativeHookException e)
		{
			e.printStackTrace();
		}
		myGenerator = new SimplerScreenshotGenerator(screenshotTimeout);
		myGenerator.addMetricListener(this);
		myGenerator.addScreenshotListener(this);
		myProcessMonitor = new PortableProcessMonitor(processTimeout, true, threads);
		myMonitor = new PortableActiveWindowMonitor(myProcessMonitor);
		myMonitor.addWindowConsumer(this);
		myProcessMonitor.setStart(this);
		
		myThread = new Thread(this, "mainMonitorThread");
		myThread.start();
	}
	
	/**
	 * Updates and restarts the software.  Currently needs work due to dependency drift.
	 */
	public static synchronized void update()
	{
		boolean update = checkVersion();
		if(update)
		{
			System.out.println("Updated, now restarting");
			restart();
		}
	}
	
	/**
	 * Restarts this application with no OS dependency.  Unknown if still working;
	 * this was used by the updater component but the update checking currently needs
	 * fixing before this is needed.
	 */
	public static void restart()
	{
		File currentFile = new File(jarPath);
		if(!currentFile.isFile())
		{
			System.out.println("Warning: Not running from JAR");
		}
		else
		{
			if(myStart != null)
			{
				myStart.stop();
				while(myStart.cleanedUp == false)
				{
					try
					{
						Thread.sleep(500);
					}
					catch(InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			}
			System.out.println("Current execution cleaned up");
			
			
			ArrayList<String> processList = new ArrayList<String>();
			processList.add(javaBin);
			processList.add("-jar");
			processList.add(jarPath);
			for(String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments())
			{
				processList.add(jvmArg);
			}
			for(String arg : programArgs)
			{
				processList.add(arg);
			}
			
			System.out.println("Launching with: " + processList);
			ProcessBuilder processBuilder = new ProcessBuilder(processList);
			processBuilder.inheritIO();
			try
			{
				Process myProcess = processBuilder.start();
				System.out.println("Done launching");
				myProcess.waitFor();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			System.exit(0);
		}
	}
	
	/**
	 * Checks to see if the current jar file is the latest version on the server.
	 * Currently does not work due do dependency drift.  (Why did the API remove
	 * its MD5 functions?  And why not replace them with other standard hashes?
	 * Could fix with an external hash library)
	 * @return true if the running version matches the server version, false otherwise.
	 */
	public static boolean checkVersion()
	{
		boolean updated = false;
		File currentFile = new File(jarPath);
		//System.out.println(jarPath);
		//System.out.println(Arrays.toString(currentFile.list()));
		if(!currentFile.isFile())
		{
			System.out.println("Warning: Not running from JAR");
		}
		else
		{
			try
			{
				MessageDigest md = MessageDigest.getInstance("MD5");
				if(Paths.get(jarPath).toFile().length() > 1024*1024*100)
				{
					System.out.println("File is " + Paths.get(jarPath).toFile().length());
					throw new Exception("This file is too big");
				}
				byte[] b = Files.readAllBytes(Paths.get(jarPath));
				byte[] hashBytes = MessageDigest.getInstance("MD5").digest(b);
				//curVersion = DatatypeConverter.printHexBinary(hashBytes);
				//System.out.println("Running version: " + curVersion);
				String fullCheck = "http://" + serverAddress + "/DataCollectorServer/openDataCollection/endpointSoftware/currentVersion.jsp";
				//System.out.println("Checking for update at: " + fullCheck);
				HttpClient http = new DefaultHttpClient();
				HttpGet checkGet = new HttpGet(fullCheck);
				double myTime = System.currentTimeMillis();
				
				HttpResponse checkResponse = http.execute(checkGet);
				HttpEntity checkEntity = checkResponse.getEntity();
				String checkVersion = EntityUtils.toString(checkEntity);
				//System.out.println(checkVersion);
				Gson gson = new Gson();
				HashMap fromJSON = gson.fromJson(checkVersion, HashMap.class);
				//System.out.println(fromJSON);
				
				//double serverTime = (Double)fromJSON.get("serverTime");
				//timeDifference = myTime - serverTime;
				//timeDifference = timeDifference/1000;
				//timeDifference *= 1000;
				
				if(fromJSON.get("status").equals("error"))
				{
					return updated;
				}
				//System.out.println("Current time difference is: " + timeDifference);
				
				if(!curVersion.equals(fromJSON.get("currentVersion")))
				{
					//Sleep in case server needs to reload
					System.out.println("Waiting for 15");
					Thread.sleep(15000);
					System.out.println("Current version is not up to date");
					String downloadURL = "http://" + serverAddress + fromJSON.get("downloadURL");
					System.out.println("Downloading new version from: " + downloadURL);
					HttpGet downloadUpdate = new HttpGet(downloadURL);
					HttpResponse downloadResponse = http.execute(downloadUpdate);
					HttpEntity downloadEntity = downloadResponse.getEntity();
					byte[] fileData = EntityUtils.toByteArray(downloadEntity);
					String fileInString = new String(fileData);
					
					//System.out.println("File data recieved: " + fileInString);
					System.out.println("Updating JAR");
					currentFile.delete();
					FileOutputStream updateFileStream = new FileOutputStream(currentFile, false);
					updateFileStream.write(fileData);
					updateFileStream.close();
					updated = true;
					System.out.println("JAR updated");
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		return updated;
	}
	
	/**
	 * Parses the arguments to configure settings.  May want to migrate this
	 * to a more standardized arg parsing library.
	 * @param args Argument list from the command line.
	 * @return a hash map of settings (keys) and their values.
	 */
	public synchronized static HashMap configure(String[] args)
	{
		HashMap myReturn = new HashMap();
		for(int x=0; x<args.length; x++)
		{
			if(args[x].equals("-user"))
			{
				myReturn.put("user", args[x+1]);
				x++;
			}
			else if(args[x].equals("-adminemail"))
			{
				myReturn.put("adminemail", args[x+1]);
				x++;
			}
			else if(args[x].equals("-server"))
			{
				myReturn.put("server", args[x+1]);
				x++;
			}
			else if(args[x].equals("-event"))
			{
				myReturn.put("event", args[x+1]);
				x++;
			}
			else if(args[x].equals("-taskgui"))
			{
				myReturn.put("taskgui", args[x]);
			}
			else if(args[x].equals("-logging"))
			{
				myReturn.put("logging", args[x]);
			}
			else if(args[x].equals("-metrics"))
			{
				myReturn.put("metrics", args[x]);
			}
			else if(args[x].equals("-screenshot"))
			{
				myReturn.put("screenshot", args[x+1]);
				x++;
			}
			else if(args[x].equals("-process"))
			{
				myReturn.put("process", args[x+1]);
				x++;
			}
			else if(args[x].equals("-processgranularity"))
			{
				myReturn.put("processgranularity", args[x+1]);
				x++;
			}
			else if(args[x].equals("-continuous"))
			{
				myReturn.put("continuous", true);
				myReturn.put("token", args[x+1]);
				x++;
				myReturn.put("collectionServer", args[x+1]);
				x++;
			}
			else if(args[x].equals("-imagecompression"))
			{
				myReturn.put("imagecompression", true);
				myReturn.put("imagecompressiontype", args[x+1]);
				x++;
				if(args.length > x + 1 && !(args[x + 1].contains("-")))
				{
					myReturn.put("imagecompressionlevel", args[x+1]);
					x++;
				}
				if(args.length > x + 1 && !(args[x + 1].contains("-")))
				{
					myReturn.put("diffMethod", args[x+1]);
					x++;
				}
			}
		}
		
		return myReturn;
	}

	/**
	 * Main method, called to launch the Endpoint Monitor (this file,
	 * maybe refactor this file's name at some point).  Here, the method
	 * creates and configures a Start (the data collection object that
	 * gets data from all the different sources) and optionally a
	 * DataAggregator (which streams data to the server) and a TaskGUI
	 * (which allows users to enter tasks) if configured to do so based
	 * on passed args.  The method does not give up control due to
	 * (possibly prior) dependencies exiting if control on this thread
	 * is given up.  This control at one point periodically checked the
	 * software version but that component is broken currently.
	 * @param args Command line args.
	 */
	public static void main(String[] args)
	{
		System.out.println("Launching on " + new Date());
		System.out.println("Got args: " + Arrays.toString(args));
		//if(true)
		//	return;
		if(hasBeenLaunched)
		{
			System.out.println("Already running!");
			return;
		}
		hasBeenLaunched = true;
		programArgs = args;
		HashMap configuration = configure(args);
		//update();
		//if(true)
		//	return;
		String userToStart = "default";
		String adminToStart = "default";
		String eventToStart = "";
		
		
		boolean metrics = false;
		
		
		if(configuration.containsKey("metrics"))
		{
			metrics = true;
		}
		
		boolean threadGranularity = false;
		
		if(configuration.containsKey("processgranularity") && configuration.get("processgranularity").equals("thread"))
		{
			threadGranularity = true;
		}
		
		boolean logging = false;
		
		
		if(configuration.containsKey("logging"))
		{
			logging = true;
		}
		if(configuration.containsKey("user"))
		{
			userToStart = (String) configuration.get("user");
		}
		if(configuration.containsKey("adminemail"))
		{
			adminToStart = (String) configuration.get("adminemail");
		}
		else
		{
			adminToStart = "cgtboy1988@yahoo.com";
		}
		if(configuration.containsKey("server"))
		{
			serverAddress = (String) configuration.get("server");
		}
		if(configuration.containsKey("event"))
		{
			eventToStart = (String) configuration.get("event");
		}
		
		int screenshot = 10000;
		if(configuration.containsKey("screenshot"))
		{
			screenshot = Integer.parseInt((String) configuration.get("screenshot"));
		}
		int process = 20000;

		if(configuration.containsKey("process"))
		{
			process = Integer.parseInt((String) configuration.get("process"));
		}
		
		String compressionType = "jpg";
		if(configuration.containsKey("imagecompressiontype"))
		{
			compressionType = ((String) configuration.get("imagecompressiontype"));
		}
		double compressionAmount = .5;
		if(configuration.containsKey("imagecompressionlevel"))
		{
			compressionAmount = Double.parseDouble((String) configuration.get("imagecompressionlevel"));
		}
		String diffMethod = "none";
		if(configuration.containsKey("diffMethod"))
		{
			diffMethod = ((String) configuration.get("diffMethod"));
		}
		
		myStart = new Start(userToStart, eventToStart, adminToStart, screenshot, process, logging, metrics, threadGranularity, compressionType, compressionAmount, diffMethod);
		
		if(configuration.containsKey("continuous"))
		{
			DataAggregator currentAggregator = DataAggregator.getInstance((String)configuration.get("collectionServer"), (String)configuration.get("user"), (String)configuration.get("token"), true, eventToStart, adminToStart);
		}
		
		if(configuration.containsKey("taskgui"))
		{
			myTaskGUI = new TaskInputGUI(eventToStart, userToStart, adminToStart, myStart.sessionToken);
			myTaskGUI.setSize(400,200);
			myTaskGUI.addPauseListener(myStart);
			myTaskGUI.setVisible(true);
			//windowsToClose.add(myTaskGUI);
		}
		
		while(myStart.running)
		{
			//Don't give up control
			try {
				Thread.currentThread().sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Gives this instance a DataAggregator, which is used to upload
	 * data to the remote server.
	 * @param myAggregator The aggregator currently uploading data.
	 */
	public void setAggregator(DataAggregator myAggregator)
	{
		curAggregator = myAggregator;
	}
	
	/**
	 * Polls the active window to see if it has changed.  If a new
	 * active window is detected, it is recorded and a signal to
	 * take a new screenshot is sent to the screenshot generator.
	 * @param newWindows the new window list, generated by OSHI.
	 * @return true if a new window is detected, false otherwise.
	 */
	public synchronized boolean checkNew(ArrayList newWindows)
	{
		if(paused)
		{
			return false;
		}
		
		//System.out.println("Checking for new window...");
		
		long metricTime = System.currentTimeMillis();
		HashMap newWindow = null;
		Timestamp curTimestamp = getUniqueCurrentTimestamp();
		for(int x=0; x < newWindows.size(); x++)
		{
			HashMap curWindow = (HashMap) newWindows.get(x);
			if(curWindow.get("IsFocus").equals("1"))
			{
				newWindow = curWindow;
			}
		}
		if(newWindow == null)
		{
			metricTime = metricTime - System.currentTimeMillis();
			recordMetric("Window Detection", metricTime, "ms");
			
			//System.out.println("Window is null.");
			
			return false;
		}
		if(!("" + newWindow.get("WindowID")).equals(windowID) || !("" + newWindow.get("WindowTitle")).equals(windowName) || !((double)newWindow.get("x") == windowX) || !((double)newWindow.get("y") == windowY || !((double)newWindow.get("width") == windowWidth) || !((double)newWindow.get("height") == windowHeight)))
		{
			windowName = "" + newWindow.get("WindowTitle");
			windowID = "" + newWindow.get("WindowID");
			windowX = (double) newWindow.get("x");
			windowY = (double) newWindow.get("y");
			windowWidth = (double) newWindow.get("width");
			windowHeight = (double) newWindow.get("height");
			if(newWindow != null)
			{
				for(int x=0; x < newWindows.size(); x++)
				{
					HashMap curWindow = (HashMap) newWindows.get(x);
					
					curWindow.put("clickedInTime", curTimestamp);
					curWindow.put("username", userName);
					
					windowsToWrite.add(curWindow);
				}
			}
			currentWindowData = newWindow;
			if(verbose)
				System.out.println("New window");
			
			metricTime = metricTime - System.currentTimeMillis();
			recordMetric("Window Detection", metricTime, "ms");
			while(myGenerator == null)
			{
				//System.out.println("Still waiting");
				try {
					Thread.currentThread().sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			//System.out.println("Interrupting sleep screenshot.");
			
			myGenerator.interruptSleepScreenshot();
			
			//System.out.println("New window.");
			
			return true;
		}
		
		//System.out.println("No new window.");
		
		return false;
	}
	
	/**
	 * Sends a signal to asynchronously check the current active window as
	 * soon as possible.  In some systems, window checking with OSHI can
	 * take human-input levels of latency to complete, which means checking
	 * on all inputs causes system lag and incorrect input timestamps.
	 * Asynchronously checking top windows eliminates input timestamp lag
	 * but could also introduce very slight lag into active window
	 * detection.
	 * @param myMonitor The ActiveWindowMonitor which gets the window
	 * list from OSHI.
	 * @param diff deprecated.
	 */
	public void checkNewInterrupt(PortableActiveWindowMonitor myMonitor, long diff)
	{
		if(paused)
		{
			return;
		}
		if(myMonitor != null)
		{
			myMonitor.interruptSleep(diff);
		}
		if(currentWindowData == null || currentWindowData.isEmpty())
		{
			getEmptyWindow();
		}
	}
	
	private HashMap emptyProcess = null;
	public HashMap getEmptyProcess()
	{
		
		if(emptyProcess != null)
		{
			return emptyProcess;
		}
		
		HashMap myReturn = new ProcessMap();
		
		myReturn.put("USER", "NONE");
		myReturn.put("PID", -1);
		myReturn.put("%CPU", 0);
		myReturn.put("%MEM", 0);
		myReturn.put("VSZ", (long)0);
		myReturn.put("RSS", (long)0);
		myReturn.put("TTY", "");
		myReturn.put("STAT", "NONE");
		myReturn.put("START", System.currentTimeMillis());
		myReturn.put("TIME", (long)0);
		String commandLine = "NONE";
		myReturn.put("PARENTPID", -1);
		myReturn.put("COMMAND", "");
		
		emptyProcess = myReturn;
		
		return myReturn;
	}
	
	public synchronized void getEmptyWindow()
	{
		HashMap windowMap = getEmptyWindowMap();
		
		ArrayList toConsume = new ArrayList();
		toConsume.add(windowMap);
		consumeWindowList(toConsume);
	}
	
	public synchronized HashMap getEmptyWindowMap()
	{
		
		//HashMap processInfo = myProcMonitor.getProcessInfo((int)curWindow.getOwningProcessId());
		
		
		
		HashMap windowMap = new HashMap();
		
		windowMap.put("WindowID", "-1");
		windowMap.put("WindowTitle", "NONE");
		
		//System.out.println(curWindow.getTitle());
		
		windowMap.put("WindowFirstClass", "NONE");
		windowMap.put("WindowSecondClass", "NONE");
		windowMap.put("WindowPID", "-1");
		windowMap.put("ProcessInfo", getEmptyProcess());
		windowMap.put("x", 0.0);
		windowMap.put("y", 0.0);
		windowMap.put("width", 0.0);
		windowMap.put("height", 0.0);
		
		windowMap.put("IsFocus", "1");
		
		return windowMap;
		
	}
	
	/**
	 * Consumes the window list generated by OSHI in an ActiveWindowMonitor.
	 * This window list is queued to write to disk.
	 * @param newWindows The latest window list from OSHI.
	 */
	public synchronized void consumeWindowList(ArrayList newWindows)
	{
		if(paused)
		{
			return;
		}
		
		//System.out.println("Checking for new window...");
		
		long metricTime = System.currentTimeMillis();
		HashMap newWindow = null;
		Timestamp curTimestamp = getUniqueCurrentTimestamp();
		for(int x=0; x < newWindows.size(); x++)
		{
			HashMap curWindow = (HashMap) newWindows.get(x);
			if(curWindow.get("IsFocus").equals("1"))
			{
				newWindow = curWindow;
			}
		}
		if(newWindow == null)
		{
			metricTime = metricTime - System.currentTimeMillis();
			recordMetric("Window Detection", metricTime, "ms");
			
			HashMap defWindowMap = getEmptyWindowMap();
			newWindow = defWindowMap;
			newWindows.add(defWindowMap);
			
			System.out.println("Top window is null, using default. List size is: " + newWindows.size());
			
			return;
		}
		if(!("" + newWindow.get("WindowID")).equals(windowID) || !("" + newWindow.get("WindowTitle")).equals(windowName) || !((double)newWindow.get("x") == windowX) || !((double)newWindow.get("y") == windowY || !((double)newWindow.get("width") == windowWidth) || !((double)newWindow.get("height") == windowHeight)))
		{
			windowName = "" + newWindow.get("WindowTitle");
			windowID = "" + newWindow.get("WindowID");
			windowX = (double) newWindow.get("x");
			windowY = (double) newWindow.get("y");
			windowWidth = (double) newWindow.get("width");
			windowHeight = (double) newWindow.get("height");
			if(newWindow != null)
			{
				for(int x=0; x < newWindows.size(); x++)
				{
					HashMap curWindow = (HashMap) newWindows.get(x);
					
					curWindow.put("clickedInTime", curTimestamp);
					curWindow.put("username", userName);
					
					windowsToWrite.add(curWindow);
				}
			}
			currentWindowData = newWindow;
			if(verbose)
				System.out.println("New window");
			
			metricTime = metricTime - System.currentTimeMillis();
			recordMetric("Window Detection", metricTime, "ms");
			while(myGenerator == null)
			{
				//System.out.println("Still waiting");
				try {
					Thread.currentThread().sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			//System.out.println("Interrupting sleep screenshot.");
			
			myGenerator.interruptSleepScreenshot();
			
			//System.out.println("New window.");
			
		}
		
		//System.out.println("No new window.");
		
		
	}

	
	boolean recordClick = false;
	/**
	 * Consumes mouse clicked events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the mouse clicked event.
	 */
	@Override
	public synchronized void nativeMouseClicked(NativeMouseEvent arg0)
	{
		if(paused)
		{
			return;
		}
		if(!recordClick)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		//if(verbose)
		//System.out.println("Mouse Clicked: " + arg0.getX() + ", " + arg0.getY());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "click");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for mouse input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		clickToWrite.put("window", currentWindowData);
		clickToWrite.put("username", userName);
		clicksToWrite.add(clickToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}

	/**
	 * Consumes mouse pressed events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the mouse pressed event.
	 */
	@Override
	public synchronized void nativeMousePressed(NativeMouseEvent arg0)
	{
		if(paused)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		//if(verbose)
		//System.out.println("Mouse Pressed: " + arg0.getX() + ", " + arg0.getY());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "down");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for mouse input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		clickToWrite.put("window", currentWindowData);
		clickToWrite.put("username", userName);
		clicksToWrite.add(clickToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}

	/**
	 * Consumes mouse released events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the mouse released event.
	 */
	@Override
	public synchronized void nativeMouseReleased(NativeMouseEvent arg0)
	{
		if(paused)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		//if(verbose)
		//System.out.println("Mouse Released: " + arg0.getX() + ", " + arg0.getY());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "up");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for mouse input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		clickToWrite.put("window", currentWindowData);
		clickToWrite.put("username", userName);
		clicksToWrite.add(clickToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}

	
	private boolean useDragged = false;
	/**
	 * Consumes mouse dragged events from JNativeHook.  Currently, these are ignored.
	 * @param arg0 the mouse dragged event.
	 */
	@Override
	public synchronized void nativeMouseDragged(NativeMouseEvent arg0)
	{
		if(paused)
		{
			return;
		}
		if(!useDragged)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		//if(verbose)
		//System.out.println("Mouse Dragged: " + arg0.getX() + ", " + arg0.getY());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap clickToWrite = new HashMap();
		clickToWrite.put("xLoc", arg0.getX());
		clickToWrite.put("yLoc", arg0.getY());
		clickToWrite.put("type", "drag");
		//Calendar currentTime = Calendar.getInstance();
		clickToWrite.put("clickTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for mouse input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		clickToWrite.put("window", currentWindowData);
		clickToWrite.put("username", userName);
		clicksToWrite.add(clickToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}

	/**
	 * Consumes mouse moved events from JNativeHook.  They are currently ignored.
	 * @param arg0 the mouse moved event.
	 */
	@Override
	public void nativeMouseMoved(NativeMouseEvent arg0)
	{
		//if(verbose)
		//System.out.println("Mouse Moved: " + arg0.getX() + ", " + arg0.getY());
		
	}
	
	private long lastTimestamp = -1;
	private synchronized Timestamp getUniqueCurrentTimestamp()
	{
		long curTime = new Date().getTime() - timeDifference;
		if(curTime <= lastTimestamp)
		{
			curTime = lastTimestamp + 1;
		}
		lastTimestamp = curTime;
		return new Timestamp(curTime);
	}
	
	/**
	 * Consumes keyboard pressed events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the keyboard pressed event.
	 */
	@Override
	public synchronized void nativeKeyPressed(NativeKeyEvent arg0)
	{
		if(paused)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap keyToWrite = new HashMap();
		keyToWrite.put("type", "press");
		keyToWrite.put("inputTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for key input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		keyToWrite.put("window", currentWindowData);
		
		//System.out.println("Char typed: " + NativeKeyEvent.getKeyText(arg0.getKeyCode()));
		
		//System.out.println("Typed at: " + recordTimestamp);
		
		keyToWrite.put("button", NativeKeyEvent.getKeyText(arg0.getKeyCode()));
		keyToWrite.put("username", userName);
		keysToWrite.add(keyToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}

	/**
	 * Consumes keyboard released events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the keyboard released event.
	 */
	@Override
	public synchronized void nativeKeyReleased(NativeKeyEvent arg0)
	{
		if(paused)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap keyToWrite = new HashMap();
		keyToWrite.put("type", "release");
		keyToWrite.put("inputTime", recordTimestamp);
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for key input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		keyToWrite.put("window", currentWindowData);
		keyToWrite.put("button", NativeKeyEvent.getKeyText(arg0.getKeyCode()));
		keyToWrite.put("username", userName);
		keysToWrite.add(keyToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}
	
	/**
	 * Consumes keyboard typed events from JNativeHook.  They are added
	 * to the queue to be recorded and trigger an active window check.
	 * @param arg0 the keyboard typed event.
	 */
	@Override
	public void nativeKeyTyped(NativeKeyEvent arg0)
	{
		if(paused)
		{
			return;
		}
		Timestamp recordTimestamp = getUniqueCurrentTimestamp();
		//System.out.println(new Date().getTime()-timeDifference - arg0.getWhen());
		//System.out.println("" + arg0.getWhen() + ":" + arg0.getKeyChar());
		//System.out.println(Short.MAX_VALUE * 2);
		//if(true)
		//	return;
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		HashMap keyToWrite = new HashMap();
		keyToWrite.put("type", "type");
		keyToWrite.put("inputTime", recordTimestamp);
		keyToWrite.put("preciseTime", arg0.getWhen());
		while(currentWindowData == null)
		{
			System.out.println("Warning: No top window detected for key input.");
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				Thread.currentThread().sleep(shortPoll);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			};
		}
		keyToWrite.put("window", currentWindowData);
		keyToWrite.put("button", "" + arg0.getKeyChar());
		keyToWrite.put("username", userName);
		keysToWrite.add(keyToWrite);
		if(myGenerator != null)
		{
			myGenerator.interruptSleepScreenshot();
		}
	}
	
	/**
	 * Adds a single-value metric to the queue to record.
	 * @param metricName The metric name.
	 * @param metricValue The metric value.
	 * @param metricUnit The metric unit.
	 */
	public void recordMetric(String metricName, double metricValue, String metricUnit)
	{
		if(paused || !metrics)
		{
			return;
		}
		
		HashMap metricToWrite = new HashMap();
		
		metricToWrite.put("metricName", metricName);
		metricToWrite.put("recordedTimestamp", new Timestamp(new Date().getTime()-timeDifference));
		metricToWrite.put("metricValue1", metricValue);
		metricToWrite.put("metricUnit1", metricUnit);
		metricToWrite.put("metricValue2", 0.0);
		metricToWrite.put("metricUnit2", "None");
		metricToWrite.put("username", userName);
		
		if(verbose)
			System.out.println("Recording metric: ");
		if(verbose)
			System.out.println(metricToWrite);
		
		metricsToWrite.add(metricToWrite);
	}
	
	/**
	 * Adds a double-value metric to the queue to record.  Useful for
	 * calculating rates (ie: count per time period).
	 * @param metricName The metric name.
	 * @param metricValue1 The first metric value.
	 * @param metricUnit1 The first metric unit.
	 * @param metricValue2 The second metric value.
	 * @param metricUnit2 The second metric unit.
	 */
	public void recordMetric(String metricName, double metricValue1, String metricUnit1, double metricValue2, String metricUnit2)
	{
		if(paused || !metrics)
		{
			return;
		}
		
		HashMap metricToWrite = new HashMap();
		
		metricToWrite.put("metricName", metricName);
		metricToWrite.put("recordedTimestamp", new Timestamp(new Date().getTime()-timeDifference));
		metricToWrite.put("metricValue1", metricValue1);
		metricToWrite.put("metricUnit1", metricUnit1);
		metricToWrite.put("metricValue2", metricValue2);
		metricToWrite.put("metricUnit2", metricUnit2);
		metricToWrite.put("username", userName);
		
		if(verbose)
			System.out.println("Recording metric: ");
		if(verbose)
			System.out.println(metricToWrite);
		
		metricsToWrite.add(metricToWrite);
	}
	
	/**
	 * Adds a list of processes to the queue to record.
	 * @param processes the current process list to record.
	 */
	public void monitorProcesses(ArrayList processes)
	{
		if(paused)
		{
			return;
		}
		Timestamp curTimestamp = getUniqueCurrentTimestamp();
		for(int x=0; x<processes.size(); x++)
		{
			((HashMap)processes.get(x)).put("username", userName);
			((HashMap)processes.get(x)).put("timestamp", curTimestamp);
			processesToWrite.add(processes.get(x));
		}
	}
	
	/**
	 * Stops the data collection threads in preparation for exit.
	 */
	public void stop()
	{
		GlobalScreen.removeNativeKeyListener(this);
		GlobalScreen.removeNativeMouseListener(this);
		try
		{
			GlobalScreen.unregisterNativeHook();
		}
		catch(NativeHookException e)
		{
			e.printStackTrace();
		}
		myGenerator.stop();
		myProcessMonitor.stop();
		running = false;
	}

	/**
	 * This method records all of the queues (of screenshots, windows, processes, keystrokes,
	 * mouse input) to disk via a MySql database.  It continually does this in the background
	 * as other threads generate new data.  To prevent synchronization issues, new queues
	 * are instantiated and replace the old references before the old queues are written to disk.
	 */
	@Override
	public void run()
	{
		running = true;
		System.out.println("Running in session " + sessionToken);
		//if(true)
		//	return;
		Connection myConnection = connectionSource.getDatabaseConnection();
		
		try
		{
			while(myConnection == null)
			{
				Thread.sleep(5000);
				myConnection = connectionSource.getDatabaseConnection();
			}
			myConnection.setAutoCommit(false);
		}
		catch (Exception e2)
		{
			e2.printStackTrace();
		}
		int count = 0;
		do
		{
			long startWriteout = System.currentTimeMillis();
			long timeTaken = -1;
			count++;
			try
			{
				if(myConnection == null)
				{
					myConnection = connectionSource.getDatabaseConnection();
					myConnection.setAutoCommit(false);
				}
				if(myConnection.isClosed())
				{
					myConnection = connectionSource.getDatabaseConnection();
					myConnection.setAutoCommit(false);
				}
			}
			catch(SQLException e1)
			{
				
			}
			
			
			try
			{
				checkNewInterrupt(myMonitor, timeDifference);
				//checkNew(myMonitor.getTopWindow(timeDifference));
				if(count > 5 && !windowsToWrite.isEmpty() || !clicksToWrite.isEmpty() || !screenshotsToWrite.isEmpty())
				{
					long startEventInsert = System.currentTimeMillis();
					if(verbose)
						System.out.println("Recording user JIC");
					
					String eventInsert = "INSERT IGNORE INTO `dataCollection`.`Event` (`event`, `adminEmail`) VALUES ";
					String eventRow = "(?, ?)";
					eventInsert += eventRow;
					PreparedStatement eventStatement = myConnection.prepareStatement(eventInsert);
					eventStatement.setString(1, eventName);
					eventStatement.setString(2, adminEmail);
					eventStatement.execute();
					eventStatement.close();
					
					String userInsert = "INSERT IGNORE INTO `dataCollection`.`User` (`username`, `adminEmail`, `session`, `event`, `sessionEnvironment`) VALUES ";
					String userRow = "(?, ?, ?, ?, ?)";
					userInsert += userRow;
					PreparedStatement userStatement = myConnection.prepareStatement(userInsert);
					userStatement.setString(1, userName);
					userStatement.setString(2, adminEmail);
					userStatement.setString(3, sessionToken);
					userStatement.setString(4, eventName);
					userStatement.setString(5, "" + os + " " + hal.getComputerSystem() + " " + hal.getProcessor());
					userStatement.execute();
					userStatement.close();
					
					long endEventInsert = System.currentTimeMillis() - startEventInsert;
					recordMetric("Event Data Write Time", endEventInsert, "ms");
					
					
					
					if(verbose)
						System.out.println("Time to record " + windowsToWrite.size() + " window changes");
					
					
					int toInsert = windowsToWrite.size();
					int clickToInsert = clicksToWrite.size();
					int screenshotsToInsert = screenshotsToWrite.size();
					int keyToInsert = keysToWrite.size();
					int metricToInsert = metricsToWrite.size();
					int toInsertProcess = processesToWrite.size();
					ConcurrentLinkedQueue countQueue = new ConcurrentLinkedQueue();
					//ConcurrentLinkedQueue nextQueue = new ConcurrentLinkedQueue();
					
					
					if(toInsert > 0)
					{
						long startWindowInsert = System.currentTimeMillis();
						String processInsert = "INSERT IGNORE INTO `dataCollection`.`Process` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `command`, `parentpid`, `parentuser`, `parentstart`) VALUES ";
						String eachProcessRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String processArgInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessArgs` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `numbered`, `arg`) VALUES ";
						String eachProcessArgRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
						//int argCount = 0;
						
						String processAttInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessAttributes` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `cpu`, `mem`, `vsz`, `rss`, `tty`, `stat`, `time`, `timestamp`) VALUES ";
						String eachProcessAttRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String windowInsert = "INSERT IGNORE INTO `dataCollection`.`Window` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `xid`, `firstClass`, `secondClass`) VALUES ";
						String eachWindowRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String windowDetailInsert = "INSERT IGNORE INTO `dataCollection`.`WindowDetails` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `xid`, `x`, `y`, `width`, `height`, `name`, `timeChanged`, `active`) VALUES ";
						String eachWindowDetailRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						boolean hasArgs = false;
						
						
						boolean argsStarted = false;
						for(int x=0; x<toInsert; x++)
						{
							HashMap tmpMap = (HashMap) windowsToWrite.poll();
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							tmpProcess.put("timestamp", tmpMap.get("clickedInTime"));
							ArrayList argList = (ArrayList) tmpProcess.get("ARGS");
							//argCount += argList.size();
							
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								hasArgs = true;
								if(y > 0 || argsStarted)
								{
									processArgInsert += ", " + eachProcessArgRow;
								}
								else
								{
									argsStarted = true;
									processArgInsert += eachProcessArgRow;
								}
								//if(verbose)
								//System.out.println(processArgInsert);
							}
							
							//if(verbose)
							//System.out.println(tmpProcess);
							
							countQueue.add(tmpMap);
							
							if(x > 0)
							{
								processInsert += ", " + eachProcessRow;
								processAttInsert += ", " + eachProcessAttRow;
								windowInsert += ", " + eachWindowRow;
								windowDetailInsert += ", " + eachWindowDetailRow;
							}
							else
							{
								processInsert += eachProcessRow;
								processAttInsert += eachProcessAttRow;
								windowInsert += eachWindowRow;
								windowDetailInsert += eachWindowDetailRow;
							}
						}
						
						if(verbose)
							System.out.println(processInsert);
						if(verbose)
							System.out.println(processAttInsert);
						if(verbose)
							System.out.println(windowInsert);
						if(verbose)
							System.out.println(windowDetailInsert);
						if(verbose)
							System.out.println(processArgInsert);
						
						PreparedStatement processStatement = myConnection.prepareStatement(processInsert);
						PreparedStatement processAttStatement = myConnection.prepareStatement(processAttInsert);
						PreparedStatement windowStatement = myConnection.prepareStatement(windowInsert);
						PreparedStatement windowDetailStatement = myConnection.prepareStatement(windowDetailInsert);
						
						//if(verbose)
						//System.out.println(processArgInsert);
						PreparedStatement processArgStatement = null;
						if(hasArgs)
						{
							processArgStatement = myConnection.prepareStatement(processArgInsert);
						}
						
						int fieldCount = 1;
						int argFieldCount = 1;
						int attFieldCount = 1;
						int windowFieldCount = 1;
						int windowDetailFieldCount = 1;
						for(int x=0; x<toInsert; x++)
						{
							HashMap tmpMap = (HashMap) countQueue.poll();
							
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							
							processStatement.setString(fieldCount, (String) tmpMap.get("username"));
							processAttStatement.setString(attFieldCount, (String) tmpMap.get("username"));
							windowStatement.setString(windowFieldCount, (String) tmpMap.get("username"));
							windowDetailStatement.setString(windowDetailFieldCount, (String) tmpMap.get("username"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, adminEmail);
							processAttStatement.setString(attFieldCount, adminEmail);
							windowStatement.setString(windowFieldCount, adminEmail);
							windowDetailStatement.setString(windowDetailFieldCount, adminEmail);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, sessionToken);
							processAttStatement.setString(attFieldCount, sessionToken);
							windowStatement.setString(windowFieldCount, sessionToken);
							windowDetailStatement.setString(windowDetailFieldCount, sessionToken);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, eventName);
							processAttStatement.setString(attFieldCount, eventName);
							windowStatement.setString(windowFieldCount, eventName);
							windowDetailStatement.setString(windowDetailFieldCount, eventName);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("USER"));
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("USER"));
							windowStatement.setString(windowFieldCount, "" +  tmpProcess.get("USER"));
							windowDetailStatement.setString(windowDetailFieldCount, "" +  tmpProcess.get("USER"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("PID"));
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("PID"));
							windowStatement.setString(windowFieldCount, "" +  tmpProcess.get("PID"));
							windowDetailStatement.setString(windowDetailFieldCount, "" +  tmpProcess.get("PID"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("START"));
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("START"));
							windowStatement.setString(windowFieldCount, "" +  tmpProcess.get("START"));
							windowDetailStatement.setString(windowDetailFieldCount, "" +  tmpProcess.get("START"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							//processStatement.setString(fieldCount, "" +  tmpProcess.get("TIME"));
							//fieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("COMMAND"));
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("%CPU"));
							windowStatement.setString(windowFieldCount, "" +  tmpMap.get("WindowID"));
							windowDetailStatement.setString(windowDetailFieldCount, "" +  tmpMap.get("WindowID"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("PARENTPID"));
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("%MEM"));
							windowStatement.setString(windowFieldCount, "" +  tmpMap.get("WindowFirstClass"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) Math.round((double) tmpMap.get("x")));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("PARENTUSER"));
							processAttStatement.setLong(attFieldCount, (long)tmpProcess.get("VSZ"));
							windowStatement.setString(windowFieldCount, "" +  tmpMap.get("WindowSecondClass"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) Math.round((double) tmpMap.get("y")));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpProcess.get("PARENTSTART"));
							processAttStatement.setLong(attFieldCount, (long)tmpProcess.get("RSS"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) Math.round((double) tmpMap.get("width")));
							fieldCount++;
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("TTY"));
							windowDetailStatement.setInt(windowDetailFieldCount, (int) Math.round((double) tmpMap.get("height")));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("STAT"));
							windowDetailStatement.setString(windowDetailFieldCount, "" +  tmpMap.get("WindowTitle"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpProcess.get("TIME"));
							windowDetailStatement.setTimestamp(windowDetailFieldCount, (Timestamp) tmpMap.get("clickedInTime"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setTimestamp(attFieldCount, (Timestamp) tmpProcess.get("timestamp"));
							windowDetailStatement.setInt(windowDetailFieldCount, Integer.parseInt("" + tmpMap.get("IsFocus")));
							attFieldCount++;
							windowDetailFieldCount++;
							
							ArrayList argList = (ArrayList) tmpProcess.get("ARGS");
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								processArgStatement.setString(argFieldCount, "" +  tmpMap.get("username"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, adminEmail);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, sessionToken);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, eventName);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpProcess.get("USER"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpProcess.get("PID"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpProcess.get("START"));
								argFieldCount++;
								
								//processArgStatement.setString(argFieldCount, "" +  tmpProcess.get("TIME"));
								//argFieldCount++;
								
								processArgStatement.setInt(argFieldCount, y);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  argList.get(y));
								argFieldCount++;
							}
							
							//if(verbose)
							//System.out.println(tmpProcess);
							
							//nextQueue.add(tmpMap);
						}
						
						processStatement.execute();
						processStatement.close();
						processAttStatement.execute();
						processAttStatement.close();
						windowStatement.execute();
						windowStatement.close();
						windowDetailStatement.execute();
						windowDetailStatement.close();
						if(hasArgs)
						{
							//if(verbose)
							//System.out.println(processArgStatement);
							processArgStatement.execute();
							processArgStatement.close();
						}
						
						if(metrics)
						{
							startWindowInsert = System.currentTimeMillis() - startWindowInsert;
							recordMetric("Window Write Time", startWindowInsert, "ms", toInsert, "count");
						}
						
					}
					
					ConcurrentLinkedQueue nextProcessQueue = new ConcurrentLinkedQueue();
					//ConcurrentLinkedQueue nextQueue = new ConcurrentLinkedQueue();
					
					if(toInsertProcess > 0)
					{
						
						String processInsert = "INSERT IGNORE INTO `dataCollection`.`Process` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `command`, `parentpid`, `parentuser`, `parentstart`) VALUES ";
						String eachProcessRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String processArgInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessArgs` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `numbered`, `arg`) VALUES ";
						String eachProcessArgRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
						//int argCount = 0;
						
						String processAttInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessAttributes` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `cpu`, `mem`, `vsz`, `rss`, `tty`, `stat`, `time`, `timestamp`) VALUES ";
						String eachProcessAttRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						String processThreadInsert = "INSERT IGNORE INTO `dataCollection`.`ProcessThreads` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `name`, `tid`, `tstate`, `tcpu`, `minorfault`, `majorfault`, `tstart`, `priority`, `timestamp`) VALUES ";
						String eachProcessThreadRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						
						
						boolean hasArgs = false;
						
						long processTime = System.currentTimeMillis();
						
						if(verbose)
							System.out.println("Time to record " + processesToWrite.size() + " processes");
						
						ArrayList totalThreads = new ArrayList();
						
						boolean argsStarted = false;
						for(int x=0; x<toInsertProcess; x++)
						{
							HashMap tmpProcess = (HashMap) processesToWrite.poll();
							
							ArrayList argList = (ArrayList) tmpProcess.get("ARGS");
							if(tmpProcess.containsKey("THREADS"))
							{
								ArrayList threadList = (ArrayList) tmpProcess.get("THREADS");
								totalThreads.addAll(threadList);
							}
							//argCount += argList.size();
							
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								hasArgs = true;
								if(y > 0 || argsStarted)
								{
									processArgInsert += ", " + eachProcessArgRow;
								}
								else
								{
									argsStarted = true;
									processArgInsert += eachProcessArgRow;
								}
								//if(verbose)
								//System.out.println(processArgInsert);
							}
							
							if(verbose)
								System.out.println(tmpProcess);
							
							nextProcessQueue.add(tmpProcess);
							
							if(x > 0)
							{
								processInsert += ", " + eachProcessRow;
								processAttInsert += ", " + eachProcessAttRow;
							}
							else
							{
								processInsert += eachProcessRow;
								processAttInsert += eachProcessAttRow;
							}
						}
						
						if(verbose)
							System.out.println(processInsert);
						if(verbose)
							System.out.println(processAttInsert);
						if(verbose)
							System.out.println(processArgInsert);
						
						PreparedStatement processStatement = myConnection.prepareStatement(processInsert);
						PreparedStatement processAttStatement = myConnection.prepareStatement(processAttInsert);
						
						for(int x = 0; x < totalThreads.size(); x++)
						{
							if(x > 0)
							{
								processThreadInsert += ", " + eachProcessThreadRow;
							}
							else
							{
								processThreadInsert += eachProcessThreadRow;
							}
						}
						
						
						
						//if(verbose)
						//System.out.println(processArgInsert);
						PreparedStatement processArgStatement = null;
						if(hasArgs)
						{
							processArgStatement = myConnection.prepareStatement(processArgInsert);
						}
						
						int fieldCount = 1;
						int argFieldCount = 1;
						int attFieldCount = 1;
						int windowFieldCount = 1;
						int windowDetailFieldCount = 1;
						for(int x=0; x<toInsertProcess; x++)
						{
							HashMap tmpMap = (HashMap) nextProcessQueue.poll();
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("username"));
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("username"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, adminEmail);
							processAttStatement.setString(attFieldCount, adminEmail);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, sessionToken);
							processAttStatement.setString(attFieldCount, sessionToken);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, eventName);
							processAttStatement.setString(attFieldCount, eventName);
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("USER"));
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("USER"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" + tmpMap.get("PID"));
							processAttStatement.setString(attFieldCount, "" + tmpMap.get("PID"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" + tmpMap.get("START"));
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("START"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							//processStatement.setString(fieldCount, "" +  tmpProcess.get("TIME"));
							//fieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("COMMAND"));
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("%CPU"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("PARENTPID"));
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("%MEM"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("PARENTUSER"));
							processAttStatement.setLong(attFieldCount, (long)tmpMap.get("VSZ"));
							fieldCount++;
							attFieldCount++;
							windowFieldCount++;
							windowDetailFieldCount++;
							
							processStatement.setString(fieldCount, "" +  tmpMap.get("PARENTSTART"));
							processAttStatement.setLong(attFieldCount, (long) tmpMap.get("RSS"));
							fieldCount++;
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("TTY"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("STAT"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setString(attFieldCount, "" +  tmpMap.get("TIME"));
							attFieldCount++;
							windowDetailFieldCount++;
							
							processAttStatement.setTimestamp(attFieldCount, (Timestamp) tmpMap.get("timestamp"));
							attFieldCount++;
							
							ArrayList argList = (ArrayList) tmpMap.get("ARGS");
							for(int y=0; argList != null && y<argList.size(); y++)
							{
								processArgStatement.setString(argFieldCount, "" +  tmpMap.get("username"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, adminEmail);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, sessionToken);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, eventName);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpMap.get("USER"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpMap.get("PID"));
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  tmpMap.get("START"));
								argFieldCount++;
								
								//processArgStatement.setString(argFieldCount, "" +  tmpProcess.get("TIME"));
								//argFieldCount++;
								
								processArgStatement.setInt(argFieldCount, y);
								argFieldCount++;
								
								processArgStatement.setString(argFieldCount, "" +  argList.get(y));
								argFieldCount++;
							}
							
							//if(verbose)
							//System.out.println(tmpProcess);
							//System.out.println(tmpMap);
							//nextQueue.add(tmpMap);
						}
						
						if(verbose)
							System.out.println(processStatement);
						//if(verbose)
						//	System.out.println(processAttStatement);
						
						processStatement.execute();
						processStatement.close();
						processAttStatement.execute();
						processAttStatement.close();
						if(hasArgs)
						{
							//if(verbose)
							//	System.out.println(processArgStatement);
							processArgStatement.execute();
							processArgStatement.close();
						}
						
						
						if(totalThreads.size() > 0)
						{
							PreparedStatement processThreadStatement = null;
							if(totalThreads.size() > 0)
							{
								processThreadStatement = myConnection.prepareStatement(processThreadInsert);
							}
							
							int threadFieldCount = 1;
							for(int x = 0; x < totalThreads.size(); x++)
							{
								HashMap curThread = (HashMap) totalThreads.get(x);
								HashMap parentProc = (HashMap) curThread.get("PARENT");
								
								processThreadStatement.setString(threadFieldCount, (String) parentProc.get("username"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, adminEmail);
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, sessionToken);
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, eventName);
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, (String) parentProc.get("USER"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + parentProc.get("PID"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + parentProc.get("START"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, (String) curThread.get("NAME"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + curThread.get("TID"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + curThread.get("TSTATE"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + curThread.get("T%CPU"));
								threadFieldCount++;
								
								processThreadStatement.setLong(threadFieldCount, (long) curThread.get("MINORFAULT"));
								threadFieldCount++;
								
								processThreadStatement.setLong(threadFieldCount, (long) curThread.get("MAJORFAULT"));
								threadFieldCount++;
								
								processThreadStatement.setString(threadFieldCount, "" + curThread.get("TSTART"));
								threadFieldCount++;
								
								processThreadStatement.setInt(threadFieldCount, (int) curThread.get("PRIORITY"));
								threadFieldCount++;
								
								processThreadStatement.setTimestamp(threadFieldCount, (Timestamp) parentProc.get("timestamp"));
								threadFieldCount++;
							}
							
							processThreadStatement.execute();
							processThreadStatement.close();
						}
						
						if(metrics)
						{
							processTime = System.currentTimeMillis() - processTime;
							recordMetric("Process Write Time", processTime, "ms", toInsertProcess, "count");
						}
						
					}
					
					
					//toWrite.clear();
					
					long mouseTime = System.currentTimeMillis();
					if(verbose)
						System.out.println("Time to record " + clicksToWrite.size() + " mouse clicks");
					if(clickToInsert > 0)
					{
						ConcurrentLinkedQueue nextClickQueue = new ConcurrentLinkedQueue();
						
						String mouseClickInsert = "INSERT IGNORE INTO `dataCollection`.`MouseInput` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `xid`, `timeChanged`, `type`, `xLoc`, `yLoc`, `inputTime`) VALUES ";
						String mouseClickRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						for(int x=0; x < clickToInsert; x++)
						{
							HashMap clickMap = (HashMap) clicksToWrite.poll();
							HashMap windowMap = (HashMap) clickMap.get("window");
							if(windowMap == null)
							{
								clickToInsert--;
								//continue;
							}
							else
							{
								nextClickQueue.add(clickMap);
								if(x==0)
								{
									mouseClickInsert += mouseClickRow;
								}
								else
								{
									mouseClickInsert += ", " + mouseClickRow;
								}
							}
						}
						
						if(verbose)
							System.out.println(mouseClickInsert);
						
						PreparedStatement mouseClickStatement = myConnection.prepareStatement(mouseClickInsert);
						
						int mouseClickCount = 1;
						
						
						
						for(int x=0; clickToInsert > 0 && x < clickToInsert; x++)
						{
							HashMap clickMap = (HashMap) nextClickQueue.poll();
							HashMap tmpMap = (HashMap) clickMap.get("window");
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							
							mouseClickStatement.setString(mouseClickCount, "" +  tmpMap.get("username"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, adminEmail);
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, sessionToken);
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, eventName);
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, "" +  tmpProcess.get("USER"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, "" +  tmpProcess.get("PID"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, "" +  tmpProcess.get("START"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, "" +  tmpMap.get("WindowID"));
							mouseClickCount++;
							
							//if(verbose)
							//System.out.println(tmpMap.get("clickedInTime"));
							mouseClickStatement.setTimestamp(mouseClickCount, (Timestamp) tmpMap.get("clickedInTime"));
							mouseClickCount++;
							
							mouseClickStatement.setString(mouseClickCount, "" +  clickMap.get("type"));
							mouseClickCount++;
							
							mouseClickStatement.setInt(mouseClickCount, (int) clickMap.get("xLoc"));
							mouseClickCount++;
							
							mouseClickStatement.setInt(mouseClickCount, (int) clickMap.get("yLoc"));
							mouseClickCount++;
							
							//if(verbose)
							//System.out.println(clickMap.get("clickTime"));
							mouseClickStatement.setTimestamp(mouseClickCount, (Timestamp) clickMap.get("clickTime"));
							mouseClickCount++;
							
						}
						
						if(clickToInsert > 0)
						{
							mouseClickStatement.execute();
							mouseClickStatement.close();
						}
						if(metrics)
						{
							mouseTime = System.currentTimeMillis() - mouseTime;
							recordMetric("Mouse Write Time", mouseTime, "ms", clickToInsert, "count");
						}
					}
					
					long screenshotTime = System.currentTimeMillis();
					if(verbose)
						System.out.println("Time to record " + screenshotsToWrite.size() + " screenshots");
					if(screenshotsToInsert > 0)
					{
						ConcurrentLinkedQueue nextScreenshotQueue = new ConcurrentLinkedQueue();
						
						String screenshotInsert = "INSERT IGNORE INTO `dataCollection`.`Screenshot` (`username`, `adminEmail`, `session`, `event`, `taken`, `screenshot`, `frameType`, `encoding`, `xStart`, `yStart`) VALUES ";
						String screenshotRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						for(int x=0; x < screenshotsToInsert; x++)
						{
							Object[] screenshotEntry = (Object[]) screenshotsToWrite.poll();
							if(screenshotEntry == null)
							{
								screenshotsToInsert--;
								//continue;
							}
							else
							{
								nextScreenshotQueue.add(screenshotEntry);
								if(x==0)
								{
									screenshotInsert += screenshotRow;
								}
								else
								{
									screenshotInsert += ", " + screenshotRow;
								}
							}
						}
						
						if(verbose)
							System.out.println(screenshotInsert);
						
						PreparedStatement screenshotStatement = myConnection.prepareStatement(screenshotInsert);
						
						int screenshotCount = 1;
						
						
						
						for(int x=0; screenshotsToInsert > 0 && x < screenshotsToInsert; x++)
						{
							Object[] clickMap = (Object[]) nextScreenshotQueue.poll();
							
							screenshotStatement.setString(screenshotCount, "" +  clickMap[2]);
							screenshotCount++;
							
							screenshotStatement.setString(screenshotCount, adminEmail);
							screenshotCount++;
							
							screenshotStatement.setString(screenshotCount, sessionToken);
							screenshotCount++;
							
							screenshotStatement.setString(screenshotCount, eventName);
							screenshotCount++;
							
							screenshotStatement.setTimestamp(screenshotCount, (Timestamp) clickMap[0]);
							screenshotCount++;
							
							screenshotStatement.setBytes(screenshotCount, (byte[]) clickMap[1]);
							screenshotCount++;
							
							screenshotStatement.setString(screenshotCount, (String) clickMap[4]);
							screenshotCount++;
							
							screenshotStatement.setString(screenshotCount, (String) clickMap[3]);
							screenshotCount++;
							
							screenshotStatement.setInt(screenshotCount, (int) clickMap[5]);
							screenshotCount++;
							
							screenshotStatement.setInt(screenshotCount, (int) clickMap[6]);
							screenshotCount++;
							
							
						}
						
						if(screenshotsToInsert > 0)
						{
							screenshotStatement.execute();
							screenshotStatement.close();
						}
						if(metrics)
						{
							screenshotTime = System.currentTimeMillis() - screenshotTime;
							recordMetric("Screenshot Write Time", screenshotTime, "ms", screenshotsToInsert, "count");
						}
					}
					
					long keystrokeTime = System.currentTimeMillis();
					if(verbose)
						System.out.println("Time to record " + keysToWrite.size() + " keyboard activities");
					
					if(keyToInsert > 0)
					{
						ConcurrentLinkedQueue nextPressQueue = new ConcurrentLinkedQueue();
						
						String keyPressInsert = "INSERT INTO `dataCollection`.`KeyboardInput` (`username`, `adminEmail`, `session`, `event`, `user`, `pid`, `start`, `xid`, `timeChanged`, `type`, `button`, `inputTime` ) VALUES ";
						String keyPressRow = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
						
						for(int x=0; x < keyToInsert; x++)
						{
							HashMap pressMap = (HashMap) keysToWrite.poll();
							HashMap windowMap = (HashMap) pressMap.get("window");
							if(windowMap == null)
							{
								keyToInsert--;
								//continue;
							}
							else
							{
								nextPressQueue.add(pressMap);
								if(x==0)
								{
									keyPressInsert += keyPressRow;
								}
								else
								{
									keyPressInsert += ", " + keyPressRow;
								}
							}
						}
						
						if(verbose)
							System.out.println(keyPressInsert);
						
						PreparedStatement keyPressStatement = myConnection.prepareStatement(keyPressInsert);
						
						int keyPressCount = 1;
						
						for(int x=0; keyToInsert > 0 && x < keyToInsert; x++)
						{
							HashMap pressMap = (HashMap) nextPressQueue.poll();
							HashMap tmpMap = (HashMap) pressMap.get("window");
							HashMap tmpProcess = (HashMap) tmpMap.get("ProcessInfo");
							
							keyPressStatement.setString(keyPressCount, "" +  tmpMap.get("username"));
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, adminEmail);
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, sessionToken);
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, eventName);
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, "" +  tmpProcess.get("USER"));
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, "" +  tmpProcess.get("PID"));
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, "" +  tmpProcess.get("START"));
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, "" +  tmpMap.get("WindowID"));
							keyPressCount++;
							
							//if(verbose)
							//System.out.println(tmpMap.get("pressedInTime"));
							keyPressStatement.setTimestamp(keyPressCount, (Timestamp) tmpMap.get("clickedInTime"));
							keyPressCount++;
							
							keyPressStatement.setString(keyPressCount, "" +  pressMap.get("type"));
							keyPressCount++;
							
							keyPressStatement.setObject(keyPressCount, pressMap.get("button"));
							keyPressCount++;
							
							//if(verbose)
							//System.out.println(pressMap.get("pressTime"));
							keyPressStatement.setTimestamp(keyPressCount, (Timestamp) pressMap.get("inputTime"));
							keyPressCount++;
							
							//allTyped += pressMap.get("button");
							
							
						}
						
						if(keyToInsert > 0)
						{
							keyPressStatement.execute();
							keyPressStatement.close();
							//System.out.println("Inserting " + allTyped);
							//System.out.println(keyPressStatement);
						}
						
						if(metrics)
						{
							keystrokeTime = System.currentTimeMillis() - keystrokeTime;
							recordMetric("Keystroke Write Time", keystrokeTime, "ms", keyToInsert, "count");
						}
						
						
						
					
					}
					
					long metricTime = System.currentTimeMillis();
					if(verbose)
						System.out.println("Time to record " + metricsToWrite.size() + " metrics");
					
					if(metricToInsert > 0)
					{
						ConcurrentLinkedQueue nextMetricQueue = new ConcurrentLinkedQueue();
						
						String metricInsert = "INSERT IGNORE INTO `dataCollection`.`PerformanceMetrics`(`event`, `adminEmail`, `username`, `session`, `metricName`, `metricValue1`, `metricUnit1`, `metricValue2`, `metricUnit2`, `recordedTimestamp`) VALUES ";
						String metricRow = "(?,?,?,?,?,?,?,?,?,?)";
						
						for(int x=0; x < metricToInsert; x++)
						{
							HashMap metricMap = (HashMap) metricsToWrite.poll();
							{
								nextMetricQueue.add(metricMap);
								if(x==0)
								{
									metricInsert += metricRow;
								}
								else
								{
									metricInsert += ", " + metricRow;
								}
							}
						}
						
						if(verbose)
							System.out.println(metricInsert);
						
						PreparedStatement metricsStatement = myConnection.prepareStatement(metricInsert);
						
						int metricsCount = 1;
						
						for(int x=0; metricToInsert > 0 && x < metricToInsert; x++)
						{
							HashMap metricMap = (HashMap) nextMetricQueue.poll();
							
							metricsStatement.setString(metricsCount, eventName);
							metricsCount++;
							
							metricsStatement.setString(metricsCount, adminEmail);
							metricsCount++;
							
							metricsStatement.setString(metricsCount, "" +  metricMap.get("username"));
							metricsCount++;
							
							metricsStatement.setString(metricsCount, sessionToken);
							metricsCount++;
							
							metricsStatement.setString(metricsCount, "" +  metricMap.get("metricName"));
							metricsCount++;
							
							metricsStatement.setDouble(metricsCount, (Double)metricMap.get("metricValue1"));
							metricsCount++;
							
							metricsStatement.setString(metricsCount, "" +  metricMap.get("metricUnit1"));
							metricsCount++;
							
							metricsStatement.setDouble(metricsCount, (Double)metricMap.get("metricValue2"));
							metricsCount++;
							
							metricsStatement.setString(metricsCount, "" +  metricMap.get("metricUnit2"));
							metricsCount++;
							
							metricsStatement.setTimestamp(metricsCount, (Timestamp) metricMap.get("recordedTimestamp"));
							metricsCount++;
							
							//allTyped += pressMap.get("button");
							
							
						}
						
						if(metricToInsert > 0)
						{
							metricsStatement.execute();
							metricsStatement.close();
							//System.out.println("Inserting " + allTyped);
							//System.out.println(keyPressStatement);
						}
						if(metrics)
						{
							metricTime = System.currentTimeMillis() - metricTime;
							recordMetric("Metric Write Time", metricTime, "ms", metricToInsert, "count");
						}
					}
					
					//presssToWrite.clear();
					count = 0;
					
					myConnection.commit();
					myConnection.close();
					
					timeTaken = System.currentTimeMillis() - startWriteout;
					if(metrics)
					{
						recordMetric("Total Writeout Time", timeTaken, "ms", toInsert + clickToInsert + keyToInsert + screenshotsToInsert + metricToInsert + toInsertProcess, "count");
					}
				}
				Thread.sleep(1000);
				//if(verbose)
				//System.out.println("Loop");
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			//if(new Random().nextInt() % 20 == 0)
			//{
				//update();
			
			//	System.out.println("Running in session " + sessionToken);
			//}
			System.gc();
		} while(running);
		cleanedUp = true;
		
	}

	@Override
	public synchronized void getScreenshotEvent(Date timeTaken, Image screenshot)
	{
		//System.out.println("Got a screenshot");
		if(paused)
		{
			return;
		}
		
		Timestamp newTimestamp = getUniqueCurrentTimestamp();
		
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
		Object[] myPair = new Object[7];
		
		try
		{
			ByteArrayOutputStream toByte = new ByteArrayOutputStream();
			ImageOutputStream imageOutput = ImageIO.createImageOutputStream(toByte);
			ImageWriter myWriter = ImageIO.getImageWritersByFormatName(imageCompressionType).next();
			myWriter.setOutput(imageOutput);
			
			//System.out.println("Comp type: " + imageCompressionType);
			
			if(diffMethod.equals("none"))
			{
				myPair[4] = "key";
				if(imageCompressionType.equals("jpg"))
				{
					JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
					jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					jpegParams.setCompressionQuality((float) imageCompressionFactor);
					myWriter.write(null, new IIOImage((RenderedImage) screenshot, null, null), jpegParams);
				}
				else if(imageCompressionType.equals("png"))
				{
					ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
					if(pngParam.canWriteCompressed())
					{
						pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						pngParam.setCompressionQuality((float) imageCompressionFactor);
					}
					myWriter.write(null, new IIOImage((RenderedImage) screenshot, null, null), pngParam);
				}
				
				myPair[0] = newTimestamp;//new Timestamp(timeTaken.getTime());
				myPair[1] = toByte.toByteArray();
				myPair[2] = userName;
				myPair[3] = imageCompressionType;
				myPair[5] = 0;
				myPair[6] = 0;
				screenshotsToWrite.add(myPair);
			}
			else
			{
				HashMap compressedMap = myFrameCompressor.compressNextFrame((RenderedImage) screenshot);
				if(compressedMap.get("frametype") != null)
				{
					myPair[0] = newTimestamp;//new Timestamp(timeTaken.getTime());
					myPair[1] = compressedMap.get("bytes");
					myPair[2] = userName;
					myPair[3] = imageCompressionType;
					myPair[4] = compressedMap.get("frametype");
					myPair[5] = compressedMap.get("x");
					myPair[6] = compressedMap.get("y");
					screenshotsToWrite.add(myPair);
				}
			}
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public ArrayList getInvisibleComponents() {
		return windowsToClose;
	}

	@Override
	public void pause()
	{
		paused = true;
	}

	@Override
	public void resume()
	{
		paused = false;
		checkNewInterrupt(myMonitor, timeDifference);
		//checkNew(myMonitor.getTopWindow(timeDifference));
	}

}
