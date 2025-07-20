public class ConcurrencyTest {

    public static void main(String[] args) {
        ConnectionPool pool = new ConnectionPool(2);

        Runnable task = () -> {
            try {
                Connection conn = pool.acquire();
                System.out.println(Thread.currentThread().getName() + " acquired connection " + conn.getId());
                Thread.sleep(2000); // simulate work
                pool.release(conn);
                System.out.println(Thread.currentThread().getName() + " released connection " + conn.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Run 4 threads → only 2 should be able to acquire at a time
        for (int i = 1; i <= 4; i++) {
            Thread t = new Thread(task, "Worker-" + i);
            t.start();
        }
    }
}
