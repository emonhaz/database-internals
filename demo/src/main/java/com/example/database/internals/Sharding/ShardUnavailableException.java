/** Thrown when the owning shard for a key is down (no cross-shard failover). */
public final class ShardUnavailableException extends RuntimeException {
    public ShardUnavailableException(String message) {
        super(message);
    }
}
