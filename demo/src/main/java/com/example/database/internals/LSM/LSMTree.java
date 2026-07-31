import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny LSM-Tree: memtable + immutable SSTables, newest-wins reads, tombstones, and compaction.
 */
public final class LSMTree implements AutoCloseable {
    private static final Pattern SST_NAME = Pattern.compile("sst-(\\d+)\\.dat");

    private final Memtable memtable;
    private final File dir;
    private final CopyOnWriteArrayList<SSTable> sstables = new CopyOnWriteArrayList<>();
    private final AtomicInteger sstableCounter = new AtomicInteger();
    private final int compactionThreshold;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean closed;

    public LSMTree(String dirPath, int memThreshold) throws IOException {
        this(dirPath, memThreshold, 4);
    }

    public LSMTree(String dirPath, int memThreshold, int compactionThreshold) throws IOException {
        Objects.requireNonNull(dirPath, "dirPath");
        if (compactionThreshold < 2) {
            throw new IllegalArgumentException("compactionThreshold must be >= 2");
        }
        this.memtable = new Memtable(memThreshold);
        this.dir = new File(dirPath);
        this.compactionThreshold = compactionThreshold;
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create data dir: " + dir.getAbsolutePath());
        }
        recoverExistingTables();
    }

    public void put(String key, String value) throws IOException {
        ensureOpen();
        lock.writeLock().lock();
        try {
            memtable.put(key, value);
            maybeFlushLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(String key) throws IOException {
        ensureOpen();
        lock.writeLock().lock();
        try {
            memtable.delete(key);
            maybeFlushLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String key) throws IOException {
        ensureOpen();
        Objects.requireNonNull(key, "key");

        lock.readLock().lock();
        try {
            String fromMem = memtable.get(key);
            if (fromMem != null) {
                return Memtable.TOMBSTONE.equals(fromMem) ? null : fromMem;
            }

            // Newest SSTable first.
            for (int i = sstables.size() - 1; i >= 0; i--) {
                String val = sstables.get(i).get(key);
                if (val != null) {
                    return Memtable.TOMBSTONE.equals(val) ? null : val;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void flush() throws IOException {
        ensureOpen();
        lock.writeLock().lock();
        try {
            flushLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void compact() throws IOException {
        ensureOpen();
        lock.writeLock().lock();
        try {
            compactLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int sstableCount() {
        return sstables.size();
    }

    public int memtableSize() {
        return memtable.size();
    }

    public File dataDir() {
        return dir;
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            flushLocked();
            closed = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void maybeFlushLocked() throws IOException {
        if (memtable.shouldFlush()) {
            flushLocked();
        }
        if (sstables.size() >= compactionThreshold) {
            compactLocked();
        }
    }

    private void flushLocked() throws IOException {
        if (memtable.size() == 0) {
            return;
        }
        SortedMap<String, String> data = memtable.flush();
        if (data.isEmpty()) {
            return;
        }
        int id = sstableCounter.getAndIncrement();
        File sstFile = new File(dir, "sst-" + id + ".dat");
        SSTable sst = new SSTable(sstFile, data);
        sstables.add(sst);
    }

    private void compactLocked() throws IOException {
        if (sstables.size() < 2) {
            return;
        }
        List<SSTable> snapshot = new ArrayList<>(sstables);
        TreeMap<String, String> merged = new TreeMap<>();
        // Oldest -> newest so newer values overwrite.
        for (SSTable sst : snapshot) {
            merged.putAll(sst.readAll());
        }
        // Drop tombstones in the compacted output (key is gone).
        TreeMap<String, String> live = new TreeMap<>();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            if (!Memtable.TOMBSTONE.equals(e.getValue())) {
                live.put(e.getKey(), e.getValue());
            }
        }

        int id = sstableCounter.getAndIncrement();
        File sstFile = new File(dir, "sst-" + id + ".dat");
        SSTable compacted = new SSTable(sstFile, live);

        sstables.clear();
        sstables.add(compacted);
        for (SSTable old : snapshot) {
            old.deleteFile();
        }
    }

    private void recoverExistingTables() throws IOException {
        File[] files = dir.listFiles((d, name) -> SST_NAME.matcher(name).matches());
        if (files == null || files.length == 0) {
            return;
        }
        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        Collections.sort(sorted, Comparator.comparingInt(f -> sstId(f.getName())));

        int maxId = -1;
        for (File f : sorted) {
            sstables.add(SSTable.open(f));
            maxId = Math.max(maxId, sstId(f.getName()));
        }
        sstableCounter.set(maxId + 1);
    }

    private static int sstId(String name) {
        Matcher m = SST_NAME.matcher(name);
        if (!m.matches()) {
            return -1;
        }
        return Integer.parseInt(m.group(1));
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("LSMTree is closed");
        }
    }
}
