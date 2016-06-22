package com.trivago.hazelcaststresstest;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;

/**
 * START WITH: -XX:+UseConcMarkSweepGC
 */
public class HazelcastNode {

    public static void main(String[] args) {
        Config config = new XmlConfigBuilder().build();
        config.setProperty("hazelcast.backpressure.enabled", "true");

        MapConfig mapConfig = config.getMapConfig("blcacheinmem");
        mapConfig.getMaxSizeConfig().setMaxSizePolicy(MaxSizeConfig.MaxSizePolicy.FREE_HEAP_PERCENTAGE).setSize(10);
        mapConfig.setEvictionPolicy(EvictionPolicy.LRU);

        Hazelcast.newHazelcastInstance(config);
    }

}
