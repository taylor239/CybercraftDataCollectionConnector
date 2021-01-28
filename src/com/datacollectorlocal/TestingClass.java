package com.datacollectorlocal;

import java.lang.ProcessHandle.Info;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;


@Platform(include="winuser.h")

public class TestingClass
{
	public static void main(String[] args)
	{
		
	}
	public static void oldMain(String[] args)
	{
		System.out.println("Starting test");
		System.out.println(System.getProperty("os.name"));
		//System.getProperties().list(System.out);
		
		Stream<ProcessHandle> allProcesses = ProcessHandle.allProcesses();
		allProcesses.forEach(processHandle ->
		{
			System.out.println(processHandle.pid());
			Info info = processHandle.info();
			
			String command = "";
			if(info.command().isPresent())
			{
				command = info.command().get();
				System.out.println(command);
			}
			
			String[] arguments = null;
			if(info.arguments().isPresent())
			{
				arguments = info.arguments().get();
				System.out.println(arguments);
			}
			
			String user = "";
			if(info.user().isPresent())
			{
				user = info.user().get();
				System.out.println(user);
			}
			
			Instant now = Instant.now();
			Instant startInstant = null;
			long diff = 0;
			if(info.startInstant().isPresent())
			{
				startInstant = info.startInstant().get();
				System.out.println(startInstant.toEpochMilli());
				diff = now.toEpochMilli() - startInstant.toEpochMilli();
			}
			
			Duration totalCpuDuration = null;
			double cpuUse = 0;
			if(info.totalCpuDuration().isPresent())
			{
				totalCpuDuration = info.totalCpuDuration().get();
				System.out.println(totalCpuDuration.toMillis());
				System.out.println("CPU Use %:");
				cpuUse = (100 * ((double)totalCpuDuration.toMillis() / (double)diff));
				System.out.println(cpuUse);
			}
			
		});
	}
}
