import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Demo: several workers sharing a small pool. */
public final class ConnectionPoolDemo {
    private ConnectionPoolDemo() {
    }

    public static void main(String[] args) throws Exception {
        int poolSize = 3;
        int workers = 5;
        try (ConnectionPool pool = new ConnectionPool(poolSize)) {
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            CountDownLatch done = new CountDownLatch(workers);
            AtomicInteger success = new AtomicInteger();

            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    try (ConnectionPool.Lease lease = pool.acquireLease()) {
                        Connection conn = lease.get();
                        System.out.println(Thread.currentThread().getName()
                                + " acquired connection " + conn.getId());
                        conn.execute("SELECT * FROM users");
                        Thread.sleep(200);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println(Thread.currentThread().getName() + " failed: " + e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            System.out.printf("Done. success=%d available=%d created=%d acquires=%d%n",
                    success.get(), pool.availableConnections(), pool.createdConnections(), pool.acquireCount());
        }
    }
}
