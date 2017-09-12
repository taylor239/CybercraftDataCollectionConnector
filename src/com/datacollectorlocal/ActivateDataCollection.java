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
		ServletContext context = getServletContext();
		String fullPath = context.getRealPath("/WEB-INF/test/parameters.json");
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
		if(currentAggregator != null)
		{
			currentAggregator.stop();
		}
		String username = request.getParameter("username");
		String serverAddr = request.getParameter("server");
		currentCollector = new Start(username);
		currentAggregator = new DataAggregator(serverAddr);
		response.getWriter().append("Starting data collection for user " + username + " synching at " + serverAddr);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		doGet(request, response);
	}

}
