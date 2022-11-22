package com.datacollectorlocal;

public interface UploadProgressListener
{
	public void updateProgress(long dataUploaded, long dataLeft, String currentStatus);
	public void updateProgress(long dataUploaded, long dataLeft);
	public void updateStatus(String currentStatus);
}
