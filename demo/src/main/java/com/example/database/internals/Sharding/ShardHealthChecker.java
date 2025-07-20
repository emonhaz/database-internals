import java.util.concurrent.*;

public class ShardHealthChecker {
    private final Map<Integer, HikariDataSource> dataSources;
    private final Set<Integer> healthyShards = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ShardHealthChecker(Map<Integer, HikariDataSource> dataSources) {
        this.dataSources = dataSources;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<Integer, HikariDataSource> entry : dataSources.entrySet()) {
                try (Connection conn = entry.getValue().getConnection()) {
                    if (!conn.isClosed()) {
                        healthyShards.add(entry.getKey());
                    }
                } catch (Exception e) {
                    healthyShards.remove(entry.getKey());
                    System.err.println("Shard " + entry.getKey() + " is unhealthy.");
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public boolean isShardHealthy(int shardId) {
        return healthyShards.contains(shardId);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
