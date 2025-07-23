public class ConsistentHashingLoadBalancer {
    private final int virtualNodesPerServer;
    private final SortedMap<Integer, String> ring = new TreeMap<>();
    private final MessageDigest md;

    public ConsistentHashingLoadBalancer(int virtualNodesPerServer) throws NoSuchAlgorithmException {
        this.virtualNodesPerServer = virtualNodesPerServer;
        this.md = MessageDigest.getInstance("MD5");
    }

    private int hash(String key) {
        byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.wrap(digest);
        return Math.abs(buffer.getInt()); // use only first 4 bytes
    }

    public void addServer(String serverId) {
        for (int i = 0; i < virtualNodesPerServer; i++) {
            String virtualNodeId = serverId + "#" + i;
            ring.put(hash(virtualNodeId), serverId);
        }
        System.out.println("Added server: " + serverId);
    }

    public void removeServer(String serverId) {
        for (int i = 0; i < virtualNodesPerServer; i++) {
            String virtualNodeId = serverId + "#" + i;
            ring.remove(hash(virtualNodeId));
        }
        System.out.println("Removed server: " + serverId);
    }

    public String getServer(String key) {
        if (ring.isEmpty()) return null;

        int hash = hash(key);
        SortedMap<Integer, String> tail = ring.tailMap(hash);
        Integer nodeHash = !tail.isEmpty() ? tail.firstKey() : ring.firstKey();
        return ring.get(nodeHash);
    }

    public void printRing() {
        System.out.println("Hash Ring:");
        for (Map.Entry<Integer, String> entry : ring.entrySet()) {
            System.out.println("  " + entry.getKey() + " => " + entry.getValue());
        }
    }
}
