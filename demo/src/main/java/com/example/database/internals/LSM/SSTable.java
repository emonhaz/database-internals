import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable on-disk sorted string table with an in-memory Bloom filter and sparse index
 * so point lookups avoid a full file scan on obvious misses.
 *
 * <p>File layout (v1):
 * <pre>
 * magic, version, entryCount
 * repeated: keyUTF, tombstoneFlag, valueUTF (empty when tombstone)
 * </pre>
 */
public final class SSTable {
    private static final int MAGIC = 0x4C534D31; // LSM1
    private static final int VERSION = 1;
    private static final int INDEX_INTERVAL = 16;
    private static final double BLOOM_FPP = 0.01;

    private final File file;
    private final BloomFilter bloom;
    private final TreeMap<String, Long> sparseIndex = new TreeMap<>();
    private final int entryCount;

    public SSTable(File file, SortedMap<String, String> data) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(data, "data");
        this.file = file;
        this.entryCount = data.size();
        this.bloom = data.isEmpty()
                ? new BloomFilter(1, BLOOM_FPP)
                : new BloomFilter(Math.max(1, data.size()), BLOOM_FPP);

        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (RandomAccessFile raf = new RandomAccessFile(temp, "rw")) {
            raf.setLength(0);
            raf.writeInt(MAGIC);
            raf.writeInt(VERSION);
            raf.writeInt(data.size());

            int i = 0;
            for (Map.Entry<String, String> e : data.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                boolean tombstone = Memtable.TOMBSTONE.equals(value);

                bloom.add(key);
                if (i % INDEX_INTERVAL == 0) {
                    sparseIndex.put(key, raf.getFilePointer());
                }

                raf.writeUTF(key);
                raf.writeBoolean(tombstone);
                raf.writeUTF(tombstone ? "" : (value == null ? "" : value));
                i++;
            }
        }

        if (!temp.renameTo(file) && !replaceFile(temp, file)) {
            throw new IOException("Failed to publish SSTable " + file.getAbsolutePath());
        }
    }

    /** Re-open an existing SSTable and rebuild bloom + sparse index. */
    public static SSTable open(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!file.isFile()) {
            throw new IOException("Not an SSTable file: " + file);
        }

        BloomFilter bloom;
        TreeMap<String, Long> index = new TreeMap<>();
        int count;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int magic = raf.readInt();
            int version = raf.readInt();
            if (magic != MAGIC || version != VERSION) {
                throw new IOException("Unsupported SSTable format magic=" + magic + " version=" + version);
            }
            count = raf.readInt();
            bloom = new BloomFilter(Math.max(1, count), BLOOM_FPP);

            for (int i = 0; i < count; i++) {
                long pos = raf.getFilePointer();
                String key = raf.readUTF();
                raf.readBoolean();
                raf.readUTF();
                bloom.add(key);
                if (i % INDEX_INTERVAL == 0) {
                    index.put(key, pos);
                }
            }
        }

        return new SSTable(file, bloom, index, count);
    }

    private SSTable(File file, BloomFilter bloom, TreeMap<String, Long> sparseIndex, int entryCount) {
        this.file = file;
        this.bloom = bloom;
        this.sparseIndex.putAll(sparseIndex);
        this.entryCount = entryCount;
    }

    public String get(String key) throws IOException {
        Objects.requireNonNull(key, "key");
        if (entryCount == 0 || !bloom.mightContain(key)) {
            return null;
        }

        long start = 12L;
        Map.Entry<String, Long> floor = sparseIndex.floorEntry(key);
        if (floor != null) {
            start = floor.getValue();
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            while (raf.getFilePointer() < raf.length()) {
                String k = raf.readUTF();
                boolean tombstone = raf.readBoolean();
                String v = raf.readUTF();
                int cmp = k.compareTo(key);
                if (cmp == 0) {
                    return tombstone ? Memtable.TOMBSTONE : v;
                }
                if (cmp > 0) {
                    return null;
                }
            }
        } catch (EOFException eof) {
            return null;
        }
        return null;
    }

    public SortedMap<String, String> readAll() throws IOException {
        TreeMap<String, String> data = new TreeMap<>();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            in.readInt();
            in.readInt();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String key = in.readUTF();
                boolean tombstone = in.readBoolean();
                String value = in.readUTF();
                data.put(key, tombstone ? Memtable.TOMBSTONE : value);
            }
        }
        return Collections.unmodifiableSortedMap(data);
    }

    public File getFile() {
        return file;
    }

    public int entryCount() {
        return entryCount;
    }

    public void deleteFile() {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static boolean replaceFile(File temp, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            return false;
        }
        return temp.renameTo(target);
    }
}
