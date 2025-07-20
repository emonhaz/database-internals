import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class BloomFilter {
    private final BitSet bitSet;
    private final int bitSetSize;
    private final int numHashFunctions;

    public BloomFilter(int size, int numHashFunctions) {
        this.bitSetSize = size;
        this.bitSet = new BitSet(size);
        this.numHashFunctions = numHashFunctions;
    }

    public void add(String item) {
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = getHash(item, i);
            bitSet.set(Math.abs(hash % bitSetSize), true);
        }
    }

    public boolean mightContain(String item) {
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = getHash(item, i);
            if (!bitSet.get(Math.abs(hash % bitSetSize))) {
                return false;
            }
        }
        return true;
    }

    private int getHash(String item, int seed) {
        int hash = 0;
        byte[] bytes = (item + seed).getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = (hash * 31 + b) ^ seed;
        }
        return hash;
    }
}
