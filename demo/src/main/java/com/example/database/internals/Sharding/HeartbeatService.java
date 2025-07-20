public class HeartbeatService {
    public static void updateHeartbeat(String userId) {
        try {
            Connection conn = ShardManager.getShardConnection(userId);
            String query = "REPLACE INTO oo_heartbeats (user_id, last_hb) VALUES (?, ?)";
            try (var stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);
                stmt.setLong(2, System.currentTimeMillis() / 1000);
                stmt.executeUpdate();
                System.out.println("Heartbeat updated for user: " + userId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isOnline(String userId) {
        try {
            Connection conn = ShardManager.getShardConnection(userId);
            String query = "SELECT last_hb FROM oo_heartbeats WHERE user_id = ?";
            try (var stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    int lastHb = rs.getInt("last_hb");
                    return lastHb > (System.currentTimeMillis() / 1000) - 30;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
