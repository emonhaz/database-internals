import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process master: namespace + chunk → replica locations.
 * Chooses the least-loaded alive chunkservers for new placements.
 */
public final class MasterServer {
    private final ConcurrentHashMap<String, FileMetadata> fileTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> chunkLocations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChunkServer> servers = new ConcurrentHashMap<>();
    private final AtomicInteger placementCursor = new AtomicInteger();
    private final int defaultReplicationFactor;
    private final long heartbeatTimeoutMs;

    public MasterServer() {
        this(3, 5_000L);
    }

    public MasterServer(int defaultReplicationFactor, long heartbeatTimeoutMs) {
        if (defaultReplicationFactor < 1) {
            throw new IllegalArgumentException("defaultReplicationFactor must be >= 1");
        }
        this.defaultReplicationFactor = defaultReplicationFactor;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    public void registerChunkServer(ChunkServer server) {
        Objects.requireNonNull(server, "server");
        servers.put(server.getServerId(), server);
        server.heartbeat();
    }

    public void heartbeat(String serverId) {
        ChunkServer server = servers.get(serverId);
        if (server != null) {
            server.heartbeat();
        }
    }

    public Set<String> listFiles() {
        return Collections.unmodifiableSet(fileTable.keySet());
    }

    public boolean createFile(String filename) {
        Objects.requireNonNull(filename, "filename");
        if (filename.isEmpty()) {
            throw new IllegalArgumentException("filename must not be empty");
        }
        FileMetadata existing = fileTable.putIfAbsent(filename, new FileMetadata(filename));
        return existing == null;
    }

    public boolean deleteFile(String filename) {
        FileMetadata meta = fileTable.remove(filename);
        if (meta == null) {
            return false;
        }
        for (String chunkId : meta.getChunkIds()) {
            chunkLocations.remove(chunkId);
        }
        return true;
    }

    public FileMetadata getMetadata(String filename) {
        return fileTable.get(filename);
    }

    public String generateChunkId() {
        return UUID.randomUUID().toString();
    }

    public int getDefaultReplicationFactor() {
        return defaultReplicationFactor;
    }

    /**
     * Picks up to {@code replicationFactor} healthy servers (least used bytes first,
     * with round-robin tie-break) and records the chunk under {@code filename}.
     */
    public List<ChunkServer> allocateChunk(String filename, String chunkId, int replicationFactor) {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(chunkId, "chunkId");
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be >= 1");
        }

        fileTable.computeIfAbsent(filename, FileMetadata::new);

        refreshServerLiveness();
        List<ChunkServer> healthy = new ArrayList<>();
        for (ChunkServer server : servers.values()) {
            if (server.isAlive()) {
                healthy.add(server);
            }
        }
        if (healthy.isEmpty()) {
            throw new IllegalStateException("No healthy chunkservers available");
        }

        healthy.sort(Comparator
                .comparingLong(ChunkServer::getUsedBytes)
                .thenComparing(ChunkServer::getServerId));

        int start = Math.floorMod(placementCursor.getAndIncrement(), healthy.size());
        List<ChunkServer> chosen = new ArrayList<>(Math.min(replicationFactor, healthy.size()));
        for (int i = 0; i < healthy.size() && chosen.size() < replicationFactor; i++) {
            chosen.add(healthy.get((start + i) % healthy.size()));
        }

        CopyOnWriteArrayList<String> locations = new CopyOnWriteArrayList<>();
        for (ChunkServer server : chosen) {
            locations.add(server.getServerId());
        }
        chunkLocations.put(chunkId, locations);
        fileTable.get(filename).addChunk(chunkId);
        return chosen;
    }

    public List<ChunkServer> getChunkReplicas(String chunkId) {
        List<String> ids = chunkLocations.get(chunkId);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        refreshServerLiveness();
        List<ChunkServer> replicas = new ArrayList<>();
        for (String id : ids) {
            ChunkServer server = servers.get(id);
            if (server != null && server.isAlive()) {
                replicas.add(server);
            }
        }
        return replicas;
    }

    public List<String> getChunkLocations(String chunkId) {
        List<String> ids = chunkLocations.get(chunkId);
        return ids == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(ids));
    }

    public int healthyServerCount() {
        refreshServerLiveness();
        int count = 0;
        for (ChunkServer server : servers.values()) {
            if (server.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void refreshServerLiveness() {
        long now = System.currentTimeMillis();
        for (ChunkServer server : servers.values()) {
            if (now - server.getLastHeartbeatMs() > heartbeatTimeoutMs) {
                server.markDead();
            }
        }
    }
}
