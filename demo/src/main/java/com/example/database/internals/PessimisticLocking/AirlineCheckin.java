import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent airline check-in demo using PostgreSQL row locks.
 *
 * Default mode uses {@code SELECT ... FOR UPDATE SKIP LOCKED} so workers claim
 * different free seats without blocking each other. Pass {@code --blocking} to
 * compare with plain {@code FOR UPDATE} (workers queue on the same row).
 *
 * DB settings: {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD} (optional defaults for local).
 */
public class AirlineCheckin {
    private static final String JDBC_URL = env("DB_URL", "jdbc:postgresql://localhost:5432/shard0");
    private static final String DB_USER = env("DB_USER", "postgres");
    // Default matches other local demos in this repo; override with DB_PASSWORD.
    private static final String DB_PASSWORD = env("DB_PASSWORD", "SwimAndSoar");
    private static final int MAX_WORKERS = 32;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 60L;

    enum LockMode {
        SKIP_LOCKED("FOR UPDATE SKIP LOCKED"),
        BLOCKING("FOR UPDATE");

        private final String clause;

        LockMode(String clause) {
            this.clause = clause;
        }

        String selectFreeSeatSql() {
            return "SELECT id, name FROM seats "
                    + "WHERE user_id IS NULL "
                    + "ORDER BY id "
                    + clause + " "
                    + "LIMIT 1";
        }
    }

    static class User {
        final int id;
        final String name;

        User(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class Seat {
        final int id;
        final String name;

        Seat(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static void main(String[] args) throws Exception {
        LockMode lockMode = parseLockMode(args);
        List<User> users = fetchUsers();
        if (users.isEmpty()) {
            System.out.println("No users found. Apply schema.sql and seed data first.");
            return;
        }

        int workers = Math.min(users.size(), MAX_WORKERS);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch latch = new CountDownLatch(users.size());
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        System.out.printf("Booking with %s (%d users, %d workers)%n", lockMode, users.size(), workers);

        for (User user : users) {
            pool.submit(() -> {
                try {
                    Seat seat = bookSeat(user, lockMode);
                    if (seat != null) {
                        booked.incrementAndGet();
                        System.out.printf("%s was assigned to seat %s%n", user.name, seat.name);
                    } else {
                        failed.incrementAndGet();
                        System.out.printf("Could not assign a seat to %s%n", user.name);
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.err.printf("Booking failed for %s: %s%n", user.name, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        if (!pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }

        System.out.printf("Done. booked=%d failed=%d remainingFreeSeats=%d%n",
                booked.get(), failed.get(), countFreeSeats());
    }

    static LockMode parseLockMode(String[] args) {
        for (String arg : args) {
            if ("--blocking".equals(arg)) {
                return LockMode.BLOCKING;
            }
        }
        return LockMode.SKIP_LOCKED;
    }

    static List<User> fetchUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM users ORDER BY id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("name")));
            }
        }
        return users;
    }

    static int countFreeSeats() throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM seats WHERE user_id IS NULL");
             ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Claim one free seat under a row-level lock, then assign it to {@code user}.
     * Returns {@code null} when no free seat is available (or claim lost).
     */
    static Seat bookSeat(User user, LockMode lockMode) throws SQLException {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer seatId;
                String seatName;
                try (PreparedStatement select = conn.prepareStatement(lockMode.selectFreeSeatSql());
                     ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return null;
                    }
                    seatId = rs.getInt("id");
                    seatName = rs.getString("name");
                }

                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE seats SET user_id = ? WHERE id = ? AND user_id IS NULL")) {
                    update.setInt(1, user.id);
                    update.setInt(2, seatId);
                    int updated = update.executeUpdate();
                    if (updated != 1) {
                        conn.rollback();
                        return null;
                    }
                }

                conn.commit();
                return new Seat(seatId, seatName);
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
