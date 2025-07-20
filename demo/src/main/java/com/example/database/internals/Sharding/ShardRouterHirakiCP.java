import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class ShardRouter {
    private final Map<Integer, HikariDataSource> shardDataSources;
    private final ShardHealthChecker healthChecker;

    public ShardRouter(Map<Integer, String> jdbcUrls) {
        this.shardDataSources = new HashMap<>();
        for (Map.Entry<Integer, String> entry : jdbcUrls.entrySet()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(entry.getValue());
            config.setUsername("postgres");
            config.setPassword("SwimAndSoar");
            config.setMaximumPoolSize(10);
            config.setPoolName("ShardPool-" + entry.getKey());
            config.setConnectionTimeout(5000);
            shardDataSources.put(entry.getKey(), new HikariDataSource(config));
        }
        this.healthChecker = new ShardHealthChecker(shardDataSources);
        this.healthChecker.start();
    }

    public Connection getConnectionForUserId(int userId) throws SQLException {
        int shardId = userId % shardDataSources.size();
        return shardDataSources.get(shardId).getConnection();
    }

    public void close() {
        healthChecker.stop();
        shardDataSources.values().forEach(HikariDataSource::close);
    }
}
