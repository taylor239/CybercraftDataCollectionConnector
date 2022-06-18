package com.datacollectorlocal;

import java.awt.Image;
import java.awt.image.RenderedImage;
import java.util.HashMap;

public interface VideoFrameCompressor
{
	public HashMap compressNextFrame(RenderedImage toCompress);
}
