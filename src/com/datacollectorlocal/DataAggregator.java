package com.datacollectorlocal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.sql.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.util.zip.*;
import java.io.*;
import java.net.URI;

import com.datacollectorlocal.TestingConnectionSource;

public class DataAggregator implements Runnable
{
	private ArrayList<UploadProgressListener> progressListeners = new ArrayList<UploadProgressListener>();
	
	public void addProgressListener(UploadProgressListener toAdd)
	{
		progressListeners.add(toAdd);
		
		if(myChecker == null)
		{
			myChecker = new UploadProgressChecker();
			checkerThread = new Thread(myChecker);
			checkerThread.start();
		}
	}
	
	private Thread checkerThread;
	private UploadProgressChecker myChecker = null;
	private class UploadProgressChecker implements Runnable
	{
		private String sentItems = "SELECT (SELECT count(1) from `dataCollection`.`KeyboardInput` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`MouseInput` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`PerformanceMetrics` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`Process` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`ProcessArgs` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`ProcessAttributes` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`ProcessThreads` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`Screenshot` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`Task` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`TaskEvent` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`Window` WHERE `insertTimestamp` <= ?) + (SELECT count(1) from `dataCollection`.`WindowDetails` WHERE `insertTimestamp` <= ?) as totalRows";
		private String remainingItems = "SELECT (SELECT count(1) from `dataCollection`.`KeyboardInput` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`MouseInput` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`PerformanceMetrics` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`Process` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`ProcessArgs` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`ProcessAttributes` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`ProcessThreads` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`Screenshot` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`Task` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`TaskEvent` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`Window` WHERE `insertTimestamp` > ?) + (SELECT count(1) from `dataCollection`.`WindowDetails` WHERE `insertTimestamp` > ?) as totalRows";
		private String getLastSubmit = "SELECT `lastTransfer` FROM `dataCollection`.`LastTransfer` ORDER BY `lastTransfer` DESC LIMIT 1";
		private boolean doACheck = false;
		private int interval = 10000;
		public void trigger()
		{
			doACheck = true;
		}
		public void run()
		{
			Connection myConnection = myConnectionSource.getDatabaseConnection();
			while(running)
			{
				if(doACheck)
				{
					doACheck = false;
					try
					{
						PreparedStatement lastSubmitStmt = myConnection.prepareStatement(getLastSubmit);
						ResultSet lastSubmitResults = lastSubmitStmt.executeQuery();
						Timestamp initTimestamp = null;
						if(!lastSubmitResults.first())
						{
							lastSubmitResults.close();
							lastSubmitStmt.close();
							continue;
						}
						else
						{
							if(lastSubmitResults.getObject(1) instanceof Timestamp)
							{
								initTimestamp = lastSubmitResults.getTimestamp(1);
							}
							else
							{
								System.out.println("Weird timestamp behavior");
								lastSubmitResults.close();
								lastSubmitStmt.close();
								continue;
							}
						}
						
						lastSubmitResults.close();
						lastSubmitStmt.close();
						
						PreparedStatement sentStmt = myConnection.prepareStatement(sentItems);
						
						for(int x = 0; x < 12; x++)
						{
							sentStmt.setTimestamp(x + 1, initTimestamp);
						}
						
						ResultSet sentResults = sentStmt.executeQuery();
						long sent = 0;
						
						ResultSet myResults = sentResults;
						if(myResults.next())
						{
							sent = myResults.getLong("totalRows");
						}
						else
						{
							System.out.println("No results for data sent.");
						}
						sentResults.close();
						sentStmt.close();
						
						
						PreparedStatement remainingStmt = myConnection.prepareStatement(remainingItems);
						
						for(int x = 0; x < 12; x++)
						{
							remainingStmt.setTimestamp(x + 1, initTimestamp);
						}
						
						
						ResultSet remainingResults = remainingStmt.executeQuery();
						long remaining = 0;
						myResults = remainingResults;
						if(myResults.next())
						{
							remaining = myResults.getLong("totalRows");
						}
						else
						{
							System.out.println("No results for data to send.");
						}
						remainingStmt.close();
						remainingResults.close();
						
						for(int x = 0; x < progressListeners.size(); x++)
						{
							progressListeners.get(x).updateProgress(sent, remaining);
						}
					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				}
				try
				{
					Thread.currentThread().sleep(interval);
				}
				catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	private static ConcurrentHashMap curAggregators = new ConcurrentHashMap();
	private TestingConnectionSource myConnectionSource;
	private boolean running = false;
	private String server = "";
	private long maxMessageSize = 750000;
	private String myUsername = "";
	private String myToken = "";
	private String myAdminEmail = "";
	private Thread myThread = null;
	private boolean daemon = false;
	private String myEvent = "";
	
	private long maxDiff = 6000;
	private long maxDiffCeiling = 600000000;
	private long maxDiffFloor = 600;
	
	private long maxByteLength = 68000000;
	
	private boolean sendMoreSessionNames = true;
	
	public void setSendMoreSessionNames(boolean toSend)
	{
		sendMoreSessionNames = toSend;
	}
	
	
	public static DataAggregator getInstance(String serverAddr, String username, String token, boolean continuous, String event, String admin)
	{
		if(!curAggregators.containsKey(serverAddr))
		{
			curAggregators.put(serverAddr, new ConcurrentHashMap());
		}
		ConcurrentHashMap tmpMap = (ConcurrentHashMap) curAggregators.get(serverAddr);
		if(!tmpMap.containsKey(username))
		{
			tmpMap.put(username, new ConcurrentHashMap());
		}
		ConcurrentHashMap tmptmpMap = (ConcurrentHashMap) tmpMap.get(username);
		if(tmptmpMap.containsKey(token))
		{
			DataAggregator myReturn = (DataAggregator) tmptmpMap.get(token);
			if(myReturn.myThread == null || !myReturn.myThread.isAlive())
			{
				myReturn = new DataAggregator(serverAddr, username, token, continuous, event, admin);
				tmptmpMap.put(token, myReturn);
			}
			return myReturn;
		}
		else
		{
			DataAggregator myReturn = new DataAggregator(serverAddr, username, token, continuous, event, admin);
			tmptmpMap.put(token, myReturn);
			return myReturn;
		}
	}
	
	private DataAggregator(String serverAddr, String username, String token, boolean continuous, String event, String admin)
	{
		daemon = continuous;
		myUsername = username;
		myToken = token;
		myEvent = event;
		server = serverAddr;
		myAdminEmail = admin;
		myConnectionSource = new TestingConnectionSource();
		myThread = new Thread(this, "dataAggregator");
		myThread.start();
	}
	
	@Override
	public void run()
	{
		WebsocketDataSender mySender = null;
		
		String tokenSelect = "SELECT * FROM `dataCollection`.`UploadToken` WHERE `username` = ?";
		
		String isTimeInvalid = "SELECT `lastTransfer`, UTC_TIMESTAMP(3) AS `currentTime`, `lastTransfer` > UTC_TIMESTAMP(3) AS `isInvalid` FROM `dataCollection`.`LastTransfer` ORDER BY `lastTransfer` DESC LIMIT 1";
		String resetTransfer = "DELETE FROM `dataCollection`.`LastTransfer`";
		
		String getLastSubmit = "SELECT `lastTransfer` FROM `dataCollection`.`LastTransfer` ORDER BY `lastTransfer` DESC LIMIT 1";
		String transferTimeInsertDefault = "INSERT IGNORE INTO `dataCollection`.`LastTransfer`(`lastTransfer`) VALUES (UTC_TIMESTAMP(3))";
		String selectScreenshotSizeLimit = "SELECT OCTET_LENGTH(`screenshot`), `insertTimestamp` FROM `dataCollection`.`Screenshot` WHERE `insertTimestamp` >= ? ORDER BY `insertTimestamp` ASC";
		String transferTimeInsert = "INSERT IGNORE INTO `dataCollection`.`LastTransfer`(`lastTransfer`) VALUES (?)";
		String currentTimeSelect = "SELECT UTC_TIMESTAMP(3)";
		String transferTimeSelect = "SELECT `lastTransfer` FROM `dataCollection`.`LastTransfer` ORDER BY `lastTransfer` DESC LIMIT 2";
		String userSelect = "SELECT * FROM `dataCollection`.`User`";
		String windowSelect = "SELECT * FROM `dataCollection`.`Window` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String windowDetailsSelect = "SELECT * FROM `dataCollection`.`WindowDetails` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String screenshotSelect = "SELECT * FROM `dataCollection`.`Screenshot` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processSelect = "SELECT * FROM `dataCollection`.`Process` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processArgsSelect = "SELECT * FROM `dataCollection`.`ProcessArgs` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processAttributesSelect = "SELECT * FROM `dataCollection`.`ProcessAttributes` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processThreadsSelect = "SELECT * FROM `dataCollection`.`ProcessThreads` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String mouseInputSelect = "SELECT * FROM `dataCollection`.`MouseInput` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String keyboardInputSelect = "SELECT * FROM `dataCollection`.`KeyboardInput` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String metricsSelect = "SELECT * FROM `dataCollection`.`PerformanceMetrics` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String taskSelect = "SELECT * FROM `dataCollection`.`Task` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String taskEventSelect = "SELECT * FROM `dataCollection`.`TaskEvent` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		
		String selectEarliestTime = "SELECT MIN(`insertTimestamp`) FROM (SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`KeyboardInput` WHERE `insertTimestamp` > ?     UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`MouseInput`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`Process`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`ProcessArgs`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`ProcessAttributes`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`Screenshot`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`Task`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`TaskEvent`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`User`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`Window`  WHERE `insertTimestamp` > ?    UNION SELECT IFNULL(MIN(`insertTimestamp`), UTC_TIMESTAMP(3)) AS `insertTimestamp` FROM `dataCollection`.`WindowDetails`  WHERE `insertTimestamp` > ?) AS `subQuery`";
		
		running = true;
		boolean sentFirst = false;
		boolean sendOneLastSession = true;
		
		String currentTimeQuery = "SELECT UTC_TIMESTAMP(3)";
		String initSelectScreenshotSizeLimit = "SELECT COUNT(*) FROM `dataCollection`.`Screenshot` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		Timestamp maxMax = new Timestamp(1);
		Connection preConnection = myConnectionSource.getDatabaseConnection();
		//String myToken = "";
		//try
		//{
		//	PreparedStatement userQuery = preConnection.prepareStatement(tokenSelect);
		//	userQuery.setString(1, myUsername);
		//	ResultSet myTokenResult = userQuery.executeQuery();
		//	myTokenResult.next();
		//	myToken = myTokenResult.getString("token");
		//}
		//catch(Exception e)
		//{
		//	e.printStackTrace();
		//}
		
		
		
		try
		{
			PreparedStatement maxStatement = preConnection.prepareStatement(currentTimeQuery);
			ResultSet maxResult = maxStatement.executeQuery();
			maxResult.next();
			maxMax = maxResult.getTimestamp(1);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		//int //currentDone = 0;
		//int //totalToDo = 1;
		
		try
		{
			PreparedStatement lastSubmitStmt = preConnection.prepareStatement(getLastSubmit, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet lastSubmitResults = lastSubmitStmt.executeQuery();
			Timestamp initTimestamp = null;
			if(!lastSubmitResults.first())
			{
				System.out.println("No last submit");
				initTimestamp = new Timestamp(1);
			}
			else
			{
				if(lastSubmitResults.getObject(1) instanceof Timestamp)
				{
					initTimestamp = lastSubmitResults.getTimestamp(1);
				}
				else
				{
					System.out.println("No last submit 2");
					initTimestamp = new Timestamp(1);
				}
			}
			System.out.println("From: " + initTimestamp);
			System.out.println("To max " + maxMax);
			//PreparedStatement maxStatement = preConnection.prepareStatement(initSelectScreenshotSizeLimit);
			//maxStatement.setTimestamp(2, initTimestamp);
			//maxStatement.setTimestamp(1, maxMax);
			//ResultSet maxResult = maxStatement.executeQuery();
			//maxResult.next();
			//totalToDo = maxResult.getInt(1);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		
		long lastFreeMemory = freeMemory();
		
		do
		{
			long totalObjectCount = 0;
			boolean shouldPause = true;
			
			if (this.maxDiff > this.maxDiffCeiling)
			{
				System.out.println("Limiting due to max diff: " + maxDiffCeiling);
				this.maxDiff = this.maxDiffCeiling;
			}
			if (this.maxDiff < this.maxDiffFloor)
			{
				System.out.println("Limiting due to min diff: " + maxDiffFloor);
				this.maxDiff = this.maxDiffFloor;
			}
			//try
			//{
			//	Thread.currentThread().sleep(5000);
			//}
			//catch(Exception e)
			//{
			//	e.printStackTrace();
			//}
			Connection myConnection = myConnectionSource.getDatabaseConnection();
			try
			{
				PreparedStatement lastSubmitStmt = myConnection.prepareStatement(getLastSubmit, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
				ResultSet lastSubmitResults = lastSubmitStmt.executeQuery();
				Timestamp initTimestamp = null;
				if(!lastSubmitResults.first())
				{
					initTimestamp = new Timestamp(1);
				}
				else
				{
					if(lastSubmitResults.getObject(1) instanceof Timestamp)
					{
						initTimestamp = lastSubmitResults.getTimestamp(1);
					}
					else
					{
						System.out.println("Weird timestamp behavior");
						initTimestamp = new Timestamp(1);
					}
				}
				
				Timestamp maxTime = null;
				
				PreparedStatement maxStatement = preConnection.prepareStatement(currentTimeQuery);
				ResultSet maxResult = maxStatement.executeQuery();
				maxResult.next();
				maxTime = maxResult.getTimestamp(1);
				
				
				PreparedStatement myStmt = myConnection.prepareStatement(transferTimeSelect);
				ResultSet myResults = myStmt.executeQuery();
				//myResults.next();
				Timestamp curTimestamp = maxTime;
				Timestamp lastTimestamp = new Timestamp(1);
				//Timestamp curTimestamp = myResults.getTimestamp(1);
				//Timestamp lastTimestamp = new Timestamp(1);
				if(myResults.next())
				{
					lastTimestamp = myResults.getTimestamp(1);
				}
				
				if(lastTimestamp.after(maxMax) && !daemon)
				{
					running = false;
					if(mySender != null && mySender.isOpen())
					{
						mySender.send("end");
						mySender.closeBlocking();
					}
					System.out.println("Upload complete!");
				}
				else if(lastTimestamp.after(maxMax))
				{
					System.out.println("Finished sync, looping for more");
				}
				
				long diff = curTimestamp.getTime() - lastTimestamp.getTime();
				if (diff > maxDiff)
				{
					shouldPause = false;
					curTimestamp = (maxTime = new Timestamp(lastTimestamp.getTime() + maxDiff));
					System.out.println("Limiting time due to large diff " + diff);
					System.out.println("By " + maxDiff);
				}
				else
				{
						shouldPause = true;
				}
				//System.out.println("Getting entries from " + lastTimestamp + " to " + curTimestamp);
				
				System.out.println("Sending from " + lastTimestamp + " to " + curTimestamp);
				
				//Gson gson = new GsonBuilder().setPrettyPrinting().create();
				Gson gson = new GsonBuilder().create();
				HashMap totalObjects = new HashMap();
				
				totalObjects.put("username", myUsername);
				totalObjects.put("token", myToken);
				totalObjects.put("event", myEvent);
				totalObjects.put("admin", myAdminEmail);
				
				
				if(sendOneLastSession)
				{
					myStmt = myConnection.prepareStatement(userSelect);
					//myStmt.setTimestamp(1, curTimestamp);
					//myStmt.setTimestamp(2, lastTimestamp);
					myResults = myStmt.executeQuery();
					ArrayList userList = new ArrayList();
					while(myResults.next())
					{
						HashMap curMap = new HashMap();
						
						int colCount = myResults.getMetaData().getColumnCount();
						for(int x=1; x<colCount + 1; x++)
						{
							curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
						}
						
						userList.add(curMap);
					}
					totalObjects.put("User", userList);
				}
				
				myStmt = myConnection.prepareStatement(windowSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList windowList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					windowList.add(curMap);
				}
				totalObjects.put("Window", windowList);
				
				myStmt = myConnection.prepareStatement(windowDetailsSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList windowDetailsList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					windowDetailsList.add(curMap);
				}
				totalObjects.put("WindowDetails", windowDetailsList);
				
				myStmt = myConnection.prepareStatement(screenshotSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList screenshotList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						if(myResults.getMetaData().getColumnLabel(x).equals("screenshot"))
						{
							curMap.put(myResults.getMetaData().getColumnLabel(x), Base64.getEncoder().encodeToString(myResults.getBytes(x)));
						}
						else
						{
							curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
						}
					}
					
					screenshotList.add(curMap);
				}
				totalObjects.put("Screenshot", screenshotList);
				
				myStmt = myConnection.prepareStatement(processSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList processList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					processList.add(curMap);
				}
				totalObjects.put("Process", processList);
				
				myStmt = myConnection.prepareStatement(processArgsSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList processArgsList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					processArgsList.add(curMap);
				}
				totalObjects.put("ProcessArgs", processArgsList);
				
				myStmt = myConnection.prepareStatement(processAttributesSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList processAttributesList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					processAttributesList.add(curMap);
				}
				totalObjects.put("ProcessAttributes", processAttributesList);
				
				myStmt = myConnection.prepareStatement(processThreadsSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList processThreadsList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					processThreadsList.add(curMap);
				}
				totalObjects.put("ProcessThreads", processThreadsList);
				
				myStmt = myConnection.prepareStatement(mouseInputSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList mouseInputList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					mouseInputList.add(curMap);
				}
				totalObjects.put("MouseInput", mouseInputList);
				
				myStmt = myConnection.prepareStatement(keyboardInputSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList keyboardInputList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					keyboardInputList.add(curMap);
				}
				totalObjects.put("KeyboardInput", keyboardInputList);
				
				myStmt = myConnection.prepareStatement(taskSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList taskList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					taskList.add(curMap);
				}
				totalObjects.put("Task", taskList);
				
				myStmt = myConnection.prepareStatement(taskEventSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList taskEventList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					taskEventList.add(curMap);
				}
				totalObjects.put("TaskEvent", taskEventList);
				
				
				myStmt = myConnection.prepareStatement(metricsSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList metricsList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getString(x));
					}
					
					metricsList.add(curMap);
				}
				totalObjects.put("PerformanceMetrics", metricsList);
				
				
				Iterator myIter = totalObjects.entrySet().iterator();
				while(myIter.hasNext())
				{
					Entry objEntry = (Entry) myIter.next();
					if (objEntry.getValue() instanceof ArrayList)
					{
						ArrayList objList = (ArrayList)objEntry.getValue();
						System.out.println("Sending " + objList.size() + " from " + objEntry.getKey());
						if(objEntry.getKey().equals("User"))
						{
							
						}
						else
						{
							totalObjectCount += objList.size();
						}
					}
				}
				//totalObjects.put("totalToDo", totalToDo);
				//totalObjects.put("totalDone", currentDone);
				
				//System.out.println("Windows detected:");
				//String windowJSON = gson.toJson(totalObjects.get("Window"));
				//System.out.println(windowJSON);
				//windowJSON = gson.toJson(totalObjects.get("WindowDetails"));
				//System.out.println(windowJSON);
				
				String totalJSON = gson.toJson(totalObjects);
				boolean sendTheData = true;
				long dataSize = totalJSON.length() * 4;
				if(dataSize > maxByteLength || totalObjectCount == 0)
				{
					if(dataSize > maxByteLength)
					{
						System.out.println("Not sending due to data size: " + dataSize);
					}
					sendTheData = false;
				}
				
				String compressedString = null;
				if(sendTheData)
				{
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					GZIPOutputStream gzip = new GZIPOutputStream(output);
					gzip.write(totalJSON.getBytes());
					gzip.close();
					byte[] compressed = output.toByteArray();
					compressedString = new String(Base64.getEncoder().encode(compressed));
				}
				
				System.out.println("Sending to: " + server);
				
				
				
				while(mySender == null || (!mySender.isOpen()))
				{
					
					//mySender.connectBlocking();
					//if(!mySender.isOpen())
					//{
						if(mySender != null && mySender.isOpen())
						{
							mySender.closeBlocking();
						}
						mySender = new WebsocketDataSender(new URI(server));
						maxByteLength = mySender.getMaxPacket();
						
						if(!mySender.isOpen())
						{
							mySender = null;
							Thread.currentThread().sleep(5000);
						}
					//}
					//Thread.currentThread().sleep(2500);
				}
				
				double sendStart = System.currentTimeMillis();
				
				String responseString = null;
				if(sendTheData)
				{
					responseString = mySender.sendWait(compressedString);
				}
				
				double sendTime = System.currentTimeMillis() - sendStart;
				
				double uploadRate = 0;
				if(sendTheData)
				{
					uploadRate = (double) compressedString.length() / (sendTime / 1000);
				}
				
				double maxUploadTime = 100000;
				
				double headroom = 1 - (sendTime / maxUploadTime);
				double headroom2 = 1 - (dataSize / (maxByteLength));
				if(headroom2 < headroom)
				{
					headroom = headroom2;
				}
				System.out.println("Upload time: " + sendTime);
				System.out.println("Headroom: " + (headroom));
				
				while(mySender == null || (!mySender.isOpen()))
				{
					
					//mySender.connectBlocking();
					//if(!mySender.isOpen())
					//{
						if(mySender != null && mySender.isOpen())
						{
							mySender.closeBlocking();
						}
						mySender = new WebsocketDataSender(new URI(server));
						maxByteLength = mySender.getMaxPacket();
						
						if(!mySender.isOpen())
						{
							mySender = null;
							Thread.currentThread().sleep(5000);
						}
					//}
					//Thread.currentThread().sleep(2500);
				}
				
				
				
				boolean isIdle = false;
				
				if(totalObjectCount == 0)
				{
					isIdle = true;
					System.out.println("Nothing, fast forward to:");
					PreparedStatement earliestStmt = myConnection.prepareStatement(selectEarliestTime);
					earliestStmt.setTimestamp(1, lastTimestamp);
					earliestStmt.setTimestamp(2, lastTimestamp);
					earliestStmt.setTimestamp(3, lastTimestamp);
					earliestStmt.setTimestamp(4, lastTimestamp);
					earliestStmt.setTimestamp(5, lastTimestamp);
					earliestStmt.setTimestamp(6, lastTimestamp);
					earliestStmt.setTimestamp(7, lastTimestamp);
					earliestStmt.setTimestamp(8, lastTimestamp);
					earliestStmt.setTimestamp(9, lastTimestamp);
					earliestStmt.setTimestamp(10, lastTimestamp);
					earliestStmt.setTimestamp(11, lastTimestamp);
					final ResultSet earliestResults = earliestStmt.executeQuery();
					final Timestamp earliestTimestamp = null;
					if (earliestResults.next())
					{
						maxTime = earliestResults.getTimestamp(1);
					}
					System.out.println(maxTime);
					earliestStmt.close();
					
					
					myStmt = myConnection.prepareStatement(transferTimeInsert);
					myStmt.setTimestamp(1, maxTime);
					myStmt.execute();
				}
				
				if(responseString != null && responseString.equals("{\"result\":\"ok\"}"))
				{
					if(sentFirst)
					{
						sendOneLastSession = false;
					}
					if(!sendMoreSessionNames)
					{
						sentFirst = true;
					}
					else
					{
						sentFirst = false;
						sendOneLastSession = true;
					}
					System.out.println("All OK:");
					System.out.println(responseString);
					if(maxTime == null)
					{
						//System.out.println("Putting data up until now");
						myStmt = myConnection.prepareStatement(transferTimeInsertDefault);
						myStmt.execute();
					}
					else
					{
						//System.out.println("Putting data up until " + maxTime);
						myStmt = myConnection.prepareStatement(transferTimeInsert);
						myStmt.setTimestamp(1, maxTime);
						myStmt.execute();
					}
					
					long curFreeMemory = freeMemory();
					long lastSendAllocation = lastFreeMemory - curFreeMemory;
					
					System.out.println("Available memory: " + curFreeMemory);
					
					if((((3 * lastSendAllocation) < (curFreeMemory)) && ((dataSize) < curFreeMemory)) || headroom < 0)
					{
						//If we have enough memory space then we multiply by the
						//increment.  The increment is 2 multiplied by the amount
						//of headroom.  Headroom is the amount of time it takes
						//to transmit the last upload as a portion of the max
						//time before hitting a timeout, or the amount of data
						//sent as a portion of the max data size.  All of this makes
						//messages the maximal size before hitting memory or
						//timeout or size limits.
						maxDiff *= (2 * headroom);
					}
					else
					{
						System.out.println("Not increasing due to memory and/or packet limit.");
					}
					
					lastFreeMemory = curFreeMemory;
					
					if(myChecker != null)
					{
						myChecker.trigger();
					}
					
					for(int x = 0; x < progressListeners.size(); x++)
					{
						if(!sendTheData)
						{
							progressListeners.get(x).updateStatus("Max upload size reached, throttling...");
						}
						else if(isIdle)
						{
							progressListeners.get(x).updateStatus("Searching for new data...");
						}
						else
						{
							progressListeners.get(x).updateStatus("Upload progress OK at " + (int)Math.round(uploadRate) + " b/s");
						}
					}
					
				}
				else
				{
					if(!sendTheData)
					{
						System.out.println("Local throttle");
					}
					else
					{
						System.out.println("Not OK:");
						System.out.println(responseString);
					}
					
					for(int x = 0; x < progressListeners.size(); x++)
					{
						if(isIdle)
						{
							progressListeners.get(x).updateStatus("Searching for new data...");
						}
						else
						{
							if(!sendTheData)
							{
								progressListeners.get(x).updateStatus("Max upload size reached, throttling...");
							}
							else if(responseString == null)
							{
								progressListeners.get(x).updateStatus("Upload timeout, throttling speed");
							}
							else
							{
								progressListeners.get(x).updateStatus("Upload error, see log");
							}
						}
					}
					
					if(mySender.isClosing())
					{
						mySender.closeBlocking();
					}
					if(mySender.isClosed())
					{
						if(mySender != null && mySender.isOpen())
						{
							mySender.closeBlocking();
						}
						mySender = new WebsocketDataSender(new URI(server));
						maxByteLength = mySender.getMaxPacket();
					}
					shouldPause = true;
					if(totalObjectCount != 0)
					{
						maxDiff /= 3L;
					}
				}
				
				System.out.println("Cur max diff: " + maxDiff);
				
				/*byte[] buffer = new byte[1024];
				int length = 0;
				if(entity != null)
				{
					InputStream instream = entity.getContent();
					int len = 0;
					buffer = new byte[1024];
					output = new ByteArrayOutputStream();
					while((len = instream.read(buffer)) > 0)
					{
						output.write(buffer, 0, length);
					}
					instream.close();
					output.close();
					//System.out.println(new String(output.toByteArray()));
				}*/
					
				//running=false;
				
				//System.out.println(totalJSON);
				myConnection.close();
				if (this.maxDiff > this.maxDiffCeiling)
				{
					this.maxDiff = this.maxDiffCeiling;
				}
				if (this.maxDiff < this.maxDiffFloor)
				{
					this.maxDiff = this.maxDiffFloor;
				}
				if (!shouldPause)
				{
					continue;
				}
				Thread.currentThread();
				Thread.sleep(5000);
			}
			catch(Exception e)
			{
				try
				{
					Thread.sleep(5000);
				}
				catch(Exception e1)
				{
					e1.printStackTrace();
				}
				maxDiff /= 2L;
				if (this.maxDiff > this.maxDiffCeiling)
				{
					this.maxDiff = this.maxDiffCeiling;
				}
				if (this.maxDiff < this.maxDiffFloor)
				{
					this.maxDiff = this.maxDiffFloor;
				}
				e.printStackTrace();
			}
			
			System.out.println("Checking timestamp validity:");
			myConnection = myConnectionSource.getDatabaseConnection();
			try
			{
				PreparedStatement checkTime = myConnection.prepareStatement(isTimeInvalid);
				ResultSet timeResults = checkTime.executeQuery();
				if(timeResults.next())
				{
					Timestamp lastTransfer = timeResults.getTimestamp("lastTransfer");
					Timestamp currentTime = timeResults.getTimestamp("currentTime");
					boolean isInvalid = timeResults.getBoolean("isInvalid");
					System.out.println("Last Transfer: " + lastTransfer);
					System.out.println("Current Time: " + currentTime);
					System.out.println("Is Invalid? " + isInvalid);
					timeResults.close();
					checkTime.close();
					if(isInvalid)
					{
						myConnection.prepareStatement(resetTransfer).execute();
					}
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			try
			{
				myConnection.close();
			}
			catch(SQLException e)
			{
				e.printStackTrace();
			}
			
			
		} while(running);
	}
	
	private long freeMemory()
	{
		long allocatedMemory = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
		long presumableFreeMemory = Runtime.getRuntime().maxMemory() - allocatedMemory;
		return presumableFreeMemory;
	}
	/*
	private String toJSON(ArrayList input, String varName)
	{
		String myReturn = "{\n\t" + "\"" + varName + "\":" + "\n\t[";
		
		for(int x=0; x <input.size(); x++)
		{
			HashMap curMap = (HashMap) input.get(x);
			String jsonString = "\n\t\t{";
			if(x > 0)
			{
				jsonString = "," + jsonString;
			}
			
			Iterator curIterator = curMap.entrySet().iterator();
			boolean first = true;
			while(curIterator.hasNext())
			{
				if(first)
				{
					first = false;
				}
				else
				{
					jsonString += ",";
				}
				Entry curEntry = (Entry) curIterator.next();
				jsonString += "\n\t\t\t\"" + curEntry.getKey() + "\": ";
				if(curEntry.getValue() instanceof Integer || curEntry.getValue() instanceof Long || curEntry.getValue() instanceof Double || curEntry.getValue() instanceof Float)
				{
					jsonString += curEntry.getValue();
				}
				else
				{
					jsonString += "\"" + curEntry.getValue() + "\"";
				}
			}
			
			jsonString += "\n\t\t}";
			myReturn += jsonString;
		}
		
		myReturn += "\n\t]\n}";
		
		return myReturn;
	}
	*/
	
	public void stop()
	{
		running = false;
	}

}
