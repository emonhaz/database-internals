public class ConnectionPoolTest {

    public static void main(String[] args) throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(2); // max 2 connections

        Connection c1 = pool.acquire();
        System.out.println("Acquired c1: " + c1.getId());

        Connection c2 = pool.acquire();
        System.out.println("Acquired c2: " + c2.getId());

        System.out.println("Available connections (should be 0): " + pool.availableConnections());

        pool.release(c1);
        System.out.println("Released c1");

        System.out.println("Available connections (should be 1): " + pool.availableConnections());

        Connection c3 = pool.acquire();
        System.out.println("Acquired c3: " + c3.getId());

        // Optional: try acquiring with timeout
        Thread t = new Thread(() -> {
            try {
                System.out.println("Trying to acquire with timeout...");
                Connection c4 = pool.acquire(1, TimeUnit.SECONDS);
                if (c4 == null) {
                    System.out.println("Timed out waiting for connection");
                } else {
                    System.out.println("Acquired c4: " + c4.getId());
                    pool.release(c4);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        t.start();
        Thread.sleep(1500); // Wait for timeout test to complete
    }
}
