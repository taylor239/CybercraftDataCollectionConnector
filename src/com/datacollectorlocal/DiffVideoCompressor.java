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
import java.util.HashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class DiffVideoCompressor implements VideoFrameCompressor
{
	
	private RenderedImage lastImage = null;
	private int keyFrameInterval = 1;
	private int frameCount = 0;
	
	/**
	 * A class which compresses sequential images by diff-ing
	 * the next image with the previous one.  Any different
	 * pixels are set, and the rest are transparent.  Then
	 * the final image is compressed.  This method uses PNG
	 * encoding only currently, since lossy comrpession
	 * techniques can cause issues with pixel matching.
	 * @param keyInterval
	 */
	public DiffVideoCompressor(int keyInterval)
	{
		keyFrameInterval = keyInterval;
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
		frameCount++;
		HashMap myReturn = new HashMap();
		
		myReturn.put("x", 0);
		myReturn.put("y", 0);
		
		try
		{
			ByteArrayOutputStream toByte = new ByteArrayOutputStream();
			ImageOutputStream imageOutput = ImageIO.createImageOutputStream(toByte);
			ImageWriter myWriter = ImageIO.getImageWritersByFormatName("png").next();
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
				
				ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
				if(pngParam.canWriteCompressed())
				{
					pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					pngParam.setCompressionQuality(0.0f);
				}
				myWriter.write(null, new IIOImage((RenderedImage) toCompress, null, null), pngParam);
				
				myReturn.put("bytes", toByte.toByteArray());
				myReturn.put("frametype", "key");
			}
			else
			{
				//BufferedImage diffFrame = deepCopy((BufferedImage) toCompress);
				BufferedImage diffFrame = new BufferedImage(toCompress.getWidth(), toCompress.getHeight(), BufferedImage.TYPE_INT_ARGB);
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
				
				ImageWriteParam pngParam = myWriter.getDefaultWriteParam();
				if(pngParam.canWriteCompressed())
				{
					pngParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					pngParam.setCompressionQuality(0.0f);
				}
				myWriter.write(null, new IIOImage(diffFrame, null, null), pngParam);
				
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
