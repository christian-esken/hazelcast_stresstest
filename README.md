# Stress testing Hazelcast

Author: Christian Esken , trivago


Test instructions:
 - Set "addresses" in the HazelcaqstStresstest class to your Hazelcast server nodes. We use 2 nodes.
 - Run application and check results
 
Making the client die slower:
 - entryCount = 10_000;
 - entryCount = 1_000;

Different possibility for OOM:
- Remove "implements Serializable" from CacheKeyType. OOM due to allocating many HazelcastSerializationException's
