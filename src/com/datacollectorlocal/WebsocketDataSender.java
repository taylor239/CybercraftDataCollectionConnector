package com.datacollectorlocal;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WebsocketDataSender extends WebSocketClient
{
	private boolean hasResponse = false;
	private String response = "";
	private long timeout = 50000000;
	private long poll = 1000;
	//private long maxFrameSize = 60000;
	
	public static void main(String[] args)
	{
		try
		{
			//WebsocketDataSender mySender = new WebsocketDataSender(new URI("ws://localhost:8080/DataCollectorServer/UploadDataWebsocket"), "Hello World");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public WebsocketDataSender(URI serverUri)
	{
		super(serverUri);
		try
		{
			System.out.println("Connecting to websocket at " + serverUri);
			connectBlocking();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public String sendWait(String toSend)
	{
		System.out.println("Sending message");
		System.out.println(toSend.length());
		hasResponse = false;
		response = null;
		String myReturn = "";
		send(toSend);
		long curTime = 0;
		while(!hasResponse)
		{
			try {
				Thread.currentThread().sleep(poll);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			curTime += poll;
			System.out.println("Currently at " + curTime);
			System.out.println(isOpen());
			if(curTime > timeout || !isOpen())
			{
				System.out.println("Timeout: " + timeout);
				break;
			}
		}
		System.out.println("Response in " + curTime);
		myReturn = response;
		return myReturn;
	}

	@Override
	public void onClose(int arg0, String arg1, boolean arg2) {
		// TODO Auto-generated method stub
		System.out.println("Closed");
	}

	@Override
	public void onError(Exception arg0) {
		// TODO Auto-generated method stub
		arg0.printStackTrace();
	}

	@Override
	public void onMessage(String arg0) {
		// TODO Auto-generated method stub
		hasResponse = true;
		response = arg0;
		System.out.println("Client got " + arg0);
		//close();
	}

	@Override
	public void onOpen(ServerHandshake arg0) {
		// TODO Auto-generated method stub
		
	}
	
}
