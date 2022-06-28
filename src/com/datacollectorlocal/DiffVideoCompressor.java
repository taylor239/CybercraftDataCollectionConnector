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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

public class DiffVideoCompressor implements VideoFrameCompressor
{
	
	private RenderedImage lastImage = null;
	private int keyFrameInterval = 1;
	private int frameCount = 0;
	private int threadCount;
	private boolean running = true;
	private int sleepTime = 20;
	
	private Thread interruptThread = null;
	
	private String imageCompressionType;
	private double imageCompressionFactor;
	
	private ArrayList<ImageSegmentProcessor> threadList = new ArrayList();
	
	private class ImageSegmentProcessor implements Runnable
	{
		private int sleepTime = 20;
		private int sliceNum, totalNum;
		private RenderedImage toDiff = null;
		private BufferedImage toWrite = null;
		public Thread executingThread;
		
		public ImageSegmentProcessor(int mySlice, int myTotal)
		{
			sliceNum = mySlice;
			totalNum = myTotal;
			
			executingThread = new Thread(this);
			executingThread.start();
		}
		
		public boolean getDone()
		{
			return toDiff == null;
		}
		
		/**
		 * Adds a new image to diff to the parent class LastImage.
		 * @param nextImage the image to compare.
		 * @param writeImage the diff image to output to.
		 */
		public void addImage(RenderedImage nextImage, BufferedImage writeImage)
		{
			toWrite = writeImage;
			toDiff = nextImage;
		}

		@Override
		public void run()
		{
			while(running)
			{
				if(toDiff != null)
				{
					//We divide work by having threads process only 1/total threads
					//of the columns.  This is not a perfect solution but should
					//work fine.
					for(int x = sliceNum; x < toDiff.getWidth(); x+= totalNum)
					{
						for(int y = 0; y < toDiff.getHeight(); y++)
						{
							if(((BufferedImage)toDiff).getRGB(x, y) != ((BufferedImage)lastImage).getRGB(x, y))
							{
								//diffFrame.setRGB(x, y, 0);
								toWrite.setRGB(x, y, ((BufferedImage)toDiff).getRGB(x, y));
							}
						}
					}
					
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
					e.printStackTrace();
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
	public DiffVideoCompressor(int keyInterval, int numThreads, String compType, double compFactor)
	{
		imageCompressionType = compType;
		imageCompressionFactor = compFactor;
		keyFrameInterval = keyInterval;
		for(int x = 0; x < numThreads; x++)
		{
			threadList.add(new ImageSegmentProcessor(x, numThreads));
		}
	}
	
	private boolean isReady()
	{
		for(int x = 0; x < threadList.size(); x++)
		{
			if(!threadList.get(x).getDone())
			{
				return false;
			}
		}
		return true;
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
				BufferedImage diffFrame = new BufferedImage(toCompress.getWidth(), toCompress.getHeight(), BufferedImage.TYPE_INT_ARGB);
				
				for(int x = 0; x < threadList.size(); x++)
				{
					threadList.get(x).addImage(toCompress, diffFrame);
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
				myReturn.put("frametype", "diff");
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
