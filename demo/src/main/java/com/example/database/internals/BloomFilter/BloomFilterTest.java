import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Manual experiment harness (run main) for false-positive behavior.
 * For assertions, see BloomFilterUnitTest under src/test.
 */
public class BloomFilterTest {
    public static void main(String[] args) {
        int numItems = 10_000;
        int numTestItems = 10_000;

        int[] sizes = {1024, 4096, 8192, 16384, 32768};
        int[] hashCounts = {2, 4, 6, 8, 10};

        System.out.println("----- False Positive Rate vs Size (k=4) -----");
        for (int size : sizes) {
            BloomFilter filter = new BloomFilter(size, 4, numItems);
            runExperiment(filter, numItems, numTestItems, "Size=" + size);
        }

        System.out.println("\n----- False Positive Rate vs Hash Functions (m=8192) -----");
        for (int k : hashCounts) {
            BloomFilter filter = new BloomFilter(8192, k, numItems);
            runExperiment(filter, numItems, numTestItems, "HashCount=" + k);
        }

        System.out.println("\n----- Sized from n + target FPP -----");
        double[] targets = {0.1, 0.01, 0.001};
        for (double p : targets) {
            BloomFilter filter = new BloomFilter(numItems, p);
            String label = String.format(
                    "n=%d p=%.3f -> m=%d k=%d",
                    numItems, p, filter.bitSize(), filter.hashFunctionCount());
            runExperiment(filter, numItems, numTestItems, label);
        }
    }

    private static void runExperiment(BloomFilter filter, int insertCount, int testCount, String label) {
        Set<String> inserted = new HashSet<>();
        Random random = new Random(42);

        while (inserted.size() < insertCount) {
            String s = "item" + random.nextInt(1_000_000);
            if (inserted.add(s)) {
                filter.add(s);
            }
        }

        int falseNegatives = 0;
        for (String item : inserted) {
            if (!filter.mightContain(item)) {
                falseNegatives++;
            }
        }

        int falsePositives = 0;
        int totalTests = 0;

        for (int i = 0; i < testCount; i++) {
            String test = "test" + random.nextInt(1_000_000);
            if (inserted.contains(test)) {
                continue;
            }

            if (filter.mightContain(test)) {
                falsePositives++;
            }
            totalTests++;
        }

        double rate = 100.0 * falsePositives / totalTests;
        System.out.printf(
                "%s -> FPP: %.2f%% (%d/%d), estimatedFpp=%.4f, falseNegatives=%d%n",
                label, rate, falsePositives, totalTests, filter.estimatedFpp(), falseNegatives);
    }
}
