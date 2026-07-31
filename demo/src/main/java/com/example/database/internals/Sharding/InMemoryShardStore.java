import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe in-memory shard for demos and unit tests (no database required). */
public final class InMemoryShardStore implements ShardStore {
    private final int shardId;
    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> heartbeats = new ConcurrentHashMap<>();
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public InMemoryShardStore(int shardId) {
        if (shardId < 0) {
            throw new IllegalArgumentException("shardId must be >= 0");
        }
        this.shardId = shardId;
    }

    @Override
    public int shardId() {
        return shardId;
    }

    @Override
    public void upsertUser(String userId, String name) {
        ensureOpen();
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(name, "name");
        users.put(userId, name);
    }

    @Override
    public String getUser(String userId) {
        ensureOpen();
        return users.get(Objects.requireNonNull(userId, "userId"));
    }

    @Override
    public void upsertHeartbeat(String userId, long epochSeconds) {
        ensureOpen();
        heartbeats.put(Objects.requireNonNull(userId, "userId"), epochSeconds);
    }

    @Override
    public Long getHeartbeat(String userId) {
        ensureOpen();
        return heartbeats.get(Objects.requireNonNull(userId, "userId"));
    }

    @Override
    public boolean isHealthy() {
        return healthy.get() && !closed.get();
    }

    public void setHealthy(boolean value) {
        healthy.set(value);
    }

    @Override
    public void close() {
        closed.set(true);
        users.clear();
        heartbeats.clear();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Shard " + shardId + " is closed");
        }
        if (!healthy.get()) {
            throw new IllegalStateException("Shard " + shardId + " is unhealthy");
        }
    }
}
