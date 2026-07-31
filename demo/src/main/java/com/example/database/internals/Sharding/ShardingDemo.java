/**
 * Demo using in-memory shards (no Postgres required).
 * Pass {@code --jdbc} to use {@link ShardManager#jdbcDefaults()}.
 */
public final class ShardingDemo {
    private ShardingDemo() {
    }

    public static void main(String[] args) {
        boolean jdbc = false;
        for (String arg : args) {
            if ("--jdbc".equals(arg)) {
                jdbc = true;
            }
        }

        try (ShardManager manager = jdbc ? ShardManager.jdbcDefaults() : ShardManager.inMemory(3)) {
            UserService users = new UserService(manager);
            UserApi api = new UserApi(users);
            HeartbeatService heartbeats = new HeartbeatService(manager.router());

            System.out.println(api.handleCreateUser("user123", "Alice"));
            System.out.println(api.handleCreateUser("user999", "Bob"));
            System.out.println(api.handleCreateUser("user42", "Carol"));

            System.out.println(api.handleGetUser("user123"));
            System.out.println("health=" + manager.router().healthSnapshot());

            heartbeats.updateHeartbeat("user123");
            System.out.println("user123 online=" + heartbeats.isOnline("user123"));
            System.out.println("user999 online=" + heartbeats.isOnline("user999"));
        }
    }
}
