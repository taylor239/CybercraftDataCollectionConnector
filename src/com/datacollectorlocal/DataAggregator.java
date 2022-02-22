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
	
	private long maxDiff = 600000;
	private long maxDiffCeiling = 600000000;
	private long maxDiffFloor = 60000;
	
	
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
				this.maxDiff = this.maxDiffCeiling;
			}
			if (this.maxDiff < this.maxDiffFloor)
			{
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
				
				//PreparedStatement maxStatement = myConnection.prepareStatement(initSelectScreenshotSizeLimit);
				//maxStatement.setTimestamp(2, initTimestamp);
				//maxStatement.setTimestamp(1, maxMax);
				//ResultSet maxResult = maxStatement.executeQuery();
				//maxResult.next();
				//totalToDo = maxResult.getInt(1);
				
				//System.out.println("Checking images from after " + initTimestamp);
				
				//PreparedStatement screenshotSizeStatement = myConnection.prepareStatement(selectScreenshotSizeLimit);
				//screenshotSizeStatement.setTimestamp(1, initTimestamp);
				//ResultSet screenshotSizeResults = screenshotSizeStatement.executeQuery();
				//long curSize = 0;
				Timestamp maxTime = null;
				/*
				while(screenshotSizeResults.next())
				{
					long entrySize = screenshotSizeResults.getLong(1);
					if(curSize + entrySize <= maxMessageSize)
					{
						curSize += entrySize;
						currentDone++;
					}
					else
					{
						maxTime = screenshotSizeResults.getTimestamp(2);
						shouldPause = false;
						break;
					}
				}
				*/
				//PreparedStatement myStmt = null;
				PreparedStatement maxStatement = preConnection.prepareStatement(currentTimeQuery);
				ResultSet maxResult = maxStatement.executeQuery();
				maxResult.next();
				maxTime = maxResult.getTimestamp(1);
				//if(maxTime == null)
				//{
				//	myStmt = myConnection.prepareStatement(transferTimeSelect);
				//	ResultSet myResults = myStmt.executeQuery();
					
					//maxTime = new Timestamp(System.currentTimeMillis());
				//	if(myResults.next())
				//	{
				//		maxTime = myResults.getTimestamp(1);
				//	}
					//System.out.println("Putting data up until now");
					//myStmt = myConnection.prepareStatement(transferTimeInsertDefault);
					//myStmt.execute();
				//}
				//else
				//{
					//System.out.println("Putting data up until " + maxTime);
					//myStmt = myConnection.prepareStatement(transferTimeInsert);
					//myStmt.setTimestamp(1, maxTime);
					//myStmt.execute();
				//}
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
				
				String totalJSON = gson.toJson(totalObjects);
				//System.out.println("Total: " + totalJSON);
				//Writer tmpFile = new FileWriter("/home/osboxes/Desktop/empty.txt");
				//tmpFile.write(totalJSON);
				//tmpFile.close();
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				GZIPOutputStream gzip = new GZIPOutputStream(output);
				gzip.write(totalJSON.getBytes());
				gzip.close();
				byte[] compressed = output.toByteArray();
				
				String compressedString = new String(Base64.getEncoder().encode(compressed));
				//compressed = Base64.getDecoder().decode(compressedString);
				
				//ByteArrayInputStream input = new ByteArrayInputStream(compressed);
				//GZIPInputStream ungzip = new GZIPInputStream(input);
				//output = new ByteArrayOutputStream();
				//byte[] buffer = new byte[1024];
				//int length = 0;
				//while((length = ungzip.read(buffer)) > 0)
				//{
				//	output.write(buffer, 0, length);
				//}
				//ungzip.close();
				//byte[] uncompressed = output.toByteArray();
				
				//System.out.println(((double)(totalJSON.length())) / 1000000.0);
				//System.out.println(((double)(compressed.length)) / 1000000.0);
				//System.out.println(((double)(compressedString.length())) / 1000000.0);
				//System.out.println(((double)(uncompressed.length)) / 1000000.0);
				
				//String uncompressedString = new String(uncompressed);
				//System.out.println(uncompressedString.equals(totalJSON));
				
				//System.out.println(compressedString.substring(0, 25));
				
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
						
						if(!mySender.isOpen())
						{
							mySender = null;
							Thread.currentThread().sleep(5000);
						}
					//}
					//Thread.currentThread().sleep(2500);
				}
				String responseString = mySender.sendWait(compressedString);
				
				/*
				HttpClient httpclient = HttpClients.createDefault();
				HttpPost httppost = new HttpPost(server);
				List<NameValuePair> params = new ArrayList<NameValuePair>(1);
				params.add(new BasicNameValuePair("uploadData", compressedString));
				httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();
				System.out.println("Sent " + compressedString.length());
				
				String responseString = EntityUtils.toString(entity, "UTF-8");
				*/
				
				if (totalObjectCount == 0)
				{
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
				}
				
				if(responseString != null && responseString.equals("{\"result\":\"ok\"}"))
				{
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
					long dataSize = totalJSON.length() * 4;
					
					System.out.println("Available memory: " + curFreeMemory);
					
					if(((2 * lastSendAllocation) < (curFreeMemory)) && (dataSize < curFreeMemory))
					{
						maxDiff *= 2;
					}
					else
					{
						System.out.println("Not increasing due to memory limit.");
					}
					
					lastFreeMemory = curFreeMemory;
				}
				else
				{
					System.out.println("Not OK:");
					System.out.println(responseString);
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
					}
					shouldPause = true;
					maxDiff /= 2L;
				}
				
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
			try {
				myConnection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
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
