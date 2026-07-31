/**
 * Per-shard storage backend. Implementations may be in-memory (tests/demo) or JDBC.
 */
public interface ShardStore extends AutoCloseable {
    int shardId();

    void upsertUser(String userId, String name);

    String getUser(String userId);

    void upsertHeartbeat(String userId, long epochSeconds);

    Long getHeartbeat(String userId);

    boolean isHealthy();

    @Override
    void close();
}
