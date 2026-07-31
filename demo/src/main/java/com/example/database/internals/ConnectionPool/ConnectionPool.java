import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-capacity connection pool with lazy growth and borrow/return safety.
 *
 * <ul>
 *   <li>Grows from {@code minSize} up to {@code maxSize} under load</li>
 *   <li>Tracks borrowed connections so double-release / foreign release fail fast</li>
 *   <li>Supports blocking acquire, timed acquire, and {@link AutoCloseable} leases</li>
 *   <li>{@link #close()} rejects new borrows and drains idle connections</li>
 * </ul>
 */
public final class ConnectionPool implements AutoCloseable {
    private final int minSize;
    private final int maxSize;
    private final BlockingQueue<Connection> idle;
    private final Set<Connection> borrowed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger waitingAcquires = new AtomicInteger();
    private final AtomicLong acquireCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private volatile boolean closed;

    /** Eager pool: pre-creates {@code maxSize} connections. */
    public ConnectionPool(int maxSize) {
        this(maxSize, maxSize);
    }

    /**
     * @param minSize connections created up front (use {@code 0} for fully lazy)
     * @param maxSize hard cap on live connections
     */
    public ConnectionPool(int minSize, int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        if (minSize < 0 || minSize > maxSize) {
            throw new IllegalArgumentException("minSize must be in [0, maxSize]");
        }
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.idle = new ArrayBlockingQueue<>(maxSize);
        for (int i = 0; i < minSize; i++) {
            Connection connection = createConnection();
            if (!idle.offer(connection)) {
                discard(connection);
            }
        }
    }

    /** Blocks until a connection is available. */
    public Connection acquire() throws InterruptedException {
        return acquireInternal(-1L, TimeUnit.MILLISECONDS);
    }

    /** Waits up to {@code timeout} for a connection; returns {@code null} on timeout. */
    public Connection acquire(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        return acquireInternal(timeout, unit);
    }

    /**
     * Borrow as a lease that returns the connection to the pool when closed.
     * Prefer try-with-resources.
     */
    public Lease acquireLease() throws InterruptedException {
        return new Lease(acquire());
    }

    public Lease acquireLease(long timeout, TimeUnit unit) throws InterruptedException {
        Connection connection = acquire(timeout, unit);
        return connection == null ? null : new Lease(connection);
    }

    public void release(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.owner() != this) {
            throw new IllegalArgumentException("Connection does not belong to this pool");
        }
        if (!borrowed.remove(connection)) {
            throw new IllegalStateException("Connection is not currently borrowed: " + connection.getId());
        }

        if (closed || !connection.isOpen()) {
            discard(connection);
            return;
        }

        if (!idle.offer(connection)) {
            // Should be rare with correct accounting — discard instead of blocking forever.
            discard(connection);
        }
    }

    public int availableConnections() {
        return idle.size();
    }

    public int activeConnections() {
        return borrowed.size();
    }

    public int createdConnections() {
        return created.get();
    }

    public int maxSize() {
        return maxSize;
    }

    public int minSize() {
        return minSize;
    }

    public int waitingAcquires() {
        return waitingAcquires.get();
    }

    public long acquireCount() {
        return acquireCount.get();
    }

    public long timeoutCount() {
        return timeoutCount.get();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        Connection connection;
        while ((connection = idle.poll()) != null) {
            discard(connection);
        }
        // Borrowed connections are discarded when released.
    }

    private Connection acquireInternal(long timeout, TimeUnit unit) throws InterruptedException {
        ensureOpen();
        waitingAcquires.incrementAndGet();
        try {
            long deadlineNanos = timeout < 0L ? -1L : System.nanoTime() + unit.toNanos(timeout);
            while (true) {
                ensureOpen();

                Connection connection = idle.poll();
                if (connection != null) {
                    Connection checkedOut = checkoutOrNull(connection);
                    if (checkedOut != null) {
                        return checkedOut;
                    }
                    continue;
                }

                connection = tryCreate();
                if (connection != null) {
                    Connection checkedOut = checkoutOrNull(connection);
                    if (checkedOut != null) {
                        return checkedOut;
                    }
                    continue;
                }

                if (timeout == 0L) {
                    timeoutCount.incrementAndGet();
                    return null;
                }

                if (timeout < 0L) {
                    connection = idle.take();
                    Connection checkedOut = checkoutOrNull(connection);
                    if (checkedOut != null) {
                        return checkedOut;
                    }
                    continue;
                }

                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0L) {
                    timeoutCount.incrementAndGet();
                    return null;
                }
                connection = idle.poll(remaining, TimeUnit.NANOSECONDS);
                if (connection == null) {
                    timeoutCount.incrementAndGet();
                    return null;
                }
                Connection checkedOut = checkoutOrNull(connection);
                if (checkedOut != null) {
                    return checkedOut;
                }
            }
        } finally {
            waitingAcquires.decrementAndGet();
        }
    }

    private Connection checkoutOrNull(Connection connection) {
        if (connection == null) {
            return null;
        }
        if (!connection.isOpen()) {
            discard(connection);
            return null;
        }
        borrowed.add(connection);
        acquireCount.incrementAndGet();
        return connection;
    }

    private Connection tryCreate() {
        while (true) {
            int current = created.get();
            if (current >= maxSize) {
                return null;
            }
            if (created.compareAndSet(current, current + 1)) {
                return new Connection(this);
            }
        }
    }

    private Connection createConnection() {
        created.incrementAndGet();
        return new Connection(this);
    }

    private void discard(Connection connection) {
        connection.closeQuietly();
        created.decrementAndGet();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Connection pool is closed");
        }
    }

    /** AutoCloseable borrow that always releases (or discards if pool closed). */
    public final class Lease implements AutoCloseable {
        private final Connection connection;
        private boolean closedLease;

        Lease(Connection connection) {
            this.connection = connection;
        }

        public Connection get() {
            if (closedLease) {
                throw new IllegalStateException("Lease already closed");
            }
            return connection;
        }

        @Override
        public void close() {
            if (closedLease) {
                return;
            }
            closedLease = true;
            release(connection);
        }
    }
}
