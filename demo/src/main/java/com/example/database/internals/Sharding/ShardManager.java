import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class ShardManager {
    private final Map<Integer, String> shardJdbcUrls = new HashMap<>();
    private final Map<Integer, Connection> shardConnections = new HashMap<>();

    public ShardManager() throws SQLException {
        shardJdbcUrls.put(0, "jdbc:postgresql://localhost:5432/shard0");
        shardJdbcUrls.put(1, "jdbc:postgresql://localhost:5432/shard1");

        for (Map.Entry<Integer, String> entry : shardJdbcUrls.entrySet()) {
            Connection conn = DriverManager.getConnection(entry.getValue(), "postgres", "SwimAndSoar");
            shardConnections.put(entry.getKey(), conn);
        }
    }

    public static int getShardIndex(String userId) {
        // Static mapping logic based on userId
        switch (userId) {
            case "1": return 0;
            case "2": return 1;
            default: return Math.abs(userId.hashCode()) % shards.size();
        }
    }

    public Connection getConnectionForUser(int userId) {
        int shardId = userId % shardJdbcUrls.size(); // basic hash-based routing
        return shardConnections.get(shardId);
    }

    public static Connection getShardConnection(String userId) {
        int index = getShardIndex(userId);
        System.out.println("Using DB shard index: " + index);

        try {
            Connection conn = shards.get(index);
            if (conn.isValid(2)) {
                return conn;
            } else {
                System.out.println("Primary shard is not healthy. Trying next available...");
                // Try fallback (simple round-robin for demo)
                for (int i = 0; i < shards.size(); i++) {
                    if (i == index) continue;
                    if (shards.get(i).isValid(2)) {
                        System.out.println("Fallback to shard index: " + i);
                        return shards.get(i);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Shard connection error: " + e.getMessage());
        }

        throw new RuntimeException("No healthy shard available");
    }

    public void closeAll() throws SQLException {
        for (Connection conn : shardConnections.values()) {
            conn.close();
        }
    }
}
