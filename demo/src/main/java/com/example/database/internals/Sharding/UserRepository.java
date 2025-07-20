public class UserRepository {
    private final ShardManager shardManager;

    public UserRepository(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    public void createUser(String userId, String name) throws SQLException {
        DataSource ds = shardManager.getDataSource(userId);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public String getUser(String userId) throws SQLException {
        DataSource ds = shardManager.getDataSource(userId);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
            return null;
        }
    }
}
