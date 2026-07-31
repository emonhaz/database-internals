# Sharding

Deterministic key → shard routing with health checks.

Writes/reads for a user always go to **that user's shard**. If the shard is down,
the call fails with {@link ShardUnavailableException} — it does **not** silently
use another shard (that would break partition correctness).

## Layout

| Type | Role |
|------|------|
| `ShardRouter` | hash routing + health set |
| `ShardStore` | per-shard backend API |
| `InMemoryShardStore` | demo/tests (no DB) |
| `JdbcShardStore` | HikariCP + Postgres |
| `ShardManager` | wiring helper |
| `UserRepository` / `UserService` / `UserApi` | app layers |
| `HeartbeatService` | presence stored on the user shard |
| `ShardingDemo` | main |

## Run

```bash
# In-memory (default)
javac -d target/classes \
  -cp "path/to/hikari+postgresql+slf4j" \
  src/main/java/com/example/database/internals/Sharding/*.java
java -cp target/classes:deps ShardingDemo

# JDBC (needs shard0/shard1 + schema.sql)
java -cp target/classes:deps ShardingDemo --jdbc
```

From Maven `demo/` module (deps already in `pom.xml`):

```bash
mvn -q -DskipTests compile
# then run with appropriate classpath, or use your IDE
```

## Usage

```java
try (ShardManager manager = ShardManager.inMemory(3)) {
    UserApi api = new UserApi(new UserService(manager));
    api.handleCreateUser("user123", "Alice");
    System.out.println(api.handleGetUser("user123"));
}
```

## Notes

- Routing: `floorMod(userId.hashCode(), shardCount)`
- For smoother rebalancing when adding/removing shards, see the
  [`ConsistentHashing`](../ConsistentHashing/) demo
- Health checks run periodically; mark `InMemoryShardStore#setHealthy(false)` in tests
  to simulate outages
