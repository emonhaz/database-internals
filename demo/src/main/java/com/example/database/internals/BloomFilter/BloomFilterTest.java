import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class BloomFilterTest {
    public static void main(String[] args) {
        int numItems = 10000;
        int numTestItems = 10000;

        int[] sizes = {1024, 4096, 8192, 16384, 32768};
        int[] hashCounts = {2, 4, 6, 8, 10};

        System.out.println("----- False Positive Rate vs Size -----");
        for (int size : sizes) {
            BloomFilter filter = new BloomFilter(size, 4);
            runExperiment(filter, numItems, numTestItems, "Size=" + size);
        }

        System.out.println("\n----- False Positive Rate vs Hash Functions -----");
        for (int k : hashCounts) {
            BloomFilter filter = new BloomFilter(8192, k);
            runExperiment(filter, numItems, numTestItems, "HashCount=" + k);
        }
    }

    private static void runExperiment(BloomFilter filter, int insertCount, int testCount, String label) {
        Set<String> inserted = new HashSet<>();
        Random random = new Random();

        while (inserted.size() < insertCount) {
            String s = "item" + random.nextInt(1_000_000);
            inserted.add(s);
            filter.add(s);
        }

        int falsePositives = 0;
        int totalTests = 0;

        for (int i = 0; i < testCount; i++) {
            String test = "test" + random.nextInt(1_000_000);
            if (inserted.contains(test)) continue;

            if (filter.mightContain(test)) falsePositives++;
            totalTests++;
        }

        double rate = 100.0 * falsePositives / totalTests;
        System.out.printf("%s -> False Positive Rate: %.2f%% (%d/%d)\n", label, rate, falsePositives, totalTests);
    }
}
