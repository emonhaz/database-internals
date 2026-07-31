import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Simple façade mimicking an HTTP/API layer over the sharded user service. */
public final class UserApi {
    private final UserService userService;

    public UserApi(UserService userService) {
        this.userService = Objects.requireNonNull(userService, "userService");
    }

    public Map<String, Object> handleCreateUser(String userId, String name) {
        userService.createUser(userId, name);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "created");
        response.put("userId", userId);
        response.put("name", name);
        response.put("shard", userService.shardFor(userId));
        return response;
    }

    public Map<String, Object> handleGetUser(String userId) {
        String name = userService.getUser(userId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("name", name);
        response.put("shard", userService.shardFor(userId));
        response.put("found", name != null);
        return response;
    }
}
