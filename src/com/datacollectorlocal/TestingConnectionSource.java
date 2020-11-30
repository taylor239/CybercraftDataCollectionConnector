package com.datacollectorlocal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;
import java.sql.Connection;

public class TestingConnectionSource
{
	private Connection myConnection;
	
	
	String userName = "dataCollector";
	String password = "LFgVMrQ8rqR41StN";
	String address = "jdbc:mysql://localhost:3306?useLegacyDatetimeCode=false&serverTimezone=UTC&zerodatetimebehavior=Round";
	
	public TestingConnectionSource()
	{
		
		/*
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}
		*/
	}
	
	public Connection getDatabaseConnection()
	{
		if(myConnection != null)
		{
			return myConnection;
		}
		try
		{
			Connection newConnection = DriverManager.getConnection(address, userName, password);
			return newConnection;
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public void returnConnection(Connection toReturn)
	{
		myConnection = toReturn;
		//try
		//{
		//	toReturn.close();
		//}
		//catch (SQLException e)
		//{
		//	e.printStackTrace();
		//}
	}

}