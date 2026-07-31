import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for ConnectionPool. */
public class ConnectionPoolUnitTest {

    private static final int SHORT_WAIT_MS = 200;
    private static final int JOIN_TIMEOUT_MS = 2000;

    @Test
    public void acquireAndReleaseRoundTrip() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(1)) {
            Connection conn = pool.acquire();
            assertNotNull(conn);
            assertEquals(0, pool.availableConnections());
            assertEquals(1, pool.activeConnections());

            pool.release(conn);
            assertEquals(1, pool.availableConnections());
            assertEquals(0, pool.activeConnections());
        }
    }

    @Test
    public void acquireBlocksWhenEmptyUntilRelease() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(1)) {
            Connection held = pool.acquire();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicInteger acquiredId = new AtomicInteger(-1);

            Thread waiter = new Thread(() -> {
                try {
                    started.countDown();
                    Connection conn = pool.acquire();
                    acquiredId.set(conn.getId());
                    pool.release(conn);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            waiter.start();
            assertTrue(started.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            Thread.sleep(SHORT_WAIT_MS);
            assertTrue(waiter.isAlive());

            pool.release(held);
            assertTrue(finished.await(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(acquiredId.get() > 0);
        }
    }

    @Test
    public void timedAcquireReturnsNullOnTimeout() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(1)) {
            Connection held = pool.acquire();
            long before = pool.timeoutCount();
            assertNull(pool.acquire(50, TimeUnit.MILLISECONDS));
            assertEquals(before + 1, pool.timeoutCount());
            pool.release(held);
        }
    }

    @Test
    public void doubleReleaseFailsFast() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(1)) {
            Connection conn = pool.acquire();
            pool.release(conn);
            assertThrows(IllegalStateException.class, () -> pool.release(conn));
            assertEquals(1, pool.availableConnections());
        }
    }

    @Test
    public void foreignConnectionRejected() throws Exception {
        try (ConnectionPool poolA = new ConnectionPool(1);
             ConnectionPool poolB = new ConnectionPool(1)) {
            Connection fromB = poolB.acquire();
            assertThrows(IllegalArgumentException.class, () -> poolA.release(fromB));
            poolB.release(fromB);
        }
    }

    @Test
    public void lazyPoolGrowsUnderLoadUpToMax() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(0, 3)) {
            assertEquals(0, pool.createdConnections());
            List<Connection> held = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                held.add(pool.acquire());
            }
            assertEquals(3, pool.createdConnections());
            assertEquals(3, pool.activeConnections());
            assertNull(pool.acquire(20, TimeUnit.MILLISECONDS));
            for (Connection conn : held) {
                pool.release(conn);
            }
            assertEquals(3, pool.availableConnections());
        }
    }

    @Test
    public void leaseReturnsConnectionViaTryWithResources() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(1)) {
            try (ConnectionPool.Lease lease = pool.acquireLease()) {
                assertNotNull(lease.get());
                assertEquals(0, pool.availableConnections());
            }
            assertEquals(1, pool.availableConnections());
        }
    }

    @Test
    public void closeRejectsNewAcquireAndDrainsIdle() throws Exception {
        ConnectionPool pool = new ConnectionPool(2);
        assertEquals(2, pool.availableConnections());
        pool.close();
        assertTrue(pool.isClosed());
        assertEquals(0, pool.availableConnections());
        assertThrows(IllegalStateException.class, pool::acquire);
    }

    @Test
    public void concurrentWorkersNeverExceedMaxActive() throws Exception {
        int max = 3;
        int workers = 12;
        try (ConnectionPool pool = new ConnectionPool(0, max)) {
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(workers);
            AtomicInteger peakActive = new AtomicInteger();
            AtomicInteger failures = new AtomicInteger();

            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        try (ConnectionPool.Lease lease = pool.acquireLease()) {
                            int active = pool.activeConnections();
                            peakActive.accumulateAndGet(active, Math::max);
                            Thread.sleep(20);
                            lease.get().execute("SELECT 1");
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(0, failures.get());
            assertTrue(peakActive.get() <= max, "peak active " + peakActive.get());
            assertEquals(max, pool.availableConnections());
        }
    }

    @Test
    public void rejectsInvalidSizes() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPool(0));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPool(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPool(3, 2));
    }
}
