package com.datacollectorlocal;

public interface MetricListener
{
	public void recordMetric(String metricName, double metricValue, String metricUnit);
	public void recordMetric(String metricName, double metricValue1, String metricUnit1, double metricValue2, String metricUnit2);
}
