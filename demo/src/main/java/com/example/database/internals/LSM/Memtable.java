import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sorted buffer. Flushing returns an immutable snapshot and clears the table.
 * Deletes are stored as tombstones so older SSTable values stay hidden after flush.
 */
public final class Memtable {
    static final String TOMBSTONE = "\u0000__TOMBSTONE__\u0000";

    private final ConcurrentSkipListMap<String, String> map = new ConcurrentSkipListMap<>();
    private final int threshold;
    private final AtomicInteger approxSize = new AtomicInteger();

    public Memtable(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be > 0");
        }
        this.threshold = threshold;
    }

    public void put(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        if (TOMBSTONE.equals(value)) {
            throw new IllegalArgumentException("value collides with tombstone sentinel");
        }
        String prev = map.put(key, value);
        if (prev == null) {
            approxSize.incrementAndGet();
        }
    }

    public void delete(String key) {
        Objects.requireNonNull(key, "key");
        String prev = map.put(key, TOMBSTONE);
        if (prev == null) {
            approxSize.incrementAndGet();
        }
    }

    public String get(String key) {
        Objects.requireNonNull(key, "key");
        String value = map.get(key);
        if (value == null) {
            return null;
        }
        if (TOMBSTONE.equals(value)) {
            return TOMBSTONE; // signal deleted to LSMTree
        }
        return value;
    }

    public boolean shouldFlush() {
        return approxSize.get() >= threshold;
    }

    public int size() {
        return map.size();
    }

    public int threshold() {
        return threshold;
    }

    /** Snapshot + clear. Caller must serialize access with writers if needed. */
    public SortedMap<String, String> flush() {
        if (map.isEmpty()) {
            return Collections.emptySortedMap();
        }
        SortedMap<String, String> snapshot = new ConcurrentSkipListMap<>(map);
        map.clear();
        approxSize.set(0);
        return Collections.unmodifiableSortedMap(snapshot);
    }
}
