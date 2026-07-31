import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
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

/** Unit tests for sharding (in-memory backends only). */
public class ShardingUnitTest {

    @Test
    public void routingIsDeterministicAndStable() {
        try (ShardManager manager = ShardManager.inMemory(4)) {
            int first = manager.shardIndexFor("user-abc");
            for (int i = 0; i < 20; i++) {
                assertEquals(first, manager.shardIndexFor("user-abc"));
            }
            assertTrue(first >= 0 && first < 4);
        }
    }

    @Test
    public void createAndGetUserRoundTrip() {
        try (ShardManager manager = ShardManager.inMemory(3)) {
            UserApi api = new UserApi(new UserService(manager));
            Map<String, Object> created = api.handleCreateUser("u1", "Alice");
            assertEquals("created", created.get("status"));
            Map<String, Object> got = api.handleGetUser("u1");
            assertEquals("Alice", got.get("name"));
            assertEquals(true, got.get("found"));
            assertEquals(created.get("shard"), got.get("shard"));
        }
    }

    @Test
    public void usersSpreadAcrossShards() {
        try (ShardManager manager = ShardManager.inMemory(3)) {
            UserService users = new UserService(manager);
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                String id = "user-" + i;
                users.createUser(id, "n" + i);
                used.add(users.shardFor(id));
            }
            assertEquals(3, used.size());
        }
    }

    @Test
    public void unhealthyShardFailsClosedWithoutCrossShardFailover() {
        List<ShardStore> stores = new ArrayList<>();
        InMemoryShardStore s0 = new InMemoryShardStore(0);
        InMemoryShardStore s1 = new InMemoryShardStore(1);
        stores.add(s0);
        stores.add(s1);
        try (ShardRouter router = new ShardRouter(stores, 0)) {
            UserRepository repo = new UserRepository(router);
            // Find a user that maps to shard 0.
            String userOn0 = null;
            for (int i = 0; i < 100; i++) {
                String id = "probe-" + i;
                if (router.shardIndexFor(id) == 0) {
                    userOn0 = id;
                    break;
                }
            }
            assertTrue(userOn0 != null);
            repo.createUser(userOn0, "A");
            s0.setHealthy(false);
            router.refreshHealth();
            String target = userOn0;
            assertThrows(ShardUnavailableException.class, () -> repo.getUser(target));
            // Shard 1 still works for its own keys.
            String userOn1 = null;
            for (int i = 0; i < 100; i++) {
                String id = "other-" + i;
                if (router.shardIndexFor(id) == 1) {
                    userOn1 = id;
                    break;
                }
            }
            repo.createUser(userOn1, "B");
            assertEquals("B", repo.getUser(userOn1));
        }
    }

    @Test
    public void heartbeatOnlineWindow() throws Exception {
        try (ShardManager manager = ShardManager.inMemory(2)) {
            HeartbeatService hb = new HeartbeatService(manager.router(), 1);
            hb.updateHeartbeat("u1");
            assertTrue(hb.isOnline("u1"));
            assertFalse(hb.isOnline("u2"));
            Thread.sleep(1100);
            assertFalse(hb.isOnline("u1"));
        }
    }

    @Test
    public void concurrentCreatesAreVisible() throws Exception {
        try (ShardManager manager = ShardManager.inMemory(4)) {
            UserService users = new UserService(manager);
            int n = 50;
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            AtomicInteger failures = new AtomicInteger();

            for (int i = 0; i < n; i++) {
                final int id = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        users.createUser("c-" + id, "name-" + id);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(15, TimeUnit.SECONDS));
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(0, failures.get());

            Map<String, String> missing = new HashMap<>();
            for (int i = 0; i < n; i++) {
                String got = users.getUser("c-" + i);
                if (!("name-" + i).equals(got)) {
                    missing.put("c-" + i, got);
                }
            }
            assertTrue(missing.isEmpty(), "missing=" + missing);
        }
    }
}
