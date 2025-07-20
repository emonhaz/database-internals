import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

// Step 1: Simulate a Connection object
class Connection {
    private static int counter = 0;
    private final int id;

    public Connection() {
        this.id = ++counter;
    }

    public int getId() {
        return id;
    }

    public void execute(String query) {
        System.out.println("Executing query on connection " + id + ": " + query);
    }
}

// Step 2: ConnectionPool implementation using Bounded Blocking Queue
public class ConnectionPool {

    private final BlockingQueue<Connection> pool;

    public ConnectionPool(int maxSize) {
        this.pool = new ArrayBlockingQueue<>(maxSize);
        for (int i = 0; i < maxSize; i++) {
            pool.offer(new Connection());
        }
    }

    // Borrow a connection (blocks if none available)
    public Connection acquire() throws InterruptedException {
        return pool.take();
    }

    // Borrow with timeout
    public Connection acquire(long timeout, TimeUnit unit) throws InterruptedException {
        return pool.poll(timeout, unit);
    }

    // Return connection to pool
    public void release(Connection connection) throws InterruptedException {
        if (connection != null) {
            pool.put(connection); // Blocks if pool is already full
        }
    }

    public int availableConnections() {
        return pool.size();
    }

    // For demonstration
    public static void main(String[] args) {
        ConnectionPool connectionPool = new ConnectionPool(3);

        Runnable task = () -> {
            try {
                Connection conn = connectionPool.acquire();
                System.out.println(Thread.currentThread().getName() + " acquired connection " + conn.getId());
                conn.execute("SELECT * FROM users");
                Thread.sleep(1000); // simulate query time
                connectionPool.release(conn);
                System.out.println(Thread.currentThread().getName() + " released connection " + conn.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Simulate 5 threads competing for 3 connections
        for (int i = 0; i < 5; i++) {
            new Thread(task).start();
        }
    }
}
