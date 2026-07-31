# Connection pool

Bounded pool of reusable {@link Connection} objects, modeled after JDBC pools
(HikariCP / commons-dbcp style) without a real database driver.

## Features

- Fixed {@code maxSize} hard cap
- Optional lazy growth via {@code new ConnectionPool(minSize, maxSize)}
- Blocking and timed {@code acquire}
- Borrow tracking (rejects double-release / foreign connections)
- {@code Lease} + try-with-resources so connections are always returned
- {@code close()} stops new borrows and discards idle connections
- Light metrics: available / active / created / waiting / acquire / timeout counts

## Run

```bash
javac -d target/classes \
  src/main/java/com/example/database/internals/ConnectionPool/*.java
java -cp target/classes ConnectionPoolDemo
java -cp target/classes ConcurrencyTest
java -cp target/classes ConnectionPoolTest
```

Tests:

```bash
# mvn test -Dtest=ConnectionPoolUnitTest
```

## Usage

```java
try (ConnectionPool pool = new ConnectionPool(0, 10)) { // lazy up to 10
    try (ConnectionPool.Lease lease = pool.acquireLease(1, TimeUnit.SECONDS)) {
        if (lease == null) {
            // timed out
            return;
        }
        lease.get().execute("SELECT 1");
    }
}
```

## Notes

- This is an in-memory teaching pool (no TCP/JDBC). Swap {@code Connection} for
  {@code java.sql.Connection} and add validation/eviction for production.
- Prefer {@code offer} on return over unbounded {@code put} so a bookkeeping bug
  cannot deadlock the caller forever.
