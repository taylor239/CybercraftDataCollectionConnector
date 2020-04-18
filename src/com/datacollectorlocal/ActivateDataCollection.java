package com.datacollectorlocal;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.datacollectorlocal.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Servlet implementation class ActivateDataCollection
 */
@WebServlet("/ActivateDataCollection")
public class ActivateDataCollection extends HttpServlet
{
	static Start currentCollector = null;
	static DataAggregator currentAggregator = null;
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public ActivateDataCollection()
    {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		System.out.println("Activating stuff");
		ServletContext context = getServletContext();
		String fullPath = context.getRealPath("/WEB-INF/config/parameters.json");
		String configJson = new String(Files.readAllBytes(Paths.get(fullPath)), "UTF-8");
		Gson gson = new GsonBuilder().create();
		HashMap config = gson.fromJson(configJson, HashMap.class);
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		if(currentCollector != null)
		{
			currentCollector.stop();
		}
		//if(currentAggregator != null)
		{
		//	currentAggregator.stop();
		}
		String username = request.getParameter("username");
		String token = request.getParameter("token");
		String serverAddr = request.getParameter("server");
		String event = request.getParameter("event");
		String redirAddr = request.getParameter("redirect");
		if(redirAddr == null || redirAddr.equals(""))
		{
			redirAddr = "http://revenge.cs.arizona.edu/RevEngE/monitorUpload.jsp";
		}
		//serverAddr = "http://revenge.cs.arizona.edu:80/CatalystDataCollection/UploadData";
		System.out.println("Got user: " + username + " with token " + token);
		System.out.println("Sending to servr " + serverAddr);
		//currentCollector = new Start(username);
		currentAggregator = DataAggregator.getInstance(serverAddr, username, token, false, event);
		response.getWriter().append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
		response.getWriter().append("<html>\n<head>\n</head>\n<body>\n");
		response.getWriter().append("\nStarting data collection for user " + username + ":" + token + " syncing at " + serverAddr + "\n");
		//response.getWriter().append("<meta http-equiv=\"refresh\" content=\"0; url=" + request.getParameter("redirect") + "\">\n</body>\n</html>");
		response.getWriter().append("<meta http-equiv=\"refresh\" content=\"0; url=" + redirAddr + "?token=" + token + "\">\n</body>\n</html>");
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		doGet(request, response);
	}

}
