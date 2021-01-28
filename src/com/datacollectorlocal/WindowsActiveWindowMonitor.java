package com.datacollectorlocal;

import java.util.HashMap;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public class WindowsActiveWindowMonitor implements ActiveWindowMonitor
{
	
	private PortableProcessMonitor myProcMonitor;
	
	public WindowsActiveWindowMonitor()
	{
		myProcMonitor = new PortableProcessMonitor();
	}
	
    interface User32ForClientRect extends StdCallLibrary
    {
        User32ForClientRect INSTANCE = (User32ForClientRect) Native.loadLibrary("user32", User32ForClientRect.class,
                W32APIOptions.DEFAULT_OPTIONS);
        WinDef.HWND FindWindow(String lpClassName, String lpWindowName);
        boolean GetClientRect(WinDef.HWND hWnd, WinDef.RECT rect);
        boolean ClientToScreen(WinDef.HWND hWnd, WinDef.POINT lpPoint);
    }
	
	public HashMap getTopWindow(long diff)
	{
		HashMap myReturn = new HashMap();
		
		
		HWND fgWindow = User32.INSTANCE.GetForegroundWindow();
		int titleLength = User32.INSTANCE.GetWindowTextLength(fgWindow) + 1;
		char[] title = new char[titleLength];
		User32.INSTANCE.GetWindowText(fgWindow, title, titleLength);
		String titleString = new String(title);
		
		int pid = User32.INSTANCE.GetWindowThreadProcessId(fgWindow, null);
		HashMap processInfo = myProcMonitor.getProcessInfo(pid);
		
		WinDef.POINT getPos = new WinDef.POINT();
		WinDef.RECT rect = new WinDef.RECT();
		User32ForClientRect.INSTANCE.GetClientRect(fgWindow, rect);
		User32ForClientRect.INSTANCE.ClientToScreen(fgWindow, getPos);
		
		int x = getPos.x;
		int y = getPos.y;
		int width = rect.right;
		int height = rect.bottom;
		
		return myReturn;
	}
}
