import java.time.Instant;

public class CacheEntry {
    public final String content;
    public final Instant expiry;

    public CacheEntry(String content, int ttlSeconds) {
        this.content = content;
        this.expiry = Instant.now().plusSeconds(ttlSeconds);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
