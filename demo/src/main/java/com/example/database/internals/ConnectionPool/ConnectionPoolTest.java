import java.util.concurrent.TimeUnit;

/** Manual smoke checks for acquire / release / timeout. Prefer ConnectionPoolUnitTest. */
public final class ConnectionPoolTest {
    private ConnectionPoolTest() {
    }

    public static void main(String[] args) throws Exception {
        try (ConnectionPool pool = new ConnectionPool(2)) {
            Connection c1 = pool.acquire();
            Connection c2 = pool.acquire();
            System.out.println("Acquired c1=" + c1.getId() + " c2=" + c2.getId());
            System.out.println("Available (expect 0): " + pool.availableConnections());

            pool.release(c1);
            System.out.println("Available after release (expect 1): " + pool.availableConnections());

            Connection c3 = pool.acquire();
            System.out.println("Acquired c3=" + c3.getId());

            Thread t = new Thread(() -> {
                try {
                    Connection timed = pool.acquire(500, TimeUnit.MILLISECONDS);
                    if (timed == null) {
                        System.out.println("Timed out waiting for connection (expected)");
                    } else {
                        System.out.println("Unexpected acquire: " + timed.getId());
                        pool.release(timed);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();
            t.join();

            pool.release(c2);
            pool.release(c3);
            System.out.println("Final available=" + pool.availableConnections()
                    + " timeouts=" + pool.timeoutCount());
        }
    }
}
