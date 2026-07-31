import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Consistent-hashing load balancer with virtual nodes.
 *
 * <p>Designed for concurrent lookups and safe topology changes:
 * <ul>
 *   <li>{@link ConcurrentSkipListMap} ring for scalable ordered lookups</li>
 *   <li>per-server vnode tracking so removals are exact (no hash recomputation drift)</li>
 *   <li>collision probing so one vnode cannot silently overwrite another</li>
 *   <li>{@link #getReplicas(String, int)} for multi-node placement / failover</li>
 * </ul>
 */
public final class ConsistentHashingLoadBalancer {
    private static final int DEFAULT_VIRTUAL_NODES = 100;
    private static final int MAX_COLLISION_PROBES = 128;

    private final int virtualNodesPerServer;
    private final ConcurrentSkipListMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<String, List<Long>> serverVNodes = new ConcurrentHashMap<>();
    private final ReentrantLock topologyLock = new ReentrantLock();
    private final ThreadLocal<MessageDigest> digests;

    public ConsistentHashingLoadBalancer() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashingLoadBalancer(int virtualNodesPerServer) {
        if (virtualNodesPerServer <= 0) {
            throw new IllegalArgumentException("virtualNodesPerServer must be > 0");
        }
        this.virtualNodesPerServer = virtualNodesPerServer;
        this.digests = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("MD5 not available", e);
            }
        });
    }

    public int virtualNodesPerServer() {
        return virtualNodesPerServer;
    }

    public int ringSize() {
        return ring.size();
    }

    public Set<String> servers() {
        return Collections.unmodifiableSet(serverVNodes.keySet());
    }

    public boolean hasServer(String serverId) {
        return serverVNodes.containsKey(Objects.requireNonNull(serverId, "serverId"));
    }

    /**
     * Adds a physical server and places {@code virtualNodesPerServer} points on the ring.
     * Idempotent: re-adding an existing server is a no-op.
     */
    public void addServer(String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        if (serverId.isEmpty()) {
            throw new IllegalArgumentException("serverId must not be empty");
        }

        topologyLock.lock();
        try {
            if (serverVNodes.containsKey(serverId)) {
                return;
            }

            List<Long> vnodeHashes = new ArrayList<>(virtualNodesPerServer);
            for (int i = 0; i < virtualNodesPerServer; i++) {
                long hash = placeVirtualNode(serverId + "#" + i, serverId);
                vnodeHashes.add(hash);
            }
            serverVNodes.put(serverId, Collections.unmodifiableList(vnodeHashes));
        } finally {
            topologyLock.unlock();
        }
    }

    /**
     * Removes a physical server and all of its virtual nodes.
     * Idempotent: removing an unknown server is a no-op.
     */
    public void removeServer(String serverId) {
        Objects.requireNonNull(serverId, "serverId");

        topologyLock.lock();
        try {
            List<Long> vnodeHashes = serverVNodes.remove(serverId);
            if (vnodeHashes == null) {
                return;
            }
            for (Long hash : vnodeHashes) {
                ring.remove(hash, serverId);
            }
        } finally {
            topologyLock.unlock();
        }
    }

    /** Primary owner for {@code key}, or {@code null} when the ring is empty. */
    public String getServer(String key) {
        Objects.requireNonNull(key, "key");
        if (ring.isEmpty()) {
            return null;
        }
        Long nodeHash = clockwiseFrom(hash(key));
        return nodeHash == null ? null : ring.get(nodeHash);
    }

    /**
     * Distinct servers walking clockwise from {@code key} (primary first).
     * Useful for replicas / failover without placing everything on one node.
     */
    public List<String> getReplicas(String key, int count) {
        Objects.requireNonNull(key, "key");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        if (ring.isEmpty()) {
            return Collections.emptyList();
        }

        int limit = Math.min(count, serverVNodes.size());
        LinkedHashSet<String> replicas = new LinkedHashSet<>(limit);
        Long start = clockwiseFrom(hash(key));
        if (start == null) {
            return Collections.emptyList();
        }

        Long cursor = start;
        int guarded = 0;
        int maxSteps = ring.size();
        while (replicas.size() < limit && guarded < maxSteps) {
            String server = ring.get(cursor);
            if (server != null) {
                replicas.add(server);
            }
            Map.Entry<Long, String> next = ring.higherEntry(cursor);
            cursor = next != null ? next.getKey() : ring.firstKey();
            guarded++;
        }
        return new ArrayList<>(replicas);
    }

    /**
     * Counts how many of {@code keys} map to each server (for balance checks).
     */
    public Map<String, Integer> loadDistribution(Iterable<String> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String server : serverVNodes.keySet()) {
            counts.put(server, 0);
        }
        for (String key : keys) {
            String server = getServer(key);
            if (server != null) {
                counts.put(server, counts.getOrDefault(server, 0) + 1);
            }
        }
        return counts;
    }

    /** Fraction of keys that change owner when {@code serverId} is removed (approx. 1/N). */
    public double remapRatioOnRemove(String serverId, Iterable<String> keys) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(keys, "keys");
        if (!hasServer(serverId)) {
            return 0.0;
        }

        List<String> keyList = new ArrayList<>();
        for (String key : keys) {
            keyList.add(key);
        }
        if (keyList.isEmpty()) {
            return 0.0;
        }

        Map<String, String> before = new LinkedHashMap<>();
        for (String key : keyList) {
            before.put(key, getServer(key));
        }

        removeServer(serverId);
        int remapped = 0;
        for (String key : keyList) {
            String after = getServer(key);
            if (!Objects.equals(before.get(key), after)) {
                remapped++;
            }
        }
        // Restore topology for callers that only wanted a measurement.
        addServer(serverId);
        return (double) remapped / keyList.size();
    }

    public void printRing() {
        System.out.println("Hash Ring (" + ring.size() + " vnodes, "
                + serverVNodes.size() + " servers):");
        for (Map.Entry<Long, String> entry : ring.entrySet()) {
            System.out.println("  " + Long.toUnsignedString(entry.getKey()) + " => " + entry.getValue());
        }
    }

    private long placeVirtualNode(String virtualNodeId, String serverId) {
        long hash = hash(virtualNodeId);
        for (int probe = 0; probe < MAX_COLLISION_PROBES; probe++) {
            long candidate = hash + probe;
            String existing = ring.putIfAbsent(candidate, serverId);
            if (existing == null) {
                return candidate;
            }
            if (existing.equals(serverId)) {
                // Same server already owns this point (shouldn't happen across vnodes often).
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to place virtual node for " + serverId + " after " + MAX_COLLISION_PROBES + " probes");
    }

    private Long clockwiseFrom(long hash) {
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        if (entry != null) {
            return entry.getKey();
        }
        Map.Entry<Long, String> first = ring.firstEntry();
        return first == null ? null : first.getKey();
    }

    private long hash(String key) {
        MessageDigest md = digests.get();
        md.reset();
        byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(digest).getLong();
    }
}
