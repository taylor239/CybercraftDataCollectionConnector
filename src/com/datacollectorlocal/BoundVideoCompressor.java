package com.datacollectorlocal;

import java.awt.Color;
import java.awt.Image;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

public class BoundVideoCompressor implements VideoFrameCompressor
{
	
	private RenderedImage lastImage = null;
	private int keyFrameInterval = 1;
	private int frameCount = 0;
	private int threadCount;
	private boolean running = true;
	private int sleepTime = 20;
	
	private static HashMap<String, ImageSegmentProcessor> curSideMap = new HashMap<String, ImageSegmentProcessor>();
	
	private Thread interruptThread = null;
	
	private String imageCompressionType;
	private double imageCompressionFactor;
	
	private ArrayList<ImageSegmentProcessor> threadList = new ArrayList();
	
	private class ImageSegmentProcessor implements Runnable
	{
		private int sleepTime = 20;
		private int sliceNum;
		private RenderedImage toDiff = null;
		public Thread executingThread;
		public String side;
		public int value = 0;
		private long curImageNum = 0;
		
		public ImageSegmentProcessor(int mySlice)
		{
			sliceNum = mySlice;
			
			if(sliceNum == 0)
			{
				side = "top";
			}
			else if(sliceNum == 1)
			{
				side = "left";
			}
			else if(sliceNum == 2)
			{
				side = "bottom";
			}
			else if(sliceNum == 3)
			{
				side = "right";
			}
			
			curSideMap.put(side, this);
			
			//executingThread = new Thread(this);
			//executingThread.start();
		}
		
		public String getSide()
		{
			return side;
		}
		
		public boolean getDone()
		{
			return toDiff == null;
		}
		
		public int getValue()
		{
			return value;
		}
		
		public long getImageNum()
		{
			return curImageNum;
		}
		
		/**
		 * Adds a new image to diff to the parent class LastImage.
		 * @param nextImage the image to compare.
		 * @param writeImage the diff image to output to.
		 */
		public void addImage(RenderedImage nextImage)
		{
			if(side.equals("top") || side.equals("left"))
			{
				value = 0;
			}
			else
			{
				if(side.equals("right"))
				{
					value = nextImage.getWidth();
				}
				else //"top"
				{
					value = nextImage.getHeight();
				}
			}
			if(curImageNum == Long.MAX_VALUE)
			{
				curImageNum = 0;
			}
			curImageNum++;
			toDiff = nextImage;
			if(executingThread != null && executingThread.getState() == Thread.State.WAITING || executingThread.getState() == Thread.State.TIMED_WAITING)
			{
				executingThread.interrupt();
			}
		}
		
		private int minX, minY, maxX, maxY;
		
		private void syncBounds()
		{
			maxX = toDiff.getWidth();
			minX = 0;
			maxY = toDiff.getHeight();
			minY = 0;
			if(side.equals("left"))
			{
				minX = value;
			}
			else if(curSideMap.containsKey("left") && curImageNum == curSideMap.get("left").getImageNum())
			{
				minX = curSideMap.get("left").getValue();
			}
			if(side.equals("right"))
			{
				maxX = value;
			}
			else if(curSideMap.containsKey("right") && curImageNum == curSideMap.get("right").getImageNum())
			{
				maxX = curSideMap.get("right").getValue();
			}
			if(side.equals("top"))
			{
				minY = value;
			}
			else if(curSideMap.containsKey("top") && curImageNum == curSideMap.get("top").getImageNum())
			{
				minY = curSideMap.get("top").getValue();
			}
			if(side.equals("bottom"))
			{
				maxY = value;
			}
			else if(curSideMap.containsKey("bottom") && curImageNum == curSideMap.get("bottom").getImageNum())
			{
				maxY = curSideMap.get("bottom").getValue();
			}
		}
		
		public void setValue(int toSet)
		{
			value = toSet;
		}

		@Override
		public void run()
		{
			while(running)
			{
				if(toDiff != null)
				{
					boolean unfound = true;
					syncBounds();
					if(side.equals("top"))
					{
						for(int y = minY; y < maxY && unfound; y++)
						{
							value = y;
							for(int x = minX; x < maxX && unfound; x++)
							{
								syncBounds();
								if(x < minX)
								{
									x = minX;
								}
								if(x > maxX)
								{
									break;
								}
								if(y > maxY)
								{
									value = maxY;
									unfound = false;
								}
								if(((BufferedImage)toDiff).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
								{
									unfound = false;
								}
							}
						}
					}
					else if(side.equals("bottom"))
					{
						for(int y = maxY - 1; y >= minY && unfound; y--)
						{
							value = y;
							for(int x = maxX - 1; x >= minX && unfound; x--)
							{
								syncBounds();
								if(x < minX)
								{
									break;
								}
								if(x > maxX)
								{
									x = maxX;
								}
								if(y < minY)
								{
									value = minY;
									unfound = false;
								}
								if(((BufferedImage)toDiff).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
								{
									unfound = false;
								}
							}
						}
					}
					else if(side.equals("right"))
					{
						for(int x = maxX - 1; x >= minX && unfound; x--)
						{
							value = x;
							for(int y = minY; y < maxY && unfound; y++)
							{
								syncBounds();
								if(y < minY)
								{
									y = minY;
								}
								if(y > maxY)
								{
									break;
								}
								if(x < minX)
								{
									value = minX;
									unfound = false;
								}
								if(((BufferedImage)toDiff).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
								{
									unfound = false;
								}
							}
						}
					}
					else if(side.equals("left"))
					{
						for(int x = minX; x < maxX && unfound; x++)
						{
							value = x;
							for(int y = maxY - 1; y >= minY && unfound; y--)
							{
								syncBounds();
								if(y < minY)
								{
									break;
								}
								if(y > maxY)
								{
									y = maxY;
								}
								if(x > maxX)
								{
									value = maxX;
									unfound = false;
								}
								if(((BufferedImage)toDiff).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
								{
									unfound = false;
								}
							}
						}
					}
					
					//System.out.println("Finished " + side + ": " + value);
					
					toDiff = null;
					
					if(interruptThread != null && interruptThread.getState() == Thread.State.WAITING || interruptThread.getState() == Thread.State.TIMED_WAITING)
					{
						interruptThread.interrupt();
					}
				}
				try
				{
					Thread.currentThread().sleep(sleepTime);
				}
				catch(InterruptedException e)
				{
					//e.printStackTrace();
				}
			}
		}
		
	}
	
	/**
	 * A class which compresses sequential images by diff-ing
	 * the next image with the previous one.  Any different
	 * pixels are set, and the rest are transparent.  Then
	 * the final image is compressed.  This method uses PNG
	 * encoding only currently, since lossy compression
	 * techniques can cause issues with pixel matching.
	 * @param keyInterval
	 */
	public BoundVideoCompressor(int keyInterval, String compType, double compFactor)
	{
		imageCompressionType = compType;
		imageCompressionFactor = compFactor;
		keyFrameInterval = keyInterval;
		threadCount = 4;
		for(int x = 0; x < threadCount; x++)
		{
			threadList.add(new ImageSegmentProcessor(x));
		}
		for(int x = 0; x < threadCount; x++)
		{
			Thread tmpThread = new Thread(threadList.get(x));
			threadList.get(x).executingThread = tmpThread;
			tmpThread.start();
		}
	}
	
	private boolean isReady()
	{
		return
				curSideMap.get("left").getDone()
				&&
				curSideMap.get("right").getDone()
				&&
				curSideMap.get("top").getDone()
				&&
				curSideMap.get("bottom").getDone();
		//return true;
	}
	
	private BufferedImage deepCopy(BufferedImage bi)
	{
		//System.out.println("Color model: " + bi.getColorModel());
		ColorModel cm = bi.getColorModel();
		//ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[]{8,8,8,8}, true, false, ColorModel.TRANSLUCENT, DataBuffer.TYPE_BYTE);
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	@Override
	public synchronized HashMap compressNextFrame(RenderedImage toCompress)
	{
		interruptThread = Thread.currentThread();
		frameCount++;
		HashMap myReturn = new HashMap();
		
		myReturn.put("x", 0);
		myReturn.put("y", 0);
		
		try
		{
			ByteArrayOutputStream toByte = new ByteArrayOutputStream();
			ImageOutputStream imageOutput = ImageIO.createImageOutputStream(toByte);
			ImageWriter myWriter = ImageIO.getImageWritersByFormatName(imageCompressionType).next();
			myWriter.setOutput(imageOutput);
			
			if
			(
					lastImage == null
					|| toCompress.getWidth() != lastImage.getWidth()
					|| toCompress.getHeight() != lastImage.getHeight()
					|| frameCount > keyFrameInterval
			)
			{
				frameCount = 0;
				
				/* Old png-only version
				ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
				if(pngParam.canWriteCompressed())
				{
					pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					pngParam.setCompressionQuality(0.0f);
				}
				myWriter.write(null, new IIOImage((RenderedImage) toCompress, null, null), pngParam);
				*/
				
				if(imageCompressionType.equals("jpg"))
				{
					JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
					jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					jpegParams.setCompressionQuality((float) imageCompressionFactor);
					myWriter.write(null, new IIOImage((RenderedImage) toCompress, null, null), jpegParams);
				}
				else if(imageCompressionType.equals("png"))
				{
					ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
					if(pngParam.canWriteCompressed())
					{
						pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						pngParam.setCompressionQuality((float) imageCompressionFactor);
					}
					myWriter.write(null, new IIOImage((RenderedImage) toCompress, null, null), pngParam);
				}
				
				myReturn.put("bytes", toByte.toByteArray());
				myReturn.put("frametype", "key");
			}
			else
			{
				//BufferedImage diffFrame = deepCopy((BufferedImage) toCompress);
				BufferedImage diffFrame = null;//new BufferedImage(toCompress.getWidth(), toCompress.getHeight(), BufferedImage.TYPE_INT_ARGB);
				
				for(int x = 0; x < threadList.size(); x++)
				{
					threadList.get(x).addImage(toCompress);
				}
				while(!isReady())
				{
					try
					{
						Thread.currentThread().sleep(sleepTime);
					}
					catch(Exception e)
					{
						
					}
				}
				
				int minX = curSideMap.get("left").getValue();
				int maxX = curSideMap.get("right").getValue();
				int minY = curSideMap.get("top").getValue();
				int maxY = curSideMap.get("bottom").getValue();
				
				myReturn.put("x", minX);
				myReturn.put("y", minY);
				
				if(minX != maxX && minY != maxY)
				{
					diffFrame = ((BufferedImage)toCompress).getSubimage(minX, minY, maxX - minX, maxY - minY);
					
					/* The single threaded way...
					for(int x = 0; x < toCompress.getWidth(); x++)
					{
						for(int y = 0; y < toCompress.getHeight(); y++)
						{
							if(((BufferedImage)toCompress).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
							{
								//diffFrame.setRGB(x, y, 0);
								diffFrame.setRGB(x, y, ((BufferedImage)toCompress).getRGB(x, y));
							}
						}
					}
					*/
					
					/*
					ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
					if(pngParam.canWriteCompressed())
					{
						pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						pngParam.setCompressionQuality(0.0f);
					}
					myWriter.write(null, new IIOImage(diffFrame, null, null), pngParam);
					*/
					
					if(imageCompressionType.equals("jpg"))
					{
						JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
						jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						jpegParams.setCompressionQuality((float) imageCompressionFactor);
						myWriter.write(null, new IIOImage((RenderedImage) diffFrame, null, null), jpegParams);
					}
					else if(imageCompressionType.equals("png"))
					{
						ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
						if(pngParam.canWriteCompressed())
						{
							pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
							pngParam.setCompressionQuality((float) imageCompressionFactor);
						}
						myWriter.write(null, new IIOImage((RenderedImage) diffFrame, null, null), pngParam);
					}
					myReturn.put("bytes", toByte.toByteArray());
					myReturn.put("frametype", "seg");
				}
				else
				{
					//System.out.println("Empty frame, ignoring.");
					myReturn.put("bytes", null);
					myReturn.put("frametype", null);
				}
			}
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		lastImage = toCompress;
		return myReturn;
	}

}
