import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Objects;

/**
 * Probabilistic set membership:
 * - false negatives: never
 * - false positives: possible
 *
 * Used in DBs (e.g. LSM SST filters) to skip disk reads for absent keys.
 */
public final class BloomFilter {
    private final BitSet bitSet;
    private final int bitSetSize;       // m
    private final int numHashFunctions; // k
    private final int expectedInsertions;
    private long insertedCount;

    /** Build from expected cardinality n and target FPP p. */
    public BloomFilter(int expectedInsertions, double falsePositiveRate) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions must be > 0");
        }
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0) {
            throw new IllegalArgumentException("falsePositiveRate must be in (0, 1)");
        }

        this.expectedInsertions = expectedInsertions;
        this.bitSetSize = optimalBitSize(expectedInsertions, falsePositiveRate);
        this.numHashFunctions = optimalHashCount(expectedInsertions, bitSetSize);
        this.bitSet = new BitSet(bitSetSize);
    }

    /** Low-level constructor (useful for experiments / tests). */
    public BloomFilter(int bitSetSize, int numHashFunctions, int expectedInsertions) {
        if (bitSetSize <= 0 || numHashFunctions <= 0) {
            throw new IllegalArgumentException("bitSetSize and numHashFunctions must be > 0");
        }
        this.bitSetSize = bitSetSize;
        this.numHashFunctions = numHashFunctions;
        this.expectedInsertions = expectedInsertions;
        this.bitSet = new BitSet(bitSetSize);
    }

    public void add(String item) {
        Objects.requireNonNull(item, "item");
        long[] hashes = doubleHash(item);
        long h1 = hashes[0];
        long h2 = hashes[1];

        for (int i = 0; i < numHashFunctions; i++) {
            int index = (int) Math.floorMod(h1 + (long) i * h2, bitSetSize);
            bitSet.set(index);
        }
        insertedCount++;
    }

    public boolean mightContain(String item) {
        Objects.requireNonNull(item, "item");
        long[] hashes = doubleHash(item);
        long h1 = hashes[0];
        long h2 = hashes[1];

        for (int i = 0; i < numHashFunctions; i++) {
            int index = (int) Math.floorMod(h1 + (long) i * h2, bitSetSize);
            if (!bitSet.get(index)) {
                return false; // definitely not present
            }
        }
        return true; // probably present
    }

    /** Approximate FPP assuming uniform hashes: (1 - e^(-kn/m))^k */
    public double estimatedFpp() {
        double bitsSetFraction = (double) bitSet.cardinality() / bitSetSize;
        return Math.pow(bitsSetFraction, numHashFunctions);
        // Or theoretical: Math.pow(1 - Math.exp(-(double) numHashFunctions * insertedCount / bitSetSize), numHashFunctions);
    }

    public int bitSize() { return bitSetSize; }
    public int hashFunctionCount() { return numHashFunctions; }
    public long insertedCount() { return insertedCount; }
    public int expectedInsertions() { return expectedInsertions; }

    // m = -n * ln(p) / (ln2)^2
    static int optimalBitSize(long n, double p) {
        return (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    // k = (m/n) * ln2
    static int optimalHashCount(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    /** Two independent 64-bit mixes → k indices via double hashing. */
    private static long[] doubleHash(String item) {
        byte[] data = item.getBytes(StandardCharsets.UTF_8);
        long h1 = murmur64(data, 0x9747b28cL);
        long h2 = murmur64(data, 0x5bd1e995L);
        if ((h2 & 1L) == 0) {
            h2 |= 1L; // ensure odd so i*h2 covers the ring better
        }
        return new long[]{h1, h2};
    }

    // Compact MurmurHash3 64-bit style mix (good enough for a demo; cite Guava/xxHash in interview)
    private static long murmur64(byte[] data, long seed) {
        final long m = 0xc6a4a7935bd1e995L;
        final int r = 47;
        long h = seed ^ (data.length * m);

        int length8 = data.length / 8;
        for (int i = 0; i < length8; i++) {
            int i8 = i * 8;
            long k = ((long) data[i8] & 0xff)
                    | (((long) data[i8 + 1] & 0xff) << 8)
                    | (((long) data[i8 + 2] & 0xff) << 16)
                    | (((long) data[i8 + 3] & 0xff) << 24)
                    | (((long) data[i8 + 4] & 0xff) << 32)
                    | (((long) data[i8 + 5] & 0xff) << 40)
                    | (((long) data[i8 + 6] & 0xff) << 48)
                    | (((long) data[i8 + 7] & 0xff) << 56);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h ^= k;
            h *= m;
        }

        int remaining = data.length % 8;
        if (remaining > 0) {
            long k = 0;
            int offset = length8 * 8;
            switch (remaining) {
                case 7: k ^= ((long) data[offset + 6] & 0xff) << 48;
                case 6: k ^= ((long) data[offset + 5] & 0xff) << 40;
                case 5: k ^= ((long) data[offset + 4] & 0xff) << 32;
                case 4: k ^= ((long) data[offset + 3] & 0xff) << 24;
                case 3: k ^= ((long) data[offset + 2] & 0xff) << 16;
                case 2: k ^= ((long) data[offset + 1] & 0xff) << 8;
                case 1: k ^= ((long) data[offset] & 0xff);
            }
            k *= m;
            k ^= k >>> r;
            k *= m;
            h ^= k;
            h *= m;
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;
        return h;
    }
}
