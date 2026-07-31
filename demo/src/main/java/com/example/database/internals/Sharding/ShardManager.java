import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link ShardRouter} for in-memory demos or JDBC shard URLs.
 */
public final class ShardManager implements AutoCloseable {
    private final ShardRouter router;

    public ShardManager(int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be > 0");
        }
        List<ShardStore> stores = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            stores.add(new InMemoryShardStore(i));
        }
        this.router = new ShardRouter(stores);
    }

    public ShardManager(List<String> jdbcUrls) {
        Objects.requireNonNull(jdbcUrls, "jdbcUrls");
        if (jdbcUrls.isEmpty()) {
            throw new IllegalArgumentException("jdbcUrls must not be empty");
        }
        List<ShardStore> stores = new ArrayList<>(jdbcUrls.size());
        for (int i = 0; i < jdbcUrls.size(); i++) {
            stores.add(new JdbcShardStore(i, jdbcUrls.get(i)));
        }
        this.router = new ShardRouter(stores);
    }

    public static ShardManager inMemory(int shardCount) {
        return new ShardManager(shardCount);
    }

    public static ShardManager jdbcDefaults() {
        List<String> urls = new ArrayList<>();
        urls.add(env("SHARD0_URL", "jdbc:postgresql://localhost:5432/shard0"));
        urls.add(env("SHARD1_URL", "jdbc:postgresql://localhost:5432/shard1"));
        return new ShardManager(urls);
    }

    public ShardRouter router() {
        return router;
    }

    public int shardIndexFor(String userId) {
        return router.shardIndexFor(userId);
    }

    @Override
    public void close() {
        router.close();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
