import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserService {
    private final ShardManager shardManager;

    public UserService(ShardManager shardManager) {
        this.shardManager = shardManager;
    }

    public void createUser(int userId, String name) throws SQLException {
        Connection conn = shardManager.getConnectionForUser(userId);

        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)")) {
            stmt.setInt(1, userId);
            stmt.setString(2, name);
            stmt.executeUpdate();
        }
    }
}
