import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EdgeServer {
    private final String id;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final OriginServer origin;
    private final int ttlSeconds = 10;

    public EdgeServer(String id, OriginServer origin) {
        this.id = id;
        this.origin = origin;
    }

    public String serve(String path) {
        CacheEntry entry = cache.get(path);
        if (entry == null || entry.isExpired()) {
            String content = origin.fetch(path);
            cache.put(path, new CacheEntry(content, ttlSeconds));
            return "[Edge " + id + "] " + content;
        }
        return "[Edge " + id + " Cached] " + entry.content;
    }

    public String getId() {
        return id;
    }
}
