package com.trivago.hazelcaststresstest;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cache entry (key,value), that is mainly used as cache value, but also knows its key.  
 * @author cesken
 *
 */
public class CacheValueType implements Serializable
{
	private static final long serialVersionUID = 3475485160578620212L;

	final byte[] value;
	final int id;
	
	static AtomicInteger idGenerator = new AtomicInteger();

	CacheValueType(byte[] value)
	{
		this.value = value;
		this.id = idGenerator.incrementAndGet();
	}

	public int key()
	{
		return id;
	}
}
