import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Routes keys to shards with deterministic hashing.
 *
 * <p>Important: an unhealthy shard does <em>not</em> failover to another shard for the same key
 * (that would read/write the wrong partition). Callers get {@link ShardUnavailableException}.
 */
public final class ShardRouter implements AutoCloseable {
    private final List<ShardStore> shards;
    private final Set<Integer> healthyShardIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService healthScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "shard-health");
                t.setDaemon(true);
                return t;
            });
    private final long healthIntervalSeconds;

    public ShardRouter(List<ShardStore> shards) {
        this(shards, 5);
    }

    public ShardRouter(List<ShardStore> shards, long healthIntervalSeconds) {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("shards must not be empty");
        }
        this.shards = Collections.unmodifiableList(new ArrayList<>(shards));
        this.healthIntervalSeconds = healthIntervalSeconds;
        for (ShardStore shard : this.shards) {
            healthyShardIds.add(shard.shardId());
        }
        refreshHealth();
        if (healthIntervalSeconds > 0) {
            healthScheduler.scheduleAtFixedRate(
                    this::refreshHealthSafe, healthIntervalSeconds, healthIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    public int shardCount() {
        return shards.size();
    }

    /** Deterministic shard index for a user id. */
    public int shardIndexFor(String userId) {
        Objects.requireNonNull(userId, "userId");
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be empty");
        }
        return Math.floorMod(userId.hashCode(), shards.size());
    }

    public ShardStore storeFor(String userId) {
        int index = shardIndexFor(userId);
        ShardStore store = shards.get(index);
        if (!healthyShardIds.contains(store.shardId()) || !store.isHealthy()) {
            throw new ShardUnavailableException(
                    "Shard " + store.shardId() + " unavailable for user " + userId);
        }
        return store;
    }

    public boolean isShardHealthy(int shardId) {
        return healthyShardIds.contains(shardId);
    }

    public Map<Integer, Boolean> healthSnapshot() {
        Map<Integer, Boolean> snap = new LinkedHashMap<>();
        for (ShardStore shard : shards) {
            snap.put(shard.shardId(), healthyShardIds.contains(shard.shardId()));
        }
        return snap;
    }

    public void refreshHealth() {
        for (ShardStore shard : shards) {
            if (shard.isHealthy()) {
                healthyShardIds.add(shard.shardId());
            } else {
                healthyShardIds.remove(shard.shardId());
            }
        }
    }

    private void refreshHealthSafe() {
        try {
            refreshHealth();
        } catch (Exception ignored) {
            // keep last known health
        }
    }

    @Override
    public void close() {
        healthScheduler.shutdownNow();
        for (ShardStore shard : shards) {
            try {
                shard.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
