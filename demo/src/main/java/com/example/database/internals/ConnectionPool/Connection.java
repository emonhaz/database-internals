import java.util.concurrent.atomic.AtomicInteger;

/** Simulated DB connection for the pool demo. */
public final class Connection {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id;
    private final ConnectionPool owner;
    private volatile boolean open = true;

    Connection(ConnectionPool owner) {
        this.id = COUNTER.incrementAndGet();
        this.owner = owner;
    }

    public int getId() {
        return id;
    }

    public boolean isOpen() {
        return open;
    }

    ConnectionPool owner() {
        return owner;
    }

    public void execute(String query) {
        if (!open) {
            throw new IllegalStateException("Connection " + id + " is closed");
        }
        System.out.println("Executing query on connection " + id + ": " + query);
    }

    /** Marks the connection unusable (discarded by the pool). */
    void closeQuietly() {
        open = false;
    }

    @Override
    public String toString() {
        return "Connection{id=" + id + ", open=" + open + "}";
    }
}
