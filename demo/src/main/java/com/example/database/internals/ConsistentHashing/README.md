# Consistent hashing load balancer

Maps request keys to servers with a virtual-node hash ring so adding/removing a
server remaps only about `1/N` of keys (not the full set).

## Features

- Virtual nodes per server for smoother load balance
- Thread-safe lookups (`ConcurrentSkipListMap`) and locked topology changes
- Exact server removal via tracked vnode hashes (no silent ring corruption)
- Collision probing so vnode hashes cannot overwrite each other
- `getReplicas(key, n)` for multi-server placement / failover
- `loadDistribution(keys)` to inspect balance

## Run

From `demo/` (after compile):

```bash
javac -d target/classes \
  src/main/java/com/example/database/internals/ConsistentHashing/*.java
java -cp target/classes ConsistentHashingDemo
```

Tests:

```bash
# with JUnit on the classpath, or:
# mvn test -Dtest=ConsistentHashingLoadBalancerUnitTest
```

## API sketch

```java
ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(100);
lb.addServer("ServerA");
lb.addServer("ServerB");

String primary = lb.getServer("user-42");
List<String> replicas = lb.getReplicas("user-42", 2);

lb.removeServer("ServerB");
```

## Notes

- More virtual nodes ⇒ better balance, more memory / slower topology updates.
- Default hash is MD5 truncated to 64 bits (demo-quality); production systems often use Murmur3 / xxHash.
- Duplicate under `demo/src/ConsistentHashing/` is legacy; prefer this package.
