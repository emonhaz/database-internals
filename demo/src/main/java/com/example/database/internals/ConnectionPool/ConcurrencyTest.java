import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Manual concurrency smoke test: 4 workers, pool size 2. */
public final class ConcurrencyTest {
    private ConcurrencyTest() {
    }

    public static void main(String[] args) throws Exception {
        try (ConnectionPool pool = new ConnectionPool(2)) {
            int workers = 4;
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            CountDownLatch done = new CountDownLatch(workers);

            for (int i = 1; i <= workers; i++) {
                executor.submit(() -> {
                    try (ConnectionPool.Lease lease = pool.acquireLease()) {
                        Connection conn = lease.get();
                        System.out.println(Thread.currentThread().getName()
                                + " acquired connection " + conn.getId()
                                + " (active=" + pool.activeConnections() + ")");
                        Thread.sleep(500);
                    } catch (Exception e) {
                        System.err.println(Thread.currentThread().getName() + ": " + e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            System.out.println("Available after drain: " + pool.availableConnections());
        }
    }
}
