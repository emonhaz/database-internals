import java.util.*;
import java.security.MessageDigest;

public class ConsistentHashing {
    private final SortedMap<Integer, EdgeServer> circle = new TreeMap<>();
    private final int virtualNodes;

    public ConsistentHashing(List<EdgeServer> nodes, int virtualNodes) {
        this.virtualNodes = virtualNodes;
        for (EdgeServer node : nodes) {
            addNode(node);
        }
    }

    private int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            return Math.abs(Arrays.hashCode(digest));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addNode(EdgeServer node) {
        for (int i = 0; i < virtualNodes; i++) {
            int hash = hash(node.getId() + "#" + i);
            circle.put(hash, node);
        }
    }

    public EdgeServer getServer(String key) {
        if (circle.isEmpty()) return null;
        int hash = hash(key);
        SortedMap<Integer, EdgeServer> tail = circle.tailMap(hash);
        return tail.isEmpty() ? circle.get(circle.firstKey()) : tail.get(tail.firstKey());
    }
}
