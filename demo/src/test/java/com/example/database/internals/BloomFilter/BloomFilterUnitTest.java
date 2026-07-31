import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for BloomFilter guarantees and sizing helpers. */
public class BloomFilterUnitTest {

    private static final int SMALL_N = 1000;
    private static final int MEDIUM_N = 5000;
    private static final int PROBE_COUNT = 10000;
    private static final double TARGET_FPP = 0.01;
    private static final double FPP_SLACK = 3.0;
    private static final int RANDOM_SEED = 42;
    private static final int RANDOM_BOUND = 1_000_000;
    private static final int DEFINITE_MISS_MIN = 900;

    @Test
    public void neverReturnsFalseNegativeForInsertedItems() {
        BloomFilter filter = new BloomFilter(SMALL_N, TARGET_FPP);
        Set<String> inserted = new HashSet<>();

        for (int i = 0; i < SMALL_N; i++) {
            String item = "key-" + i;
            inserted.add(item);
            filter.add(item);
        }

        for (String item : inserted) {
            assertTrue(filter.mightContain(item), "Bloom filters must never false-negative");
        }
        assertEquals(SMALL_N, filter.insertedCount());
    }

    @Test
    public void measuredFalsePositiveRateIsNearTarget() {
        BloomFilter filter = new BloomFilter(MEDIUM_N, TARGET_FPP);

        Set<String> inserted = new HashSet<>();
        for (int i = 0; i < MEDIUM_N; i++) {
            String item = "ins-" + i;
            inserted.add(item);
            filter.add(item);
        }

        Random random = new Random(RANDOM_SEED);
        int falsePositives = 0;
        int tested = 0;

        while (tested < PROBE_COUNT) {
            String candidate = "probe-" + random.nextInt(RANDOM_BOUND);
            if (inserted.contains(candidate)) {
                continue;
            }
            if (filter.mightContain(candidate)) {
                falsePositives++;
            }
            tested++;
        }

        double measured = (double) falsePositives / tested;
        // Allow headroom: hashing / load can push above the theoretical target.
        assertTrue(measured < TARGET_FPP * FPP_SLACK,
                String.format("measured FPP %.4f should be roughly near target %.4f", measured, TARGET_FPP));
        assertTrue(filter.estimatedFpp() > 0.0);
        assertTrue(filter.bitSize() > 0);
        assertTrue(filter.hashFunctionCount() >= 1);
    }

    @Test
    public void optimalSizingMatchesExpectedFormulas() {
        long n = PROBE_COUNT;
        double p = TARGET_FPP;

        int m = BloomFilter.optimalBitSize(n, p);
        int k = BloomFilter.optimalHashCount(n, m);

        int expectedM = (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        int expectedK = Math.max(1, (int) Math.round((double) m / n * Math.log(2)));

        assertEquals(expectedM, m);
        assertEquals(expectedK, k);

        BloomFilter filter = new BloomFilter((int) n, p);
        assertEquals(expectedM, filter.bitSize());
        assertEquals(expectedK, filter.hashFunctionCount());
        assertEquals(n, filter.expectedInsertions());
    }

    @Test
    public void rejectsInvalidConstructorArgsAndNullItems() {
        int validN = 100;
        int validK = 4;

        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, TARGET_FPP));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(validN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(validN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, validK, validN));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(validN, 0, validN));

        BloomFilter filter = new BloomFilter(validN, TARGET_FPP);
        assertThrows(NullPointerException.class, () -> filter.add(null));
        assertThrows(NullPointerException.class, () -> filter.mightContain(null));
    }

    @Test
    public void falseMeansDefinitelyAbsentAcrossManyProbes() {
        BloomFilter filter = new BloomFilter(PROBE_COUNT, TARGET_FPP);
        filter.add("present");
        assertTrue(filter.mightContain("present"));

        // Any mightContain(false) result is a hard "not present" — never a false negative.
        int definiteMisses = 0;
        for (int i = 0; i < SMALL_N; i++) {
            if (!filter.mightContain("absent-" + i)) {
                definiteMisses++;
            }
        }
        assertTrue(definiteMisses > DEFINITE_MISS_MIN, "most absent keys should be rejected");
    }
}
