import java.util.Objects;

/** Persistence API routed through {@link ShardRouter}. */
public final class UserRepository {
    private final ShardRouter router;

    public UserRepository(ShardRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public UserRepository(ShardManager manager) {
        this(Objects.requireNonNull(manager, "manager").router());
    }

    public void createUser(String userId, String name) {
        router.storeFor(userId).upsertUser(userId, name);
    }

    public String getUser(String userId) {
        return router.storeFor(userId).getUser(userId);
    }
}
