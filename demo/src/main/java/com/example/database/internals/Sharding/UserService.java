import java.util.Objects;

/** Thin service over {@link UserRepository} (keeps API / repo separation). */
public final class UserService {
    private final UserRepository repository;
    private final ShardRouter router;

    public UserService(ShardManager manager) {
        Objects.requireNonNull(manager, "manager");
        this.repository = new UserRepository(manager);
        this.router = manager.router();
    }

    public UserService(UserRepository repository, ShardRouter router) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.router = Objects.requireNonNull(router, "router");
    }

    public void createUser(String userId, String name) {
        repository.createUser(userId, name);
    }

    public String getUser(String userId) {
        return repository.getUser(userId);
    }

    public int shardFor(String userId) {
        return router.shardIndexFor(userId);
    }
}
