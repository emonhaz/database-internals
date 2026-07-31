import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * JDBC-backed shard using HikariCP. Expects schema from {@code schema.sql}.
 * Credentials: {@code DB_USER} / {@code DB_PASSWORD} (defaults match other demos).
 */
public final class JdbcShardStore implements ShardStore {
    private final int shardId;
    private final HikariDataSource dataSource;

    public JdbcShardStore(int shardId, String jdbcUrl) {
        this(shardId, jdbcUrl, env("DB_USER", "postgres"), env("DB_PASSWORD", "SwimAndSoar"));
    }

    public JdbcShardStore(int shardId, String jdbcUrl, String user, String password) {
        if (shardId < 0) {
            throw new IllegalArgumentException("shardId must be >= 0");
        }
        this.shardId = shardId;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Objects.requireNonNull(jdbcUrl, "jdbcUrl"));
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setPoolName("ShardPool-" + shardId);
        config.setConnectionTimeout(3_000);
        config.setValidationTimeout(2_000);
        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id TEXT PRIMARY KEY, name TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS oo_heartbeats ("
                    + "user_id TEXT PRIMARY KEY, last_hb BIGINT NOT NULL)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to init schema for shard " + shardId, e);
        }
    }

    @Override
    public int shardId() {
        return shardId;
    }

    @Override
    public void upsertUser(String userId, String name) {
        String sql = "INSERT INTO users (id, name) VALUES (?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertUser failed on shard " + shardId, e);
        }
    }

    @Override
    public String getUser(String userId) {
        String sql = "SELECT name FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getUser failed on shard " + shardId, e);
        }
    }

    @Override
    public void upsertHeartbeat(String userId, long epochSeconds) {
        String sql = "INSERT INTO oo_heartbeats (user_id, last_hb) VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET last_hb = EXCLUDED.last_hb";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, epochSeconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertHeartbeat failed on shard " + shardId, e);
        }
    }

    @Override
    public Long getHeartbeat(String userId) {
        String sql = "SELECT last_hb FROM oo_heartbeats WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("last_hb") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getHeartbeat failed on shard " + shardId, e);
        }
    }

    @Override
    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
