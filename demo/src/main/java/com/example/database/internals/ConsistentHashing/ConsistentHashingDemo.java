import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Demo: routing, remapping on remove, replica placement, and load distribution. */
public final class ConsistentHashingDemo {
    private ConsistentHashingDemo() {
    }

    public static void main(String[] args) {
        ConsistentHashingLoadBalancer lb = new ConsistentHashingLoadBalancer(100);
        lb.addServer("ServerA");
        lb.addServer("ServerB");
        lb.addServer("ServerC");

        String[] sampleKeys = {"apple", "banana", "carrot", "dog", "elephant"};
        System.out.println("--- Initial routing ---");
        for (String key : sampleKeys) {
            System.out.printf("Key '%s' => %s (replicas=%s)%n",
                    key, lb.getServer(key), lb.getReplicas(key, 2));
        }

        List<String> manyKeys = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            manyKeys.add("user-" + i);
        }

        System.out.println("\n--- Load distribution (10k keys) ---");
        printDistribution(lb.loadDistribution(manyKeys));

        System.out.println("\n--- Removing ServerB ---");
        Map<String, String> before = new java.util.LinkedHashMap<>();
        for (String key : sampleKeys) {
            before.put(key, lb.getServer(key));
        }
        lb.removeServer("ServerB");
        for (String key : sampleKeys) {
            String after = lb.getServer(key);
            String changed = java.util.Objects.equals(before.get(key), after) ? "" : " (remapped)";
            System.out.printf("Key '%s' => %s%s%n", key, after, changed);
        }

        System.out.println("\n--- Load distribution after remove ---");
        printDistribution(lb.loadDistribution(manyKeys));
    }

    private static void printDistribution(Map<String, Integer> counts) {
        int total = 0;
        for (int c : counts.values()) {
            total += c;
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double pct = total == 0 ? 0.0 : 100.0 * e.getValue() / total;
            System.out.printf("  %s: %d (%.1f%%)%n", e.getKey(), e.getValue(), pct);
        }
    }
}
