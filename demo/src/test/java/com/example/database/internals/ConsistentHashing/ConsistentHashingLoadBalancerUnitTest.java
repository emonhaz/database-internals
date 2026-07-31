import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for ConsistentHashingLoadBalancer. */
public class ConsistentHashingLoadBalancerUnitTest {

    private static final int VIRTUAL_NODES = 50;
    private static final int KEY_COUNT = 5000;

    @Test
    public void emptyRingReturnsNull() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        assertNull(lb.getServer("any"));
        assertTrue(lb.getReplicas("any", 2).isEmpty());
    }

    @Test
    public void rejectsInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashingLoadBalancer(0));
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashingLoadBalancer(-1));
    }

    @Test
    public void addIsIdempotentAndRemoveClearsServer() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        int size = lb.ringSize();
        lb.addServer("A");
        assertEquals(size, lb.ringSize());
        assertEquals(VIRTUAL_NODES, lb.ringSize());

        lb.removeServer("A");
        assertEquals(0, lb.ringSize());
        assertFalse(lb.hasServer("A"));
        lb.removeServer("A"); // idempotent
    }

    @Test
    public void getServerIsStableForSameKey() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        lb.addServer("B");
        lb.addServer("C");

        String first = lb.getServer("stable-key");
        assertNotNull(first);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, lb.getServer("stable-key"));
        }
    }

    @Test
    public void removingOneServerRemapsOnlySubsetOfKeys() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        lb.addServer("B");
        lb.addServer("C");

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < KEY_COUNT; i++) {
            keys.add("key-" + i);
        }

        Map<String, String> before = new java.util.HashMap<>();
        for (String key : keys) {
            before.put(key, lb.getServer(key));
        }

        lb.removeServer("B");
        int remapped = 0;
        for (String key : keys) {
            if (!before.get(key).equals(lb.getServer(key))) {
                remapped++;
            }
        }

        double ratio = (double) remapped / keys.size();
        // Ideal ~1/3; allow wide band for small vnode counts.
        assertTrue(ratio > 0.15 && ratio < 0.55,
                "expected roughly ~1/N remaps, got " + ratio);
    }

    @Test
    public void replicasAreDistinctAndPrimaryFirst() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        lb.addServer("B");
        lb.addServer("C");

        List<String> replicas = lb.getReplicas("replica-key", 3);
        assertEquals(3, replicas.size());
        assertEquals(lb.getServer("replica-key"), replicas.get(0));
        assertEquals(3, new HashSet<>(replicas).size());
    }

    @Test
    public void loadDistributionCoversAllKeys() {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        lb.addServer("B");

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            keys.add("u-" + i);
        }
        Map<String, Integer> dist = lb.loadDistribution(keys);
        int sum = dist.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(1000, sum);
        assertTrue(dist.get("A") > 0);
        assertTrue(dist.get("B") > 0);
    }

    @Test
    public void concurrentLookupsWhileTopologyChanges() throws Exception {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(VIRTUAL_NODES);
        lb.addServer("A");
        lb.addServer("B");
        lb.addServer("C");

        int readers = 8;
        int lookupsPerReader = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int r = 0; r < readers; r++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < lookupsPerReader; i++) {
                        String server = lb.getServer("concurrent-" + i);
                        if (server == null && !lb.servers().isEmpty()) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        pool.submit(() -> {
            try {
                start.await();
                lb.removeServer("B");
                lb.addServer("D");
                lb.addServer("B");
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        });

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, failures.get());
        assertNotNull(lb.getServer("after"));
        assertNotEquals(0, lb.ringSize());
    }
}
