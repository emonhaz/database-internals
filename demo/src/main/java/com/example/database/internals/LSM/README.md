# LSM Tree

Minimal Log-Structured Merge-Tree: writes go to a sorted **memtable**, which
flushes to immutable **SSTables** on disk. Reads check memtable first, then
SSTables newest-to-oldest. Deletes use **tombstones**. Too many SSTables triggers
**compaction**.

## Layout

| Class | Role |
|-------|------|
| `Memtable` | Concurrent sorted buffer + tombstones |
| `SSTable` | On-disk sorted file + Bloom filter + sparse index |
| `LSMTree` | put/get/delete, flush, compact, recover from dir |
| `LSMCreator` | Demo main |

## Run

```bash
# BloomFilter is used by SSTable (same default package)
javac -d target/classes \
  src/main/java/com/example/database/internals/BloomFilter/BloomFilter.java \
  src/main/java/com/example/database/internals/LSM/*.java
java -cp target/classes LSMCreator /tmp/lsm-data
```

## Usage

```java
try (LSMTree tree = new LSMTree("data", /*memThreshold*/ 100, /*compactAfter*/ 4)) {
    tree.put("k", "v");
    tree.delete("k");
    String v = tree.get("k"); // null
    tree.flush();
    tree.compact();
}
```

## Reliability / scale notes

- Flush writes to `*.tmp` then renames into place
- Point lookups skip SSTables via Bloom filter; sparse index seeks near the key
- Compaction merges SSTables and drops tombstones / overwritten keys
- Directory recovery reloads `sst-*.dat` on startup
- Read/write lock: concurrent gets, exclusive flush/compact
