public class ShardingTest {
    public static void main(String[] args) throws SQLException {
        ShardManager shardManager = new ShardManager(3);
        UserRepository userRepository = new UserRepository(shardManager);
        UserApi userApi = new UserApi(userRepository);

        userApi.handleCreateUser("user123", "Alice");
        userApi.handleCreateUser("user999", "Bob");
    }
}
