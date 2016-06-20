package com.trivago.hazelcaststresstest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastOverloadException;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import com.hazelcast.nio.serialization.HazelcastSerializationException;

/**
 * Stress-testing Hazelcast client. Uses client-server mode.
 * 
 * Test instructions:
 * - Set "addresses" to your Hazelcast server nodes. We use 2 nodes.
 * - Adjust entryCount. The higher the number, the faster you get OOM issues
 * 
 * If running as-is, Hazelcast hangs on to 2 million ClientMessage objects with overall size 3GB.
 * Many major collections happen. Result: The application dies with an OOM issue. Or - if you are lucky - 
 * the HazelcastInstance dies (e.g. heartbeat miss due to OOM) and the memory held by it is freed by GC.
 * 
 * Making the client die slower:
 * - entryCount = 10_000;
 * - entryCount = 1_000;
 * 
 * Different possibility for OOM:
 * - Remove "implements Serializable" from CacheKeyType. OOM due to allocating many HazelcastSerializationException's
 * 
 * @author christian.esken@trivago.com
 *
 *
 * START WITH: -XX:+UseConcMarkSweepGC
 *
 */
public class HazelcastStresstest
{
    int sleepAtStart = 10_000; // In ms. Sleep a bit to have time to attach tools
    
    // Hazelcast config
	String addresses[]  = { "127.0.0.1:5701",  "127.0.0.2:5701"}; // Change this
	String cacheName = "blcacheinmem";
    String groupConfigName = "dev";
    String groupConfigPass = "dev";
    int executorPoolSize = 10;

    // Cache entries: Number of entries is entryCount * multiplier
	int entryCount = 100000; // number of unique values
    int multiplier = 10_000;
    
    // Executor
    int fixedThreadPoolCount = 10;
    int executorBlockingQueueSize = 4096;
    
    private final IMap<CacheKeyType, CacheValueType> map;
	final List<CacheValueType> cacheEntries;


	public static void main(String[] args) throws Exception
	{
		HazelcastStresstest hc = new HazelcastStresstest(args);
		hc.putBenchmark();
	}

	public HazelcastStresstest(String[] args) throws Exception
	{
		System.out.println("Sleeping " + sleepAtStart + "ms");
        Thread.sleep(sleepAtStart); 
		System.out.println("Starting test");

		System.out.println("Init Cache");
		HazelcastInstance client = initCache(cacheName, addresses, groupConfigName, groupConfigPass, executorPoolSize);
        map = client.getMap(cacheName);
     
		System.out.println("Init Test Data	");
		// Generate test data upfront, so that we can later write as fast as possible without losing time
        cacheEntries = getCacheEntries(entryCount);
        
	}

    // ---------------------------------------------------------------------------------
    // Below: The put/putAsync test 
    // ---------------------------------------------------------------------------------

	public void putBenchmark() throws Exception
    {
		System.out.println("Starting test");

		BlockingQueue<Runnable> executorQueue = new ArrayBlockingQueue<>(executorBlockingQueueSize);
        ExecutorService executorService = new ThreadPoolExecutor(fixedThreadPoolCount, fixedThreadPoolCount,
                0L, TimeUnit.MILLISECONDS, executorQueue, new ThreadPoolExecutor.CallerRunsPolicy());

        ExecutorCompletionService<Boolean> completionService = new ExecutorCompletionService<Boolean>(executorService);

        AtomicBoolean shutdown = new AtomicBoolean(false);
        new Thread(() -> {
            while(!shutdown.get()) {
                try {
                    // CompletionService results need to be collected!
                    completionService.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Thread.yield();
            }
        }).start();

        // 2 counter that count what we submitted to the completionService and was what actual put
        AtomicLong submittedItemCount = new AtomicLong();
        AtomicLong putItemCount = new AtomicLong();
        for (int factor = 0; factor < multiplier; factor++)
        {
        	System.out.print(".");
        	if (factor % 100 == 0)
        		System.out.println();
            populateOnce(completionService, submittedItemCount, putItemCount, factor);
        }
        shutdown.set(true);
    }

    private void populateOnce(ExecutorCompletionService<Boolean> completionService, AtomicLong submittedItemCount,
                              AtomicLong putItemCount, int prefix) throws Exception
    {
        cacheEntries.stream()
                .forEach(value -> {
                	submittedItemCount.incrementAndGet();
                    CacheKeyType key = new CacheKeyType(prefix + "-" + value.key());
                    completionService.submit(() -> {
                        retry:
                        try {
                            map.putAsync(key, value, 0L, TimeUnit.MILLISECONDS);
                        } catch (HazelcastOverloadException e) {
                            Thread.sleep(100);
                            break retry;
                        }

                        putItemCount.incrementAndGet();
                        return true;
                    });
                });
    }
    
    
    // ---------------------------------------------------------------------------------
    // Below: Initialization of Cache and Test data 
    // ---------------------------------------------------------------------------------

    private List<CacheValueType> getCacheEntries(int count)
	{
    	Random rand = new Random(42);
    	
    	List<CacheValueType> values = new ArrayList<CacheValueType>(count);
    	for (int i=0; i<count; i++)
    	{
        	byte[] value = new byte[300];
        	rand.nextBytes(value);
        	values.add(new CacheValueType(value));
    	}
    		
    	return values;
	}

	HazelcastInstance initCache(String mapName, String[] addresses, String groupConfigName2, String pass, int executorPoolSize)
	{
        ClientConfig config = new ClientConfig();
        config.setProperty("hazelcast.client.max.concurrent.invocations", "10000");
        config.setProperty("hazelcast.backpressure.enabled", "true");

        //config.getGroupConfig().setName(groupConfigName2).setPassword(pass);
        config.getNetworkConfig().addAddress(addresses);
        config.setExecutorPoolSize(executorPoolSize);

        return HazelcastClient.newHazelcastClient(config);
	}

}
