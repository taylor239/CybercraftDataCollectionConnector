package com.datacollectorlocal;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;


import java.awt.Component;

public class TaskInputGUI extends JFrame implements ActionListener
{
	private GridBagConstraints myConstraints;
	private JTextField taskField;
	private JButton addButton, pauseButton;
	private TestingConnectionSource connectionSource = new TestingConnectionSource();
	private String eventName;
	private String userName, adminEmail;
	private String session;
	private ConcurrentHashMap endMap;
	private ConcurrentHashMap pauseMap;
	private ConcurrentHashMap resumeMap;
	private ConcurrentHashMap restartMap;
	private int startingRow = 0;
	private int startingCol = 0;
	private ArrayList tableComponents;
	private ArrayList pauseListeners;
	
	public TaskInputGUI(String event, String user, String admin, String sess)
	{
		pauseListeners = new ArrayList();
		
		eventName = event;
		userName = user;
		adminEmail = admin;
		session = sess;
		
		tableComponents = new ArrayList();
		
		endMap = new ConcurrentHashMap();
		pauseMap = new ConcurrentHashMap();
		resumeMap = new ConcurrentHashMap();
		restartMap = new ConcurrentHashMap();
		
		setTitle("Current Tasks");
		myConstraints = new GridBagConstraints();
		myConstraints.fill = GridBagConstraints.HORIZONTAL;
		myConstraints.anchor = GridBagConstraints.CENTER;
		myConstraints.insets = new Insets(1, 1, 1, 1);
		myConstraints.weightx = 1.0;
		myConstraints.gridy = 0;
		myConstraints.gridx = 0;
		
		getContentPane().setLayout(new GridBagLayout());
		
		myConstraints.gridwidth = 3;
		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(this);
		getContentPane().add(pauseButton, myConstraints);
		myConstraints.gridwidth = 1;
		myConstraints.gridy++;
		
		getContentPane().add(new JLabel("Task:"), myConstraints);
		myConstraints.gridx++;
		taskField = new JTextField(20);
		getContentPane().add(taskField, myConstraints);
		myConstraints.gridx++;
		addButton = new JButton("Add");
		addButton.addActionListener(this);
		getContentPane().add(addButton, myConstraints);
		
		startingRow = myConstraints.gridy + 1;
		startingCol = 0;
		
		refreshView();
	}
	
	public void addPauseListener(PauseListener myListener)
	{
		pauseListeners.add(myListener);
	}
	
	public void refreshView()
	{
		Connection myConnection = connectionSource.getDatabaseConnection();
		
		try
		{
			Timestamp curTimestamp = new Timestamp(new Date().getTime());
			while(myConnection == null)
			{
				Thread.sleep(5000);
				myConnection = connectionSource.getDatabaseConnection();
			}
			myConnection.setAutoCommit(false);
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
			
			String windowSelect = "SELECT * FROM `dataCollection`.`Task` c INNER JOIN\n" + 
					"(\n" + 
					"SELECT *\n" + 
					"FROM `dataCollection`.`TaskEvent` a\n" + 
					"INNER JOIN (\n" + 
					"    SELECT MAX(`TaskEvent`.`eventTime`) AS `eventTimeMax`\n" + 
					"    FROM `dataCollection`.`Task`\n" + 
					"    INNER JOIN `dataCollection`.`TaskEvent` ON `Task`.`event` = `TaskEvent`.`event` AND `Task`.`username` = `TaskEvent`.`username` AND `Task`.`session` = `TaskEvent`.`session` AND `Task`.`taskName` = `TaskEvent`.`taskName` AND `Task`.`startTimestamp` = `TaskEvent`.`startTimestamp` AND `Task`.`adminEmail` = `TaskEvent`.`adminEmail` GROUP BY `Task`.`startTimestamp`, `Task`.`username`, `Task`.`session`, `Task`.`taskName`, `Task`.`startTimestamp`, `Task`.`adminEmail`\n" + 
					") b\n" + 
					"ON a.`eventTime` = b.`eventTimeMax`\n" + 
					") d\n" + 
					"ON c.`taskName` = d.`taskName` AND c.`event` = d.`event` AND c.`adminEmail` = d.`adminEmail` AND c.`username` = d.`username` AND c.`session` = d.`session` AND c.`startTimestamp` = d.`startTimestamp`" +
					"WHERE c.`username` = ? AND c.`event` = ? AND c.`adminEmail` = ? AND c.`completion` = 0 ORDER BY c.`startTimestamp` ASC";
			
			//System.out.println(windowSelect);
			PreparedStatement windowStatement = myConnection.prepareStatement(windowSelect);
			windowStatement.setString(1, userName);
			windowStatement.setString(2, eventName);
			windowStatement.setString(3, adminEmail);
			ResultSet myResults = windowStatement.executeQuery();
			
			myConstraints.gridx = startingCol;
			myConstraints.gridy = startingRow;
			
			System.out.println("Removing old rows");
			for(int x = 0; x < tableComponents.size(); x++)
			{
				getContentPane().remove((Component)tableComponents.get(x));
			}
			
			tableComponents = new ArrayList();
			
			endMap.clear();
			pauseMap.clear();
			resumeMap.clear();
			restartMap.clear();
			
			System.out.println("Adding rows");
			
			while(myResults.next())
			{
				myConstraints.gridx = startingCol;
				HashMap curMap = new HashMap();
				
				int colCount = myResults.getMetaData().getColumnCount();
				for(int x=1; x<colCount + 1; x++)
				{
					curMap.put(myResults.getMetaData().getColumnLabel(x), myResults.getObject(x));
				}
				
				
				
				System.out.println("Adding " + curMap.get("taskName"));
				JLabel curLabel = new JLabel((String) curMap.get("taskName"));
				getContentPane().add(curLabel, myConstraints);
				tableComponents.add(curLabel);
				myConstraints.gridx++;
				
				if(!curMap.get("session").equals(session))
				{
					JButton restartButton = new JButton("Restart");
					getContentPane().add(restartButton, myConstraints);
					restartMap.put(restartButton, curMap);
					tableComponents.add(restartButton);
					restartButton.addActionListener(this);
					myConstraints.gridx++;
					JButton endButton = new JButton("End");
					getContentPane().add(endButton, myConstraints);
					endMap.put(endButton, curMap);
					tableComponents.add(endButton);
					endButton.addActionListener(this);
				}
				else if(!curMap.get("eventDescription").equals("pause"))
				{
					JButton pauseButton = new JButton("Pause");
					getContentPane().add(pauseButton, myConstraints);
					pauseMap.put(pauseButton, curMap);
					tableComponents.add(pauseButton);
					pauseButton.addActionListener(this);
					myConstraints.gridx++;
					JButton endButton = new JButton("End");
					getContentPane().add(endButton, myConstraints);
					endMap.put(endButton, curMap);
					tableComponents.add(endButton);
					endButton.addActionListener(this);
				}
				else
				{
					JButton pauseButton = new JButton("Resume");
					getContentPane().add(pauseButton, myConstraints);
					resumeMap.put(pauseButton, curMap);
					tableComponents.add(pauseButton);
					pauseButton.addActionListener(this);
					myConstraints.gridx++;
					JButton endButton = new JButton("End");
					getContentPane().add(endButton, myConstraints);
					endMap.put(endButton, curMap);
					tableComponents.add(endButton);
					endButton.addActionListener(this);
				}
				
				myConstraints.gridy++;
				
			}
			getContentPane().repaint();
			getContentPane().validate();
			repaint();
			validate();
			setVisible(true);
			
		}
		catch (Exception e2)
		{
			e2.printStackTrace();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		Timestamp curTimestamp = new Timestamp(new Date().getTime());
		
		if(e.getSource().equals(pauseButton))
		{
			for(int x=0; x<pauseListeners.size(); x++)
			{
				PauseListener curListener = (PauseListener) pauseListeners.get(x);
				if(pauseButton.getText().equals("Pause"))
				{
					curListener.pause();
				}
				else
				{
					curListener.resume();
				}
			}
			if(pauseButton.getText().equals("Pause"))
			{
				pauseButton.setText("Resume");
			}
			else
			{
				pauseButton.setText("Pause");
			}
		}
		else if(e.getSource().equals(addButton))
		{
			String curTask = taskField.getText();
			taskField.setText("");
			Connection myConnection = connectionSource.getDatabaseConnection();
			
			try
			{
				while(myConnection == null)
				{
					Thread.sleep(5000);
					myConnection = connectionSource.getDatabaseConnection();
				}
				myConnection.setAutoCommit(false);
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
				
				String taskInsert = "INSERT INTO `dataCollection`.`Task` (`username`, `session`, `event`, `taskName`, `completion`, `startTimestamp`, `adminEmail`) VALUES ";
				String taskRow = "(?, ?, ?, ?, ?, ?, ?)";
				PreparedStatement taskStatement = myConnection.prepareStatement(taskInsert + taskRow);
				taskStatement.setString(1, userName);
				taskStatement.setString(2, session);
				taskStatement.setString(3, eventName);
				taskStatement.setString(4, curTask);
				taskStatement.setString(5, "0");
				taskStatement.setTimestamp(6, curTimestamp);
				taskStatement.setString(7, adminEmail);
				taskStatement.execute();
				
				String taskEventInsert = "INSERT INTO `dataCollection`.`TaskEvent` (`username`, `session`, `event`, `taskName`, `eventDescription`, `startTimestamp`, `adminEmail`) VALUES ";
				String taskEventRow = "(?, ?, ?, ?, ?, ?, ?)";
				PreparedStatement taskEventStatement = myConnection.prepareStatement(taskEventInsert + taskEventRow);
				taskEventStatement.setString(1, userName);
				taskEventStatement.setString(2, session);
				taskEventStatement.setString(3, eventName);
				taskEventStatement.setString(4, curTask);
				taskEventStatement.setString(5, "start");
				taskEventStatement.setTimestamp(6, curTimestamp);
				taskEventStatement.setString(7, adminEmail);
				taskEventStatement.execute();
				
				myConnection.commit();
			}
			catch (Exception e2)
			{
				e2.printStackTrace();
			}
		}
		else
		{
			JButton curButton = (JButton) e.getSource();
			if(endMap.containsKey(curButton))
			{
				HashMap taskAttr = (HashMap) endMap.get(curButton);
				String curTask = (String) taskAttr.get("taskName");
				String curSession = (String) taskAttr.get("session");
				String curUser = (String) taskAttr.get("username");
				String curEvent = (String) taskAttr.get("event");
				Timestamp curTime = (Timestamp) taskAttr.get("startTimestamp");
				
				Connection myConnection = connectionSource.getDatabaseConnection();
				
				try
				{
					while(myConnection == null)
					{
						Thread.sleep(5000);
						myConnection = connectionSource.getDatabaseConnection();
					}
					myConnection.setAutoCommit(false);
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
					
					String taskEventInsert = "INSERT IGNORE INTO `dataCollection`.`TaskEvent` (`username`, `session`, `event`, `taskName`, `eventDescription`, `startTimestamp`, `adminEmail`) VALUES ";
					String taskEventRow = "(?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement taskEventStatement = myConnection.prepareStatement(taskEventInsert + taskEventRow);
					taskEventStatement.setString(1, curUser);
					taskEventStatement.setString(2, curSession);
					taskEventStatement.setString(3, curEvent);
					taskEventStatement.setString(4, curTask);
					taskEventStatement.setString(5, "end");
					taskEventStatement.setTimestamp(6, curTime);
					taskEventStatement.setString(7, adminEmail);
					taskEventStatement.execute();
					
					String taskEventUpdate = "UPDATE `dataCollection`.`Task` SET `completion` = ? WHERE `username` = ? AND `session` = ? AND `event` = ? AND `taskName` = ? AND `startTimestamp` = ? AND `adminEmail` = ?";
					PreparedStatement taskEventUpdateStatement = myConnection.prepareStatement(taskEventUpdate);
					taskEventUpdateStatement.setString(1, "1");
					taskEventUpdateStatement.setString(2, curUser);
					taskEventUpdateStatement.setString(3, curSession);
					taskEventUpdateStatement.setString(4, curEvent);
					taskEventUpdateStatement.setString(5, curTask);
					taskEventUpdateStatement.setTimestamp(6, curTime);
					taskEventUpdateStatement.setString(7, adminEmail);
					System.out.println(taskEventUpdateStatement.toString());
					taskEventUpdateStatement.execute();
					
					myConnection.commit();
				}
				catch (Exception e2)
				{
					e2.printStackTrace();
				}
			}
			else if(pauseMap.containsKey(curButton))
			{
				HashMap taskAttr = (HashMap) pauseMap.get(curButton);
				String curTask = (String) taskAttr.get("taskName");
				String curSession = (String) taskAttr.get("session");
				String curUser = (String) taskAttr.get("username");
				String curEvent = (String) taskAttr.get("event");
				Timestamp curTime = (Timestamp) taskAttr.get("startTimestamp");
				
				Connection myConnection = connectionSource.getDatabaseConnection();
				
				try
				{
					while(myConnection == null)
					{
						Thread.sleep(5000);
						myConnection = connectionSource.getDatabaseConnection();
					}
					myConnection.setAutoCommit(false);
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
					
					String taskEventInsert = "INSERT IGNORE INTO `dataCollection`.`TaskEvent` (`username`, `session`, `event`, `taskName`, `eventDescription`, `startTimestamp`, `adminEmail`) VALUES ";
					String taskEventRow = "(?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement taskEventStatement = myConnection.prepareStatement(taskEventInsert + taskEventRow);
					taskEventStatement.setString(1, curUser);
					taskEventStatement.setString(2, curSession);
					taskEventStatement.setString(3, curEvent);
					taskEventStatement.setString(4, curTask);
					taskEventStatement.setString(5, "pause");
					taskEventStatement.setTimestamp(6, curTime);
					taskEventStatement.setString(7, adminEmail);
					taskEventStatement.execute();
					
					myConnection.commit();
				}
				catch (Exception e2)
				{
					e2.printStackTrace();
				}
			}
			else if(resumeMap.containsKey(curButton))
			{
				HashMap taskAttr = (HashMap) resumeMap.get(curButton);
				String curTask = (String) taskAttr.get("taskName");
				String curSession = (String) taskAttr.get("session");
				String curUser = (String) taskAttr.get("username");
				String curEvent = (String) taskAttr.get("event");
				Timestamp curTime = (Timestamp) taskAttr.get("startTimestamp");
				
				Connection myConnection = connectionSource.getDatabaseConnection();
				
				try
				{
					while(myConnection == null)
					{
						Thread.sleep(5000);
						myConnection = connectionSource.getDatabaseConnection();
					}
					myConnection.setAutoCommit(false);
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
					
					String taskEventInsert = "INSERT IGNORE INTO `dataCollection`.`TaskEvent` (`username`, `session`, `event`, `taskName`, `eventDescription`, `startTimestamp`, `adminEmail`) VALUES ";
					String taskEventRow = "(?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement taskEventStatement = myConnection.prepareStatement(taskEventInsert + taskEventRow);
					taskEventStatement.setString(1, curUser);
					taskEventStatement.setString(2, curSession);
					taskEventStatement.setString(3, curEvent);
					taskEventStatement.setString(4, curTask);
					taskEventStatement.setString(5, "resume");
					taskEventStatement.setTimestamp(6, curTime);
					taskEventStatement.setString(7, adminEmail);
					taskEventStatement.execute();
					
					myConnection.commit();
				}
				catch (Exception e2)
				{
					e2.printStackTrace();
				}
			}
			else if(restartMap.containsKey(curButton))
			{
				HashMap taskAttr = (HashMap) restartMap.get(curButton);
				String curTask = (String) taskAttr.get("taskName");
				String curSession = (String) taskAttr.get("session");
				String curUser = (String) taskAttr.get("username");
				String curEvent = (String) taskAttr.get("event");
				Timestamp curTime = (Timestamp) taskAttr.get("startTimestamp");
				
				Connection myConnection = connectionSource.getDatabaseConnection();
				
				try
				{
					while(myConnection == null)
					{
						Thread.sleep(5000);
						myConnection = connectionSource.getDatabaseConnection();
					}
					myConnection.setAutoCommit(false);
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
					
					String taskEventUpdate = "UPDATE `dataCollection`.`Task` SET `completion` = ? WHERE `username` = ? AND `session` = ? AND `event` = ? AND `taskName` = ? AND `startTimestamp` = ? AND `adminEmail` = ?";
					PreparedStatement taskEventUpdateStatement = myConnection.prepareStatement(taskEventUpdate);
					taskEventUpdateStatement.setString(1, "1");
					taskEventUpdateStatement.setString(2, curUser);
					taskEventUpdateStatement.setString(3, curSession);
					taskEventUpdateStatement.setString(4, curEvent);
					taskEventUpdateStatement.setString(5, curTask);
					taskEventUpdateStatement.setTimestamp(6, curTime);
					taskEventUpdateStatement.setString(7, adminEmail);
					taskEventUpdateStatement.execute();
					
					String taskInsert = "INSERT IGNORE INTO `dataCollection`.`Task` (`username`, `session`, `event`, `taskName`, `completion`, `startTimestamp` , `adminEmail`) VALUES ";
					String taskRow = "(?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement taskStatement = myConnection.prepareStatement(taskInsert + taskRow);
					taskStatement.setString(1, userName);
					taskStatement.setString(2, session);
					taskStatement.setString(3, eventName);
					taskStatement.setString(4, curTask);
					taskStatement.setString(5, "0");
					taskStatement.setTimestamp(6, curTimestamp);
					taskStatement.setString(7, adminEmail);
					taskStatement.execute();
					
					String taskEventInsert = "INSERT IGNORE INTO `dataCollection`.`TaskEvent` (`username`, `session`, `event`, `taskName`, `eventDescription`, `startTimestamp`, `adminEmail`) VALUES ";
					String taskEventRow = "(?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement taskEventStatement = myConnection.prepareStatement(taskEventInsert + taskEventRow);
					taskEventStatement.setString(1, userName);
					taskEventStatement.setString(2, session);
					taskEventStatement.setString(3, eventName);
					taskEventStatement.setString(4, curTask);
					taskEventStatement.setString(5, "restart");
					taskEventStatement.setTimestamp(6, curTimestamp);
					taskEventStatement.setString(7, adminEmail);
					taskEventStatement.execute();
					
					myConnection.commit();
				}
				catch (Exception e2)
				{
					e2.printStackTrace();
				}
			}
		}
		
		refreshView();
	}
}
