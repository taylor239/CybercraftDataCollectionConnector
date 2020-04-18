package com.datacollectorlocal;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Date;

public interface ScreenshotListener
{
	public void getScreenshotEvent(Date timeTaken, Image screenshot);
	public ArrayList getInvisibleComponents();
}
