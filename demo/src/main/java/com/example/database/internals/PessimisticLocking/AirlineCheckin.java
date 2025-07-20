import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AirlineCheckin {
    static final String JDBC_URL = "jdbc:postgresql://localhost:5432/shard0";
    static final String DB_USER = "postgres";
    static final String DB_PASS = "SwimAndSoar";

    static class User {
        int id;
        String name;
        User(int id, String name) { this.id = id; this.name = name; }
    }

    public static void main(String[] args) throws Exception {
        List<User> users = fetchUsers();
        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        CountDownLatch latch = new CountDownLatch(users.size());

        for (User user : users) {
            pool.submit(() -> {
                try {
                    Seat seat = bookSeat(user);
                    if (seat != null) {
                        System.out.printf("%s was assigned to seat %s%n", user.name, seat.name);
                    } else {
                        System.out.printf("Could not assign a seat to %s%n", user.name);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
    }

    static List<User> fetchUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM users")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("name")));
            }
        }
        return users;
    }

    static class Seat {
        int id;
        String name;
        Seat(int id, String name) { this.id = id; this.name = name; }
    }

    static Seat bookSeat(User user) throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name FROM seats WHERE user_id IS NULL FOR UPDATE SKIP LOCKED LIMIT 1"
            )) {
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return null;
                }

                int seatId = rs.getInt("id");
                String seatName = rs.getString("name");

                try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE seats SET user_id = ? WHERE id = ?"
                )) {
                    update.setInt(1, user.id);
                    update.setInt(2, seatId);
                    update.executeUpdate();
                }

                conn.commit();
                return new Seat(seatId, seatName);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
