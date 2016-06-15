package com.trivago.hazelcaststresstest;

import java.io.Serializable;

public class CacheKeyType 
{
	private static final long serialVersionUID = -6562474288051525065L;
	
	String key;
	
	CacheKeyType(String key)
	{
		this.key = key;
	}
}
