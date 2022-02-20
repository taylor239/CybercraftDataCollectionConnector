package com.datacollectorlocal;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;


public class ProcessMap extends HashMap
{
	private static HashSet ignoreSet = null;
	private static HashMap approxMap = null;
	private static HashSet sizeSet = null;
	
	private static Integer defaultId = null;
	private int id;
	
	public ProcessMap()
	{
		if(defaultId == null)
		{
			Random myRandom = new Random();
			int tmpInt = myRandom.nextInt();
			if(tmpInt == 0)
			{
				tmpInt--;
			}
			else if(tmpInt > 0)
			{
				tmpInt = 0 - tmpInt;
			}
			defaultId = tmpInt;
		}
		
		id = defaultId;
		
		if(ignoreSet == null)
		{
			ignoreSet = new HashSet();
			ignoreSet.add("TIME");
		}
		if(sizeSet == null)
		{
			sizeSet = new HashSet();
			sizeSet.add("THREADS");
		}
		if(approxMap == null)
		{
			approxMap = new HashMap();
			approxMap.put("%CPU", 0.01);
			approxMap.put("MEM", 0.01);
		}
	}
	
	public boolean equals(ProcessMap o)
	{
		//System.out.println("Comparing...");
		if(this.size() != o.size())
		{
			//System.out.println("Diff size");
			return false;
		}
		
		Iterator keyIter = o.keySet().iterator();
		while(keyIter.hasNext())
		{
			Object nextKey = keyIter.next();
			if(!this.containsKey(nextKey))
			{
				//System.out.println("Key mismatch: " + nextKey);
				return false;
			}
			if(ignoreSet.contains(nextKey))
			{
				
			}
			else if(sizeSet.contains(nextKey))
			{
				Collection coll1 = (Collection) this.get(nextKey);
				Collection coll2 = (Collection) o.get(nextKey);
				return coll1.size() == coll2.size();
			}
			else
			{
				if(approxMap.containsKey(nextKey))
				{
					if(! (this.get(nextKey) instanceof Double))
					{
						this.put(nextKey, Double.parseDouble("" + this.get(nextKey)));
					}
					if(! (o.get(nextKey) instanceof Double))
					{
						o.put(nextKey, Double.parseDouble("" + o.get(nextKey)));
					}
					
					double value1 = (double) this.get(nextKey);
					double value2 = (double) o.get(nextKey);
					double allowed = (double) approxMap.get(nextKey);
					double diff = (value2 - value1) / value1;
					if(diff > allowed)
					{
						//System.out.println("Too big difference: " + nextKey + " " + value1 + " : " + value2);
						return false;
					}
				}
				else
				{
					if(!this.get(nextKey).equals(o.get(nextKey)))
					{
						//System.out.println("Diff values: " + nextKey + " " + this.get(nextKey) + " : " + o.get(nextKey));
						return false;
					}
				}
			}
		}
		
		//System.out.println("They are equ!");
		return true;
	}
	
	public boolean equals(Object o)
	{
		if(o != null && o instanceof ProcessMap)
		{
			return equals((ProcessMap) o);
		}
		return super.equals(o);
	}
	
	public int hashCode()
	{
		if(id == defaultId)
		{
			if(this.containsKey("PID"))
			{
				id = Integer.parseInt("" + this.get("PID"));
			}
		}
		
		return id;
	}
	
	public String getKey()
	{
		return id + "_" + this.get("START") + "_" + this.get("USER");
	}
}
