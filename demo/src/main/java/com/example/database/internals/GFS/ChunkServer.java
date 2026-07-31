import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process chunk store (stands in for a GFS chunkserver).
 * Tracks heartbeats and used capacity for placement decisions.
 */
public final class ChunkServer {
    private final String serverId;
    private final ConcurrentHashMap<String, Chunk> chunkStore = new ConcurrentHashMap<>();
    private final AtomicLong usedBytes = new AtomicLong();
    private final AtomicLong lastHeartbeatMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean alive = new AtomicBoolean(true);

    public ChunkServer(String serverId) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
    }

    public String getServerId() {
        return serverId;
    }

    public void heartbeat() {
        lastHeartbeatMs.set(System.currentTimeMillis());
        alive.set(true);
    }

    public void markDead() {
        alive.set(false);
    }

    public boolean isAlive() {
        return alive.get();
    }

    public long getLastHeartbeatMs() {
        return lastHeartbeatMs.get();
    }

    public long getUsedBytes() {
        return usedBytes.get();
    }

    public int chunkCount() {
        return chunkStore.size();
    }

    public Set<String> chunkIds() {
        return chunkStore.keySet();
    }

    public void storeChunk(String chunkId, byte[] data) {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(data, "data");
        ensureAlive();
        Chunk chunk = new Chunk(chunkId, data);
        Chunk prev = chunkStore.put(chunkId, chunk);
        if (prev != null) {
            usedBytes.addAndGet(-prev.size());
        }
        usedBytes.addAndGet(chunk.size());
        heartbeat();
    }

    public void storeChunk(String chunkId, String data) {
        storeChunk(chunkId, data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public Chunk readChunk(String chunkId) {
        Objects.requireNonNull(chunkId, "chunkId");
        ensureAlive();
        Chunk chunk = chunkStore.get(chunkId);
        if (chunk == null) {
            return null;
        }
        if (!chunk.matchesChecksum()) {
            throw new IllegalStateException("Checksum mismatch for chunk " + chunkId + " on " + serverId);
        }
        heartbeat();
        return chunk;
    }

    public boolean hasChunk(String chunkId) {
        return chunkStore.containsKey(chunkId);
    }

    public boolean deleteChunk(String chunkId) {
        Chunk removed = chunkStore.remove(chunkId);
        if (removed != null) {
            usedBytes.addAndGet(-removed.size());
            return true;
        }
        return false;
    }

    private void ensureAlive() {
        if (!alive.get()) {
            throw new IllegalStateException("ChunkServer " + serverId + " is not alive");
        }
    }
}
