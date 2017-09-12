package com.datacollectorlocal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.sql.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import java.util.zip.GZIPInputStream;
import java.util.zip.*;
import java.io.*;

import com.datacollectorlocal.TestingConnectionSource;

public class DataAggregator implements Runnable
{
	private TestingConnectionSource myConnectionSource;
	private boolean running = false;
	private String server = "";
	public DataAggregator(String serverAddr)
	{
		server = serverAddr;
		myConnectionSource = new TestingConnectionSource();
		Thread myThread = new Thread(this);
		myThread.start();
	}
	
	@Override
	public void run()
	{
		String getLastSubmit = "";
		String transferTimeInsert = "INSERT INTO `dataCollection`.`LastTransfer`(`lastTransfer`) VALUES (CURRENT_TIMESTAMP)";
		String transferTimeSelect = "SELECT `lastTransfer` FROM `dataCollection`.`LastTransfer` ORDER BY `lastTransfer` DESC LIMIT 2";
		String userSelect = "SELECT * FROM `dataCollection`.`User` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String windowSelect = "SELECT * FROM `dataCollection`.`Window` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String windowDetailsSelect = "SELECT * FROM `dataCollection`.`WindowDetails` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String screenshotSelect = "SELECT * FROM `dataCollection`.`Screenshot` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processSelect = "SELECT * FROM `dataCollection`.`Process` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processArgsSelect = "SELECT * FROM `dataCollection`.`ProcessArgs` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String processAttributesSelect = "SELECT * FROM `dataCollection`.`ProcessAttributes` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String mouseInputSelect = "SELECT * FROM `dataCollection`.`MouseInput` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		String keyboardInputSelect = "SELECT * FROM `dataCollection`.`KeyboardInput` WHERE `insertTimestamp` <= ? AND `insertTimestamp` >= ?";
		running = true;
		do
		{
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
				PreparedStatement myStmt = myConnection.prepareStatement(transferTimeInsert);
				myStmt.execute();
				myStmt = myConnection.prepareStatement(transferTimeSelect);
				ResultSet myResults = myStmt.executeQuery();
				myResults.next();
				Timestamp curTimestamp = myResults.getTimestamp(1);
				Timestamp lastTimestamp = new Timestamp(0);
				if(myResults.next())
				{
					lastTimestamp = myResults.getTimestamp(1);
				}
				System.out.println("Getting entries from " + lastTimestamp + " to " + curTimestamp);
				Thread.currentThread().sleep(5000);
				
				//Gson gson = new GsonBuilder().setPrettyPrinting().create();
				Gson gson = new GsonBuilder().create();
				HashMap totalObjects = new HashMap();
				
				myStmt = myConnection.prepareStatement(userSelect);
				myStmt.setTimestamp(1, curTimestamp);
				myStmt.setTimestamp(2, lastTimestamp);
				myResults = myStmt.executeQuery();
				ArrayList userList = new ArrayList();
				while(myResults.next())
				{
					HashMap curMap = new HashMap();
					
					int colCount = myResults.getMetaData().getColumnCount();
					for(int x=1; x<colCount + 1; x++)
					{
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
					}
					
					processAttributesList.add(curMap);
				}
				totalObjects.put("ProcessAttributes", processAttributesList);
				
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
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
						curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
					}
					
					keyboardInputList.add(curMap);
				}
				totalObjects.put("KeyboardInput", keyboardInputList);
				
				
				String totalJSON = gson.toJson(totalObjects);
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
				
				HttpClient httpclient = HttpClients.createDefault();
				HttpPost httppost = new HttpPost(server);
				List<NameValuePair> params = new ArrayList<NameValuePair>(1);
				params.add(new BasicNameValuePair("uploadData", compressedString));
				httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();
				if(entity != null)
				{
					InputStream instream = entity.getContent();
					//int len = 0;
					//buffer = new byte[1024];
					//output = new ByteArrayOutputStream();
					//while((len = instream.read(buffer)) > 0)
					//{
					//	output.write(buffer, 0, length);
					//}
					instream.close();
					//output.close();
					//System.out.println(new String(output.toByteArray()));
				}
					
				//running=false;
				
				//System.out.println(totalJSON);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			
		} while(running);
	}
	
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
	
	public void stop()
	{
		running = false;
	}

}
