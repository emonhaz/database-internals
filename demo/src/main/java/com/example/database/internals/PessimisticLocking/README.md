# Pessimistic locking demo (`AirlineCheckin`)

Concurrent seat assignment with PostgreSQL row locks.

## Setup

```bash
createdb shard0
psql -d shard0 -f schema.sql
```

Optional env overrides: `DB_URL`, `DB_USER`, `DB_PASSWORD`
(defaults match other local demos in this repo).

## Run

From `demo/` after compile:

```bash
# SKIP LOCKED (default): workers claim different free seats without waiting
java -cp target/classes:target/dependency/* AirlineCheckin

# Blocking FOR UPDATE: workers may queue on the same candidate row
java -cp target/classes:target/dependency/* AirlineCheckin --blocking
```

Reset seats between runs:

```sql
UPDATE seats SET user_id = NULL;
```
