import java.util.*;

public class Memtable {
    private final TreeMap<String, String> map = new TreeMap<>();
    private final int threshold;

    public Memtable(int threshold) {
        this.threshold = threshold;
    }

    public void put(String key, String value) {
        map.put(key, value);
    }

    public String get(String key) {
        return map.get(key);
    }

    public boolean shouldFlush() {
        return map.size() >= threshold;
    }

    public SortedMap<String, String> flush() {
        var flushed = new TreeMap<>(map);
        map.clear();
        return flushed;
    }

    public int size() {
        return map.size();
    }
}
