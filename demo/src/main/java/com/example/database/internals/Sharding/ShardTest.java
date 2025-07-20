public class ShardTest {
    public static void main(String[] args) {
        try {
            ShardManager manager = new ShardManager();
            UserService userService = new UserService(manager);

            userService.createUser(101, "Alice");
            userService.createUser(202, "Bob");

            manager.closeAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
