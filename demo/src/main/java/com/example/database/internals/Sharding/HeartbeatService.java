import java.util.Objects;

/**
 * Per-user presence heartbeats stored on the same shard as the user
 * (so presence scales with the user partition).
 */
public final class HeartbeatService {
    private final ShardRouter router;
    private final long onlineWindowSeconds;

    public HeartbeatService(ShardRouter router) {
        this(router, 30);
    }

    public HeartbeatService(ShardRouter router, long onlineWindowSeconds) {
        this.router = Objects.requireNonNull(router, "router");
        if (onlineWindowSeconds <= 0) {
            throw new IllegalArgumentException("onlineWindowSeconds must be > 0");
        }
        this.onlineWindowSeconds = onlineWindowSeconds;
    }

    public void updateHeartbeat(String userId) {
        long now = System.currentTimeMillis() / 1000L;
        router.storeFor(userId).upsertHeartbeat(userId, now);
    }

    public boolean isOnline(String userId) {
        Long last = router.storeFor(userId).getHeartbeat(userId);
        if (last == null) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        return last > now - onlineWindowSeconds;
    }
}
