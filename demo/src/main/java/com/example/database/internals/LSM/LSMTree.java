import java.io.*;
import java.util.*;

public class LSMTree {
    private final Memtable memtable;
    private final File dir;
    private final List<SSTable> sstables = new ArrayList<>();
    private int sstableCounter = 0;

    public LSMTree(String dirPath, int memThreshold) throws IOException {
        this.memtable = new Memtable(memThreshold);
        this.dir = new File(dirPath);
        dir.mkdirs();
    }

    public void put(String key, String value) throws IOException {
        memtable.put(key, value);
        if (memtable.shouldFlush()) {
            flushToDisk();
        }
    }

    public String get(String key) throws IOException {
        String val = memtable.get(key);
        if (val != null) return val;
        for (int i = sstables.size() - 1; i >= 0; i--) {
            val = sstables.get(i).get(key);
            if (val != null) return val;
        }
        return null;
    }

    private void flushToDisk() throws IOException {
        var data = memtable.flush();
        File sstFile = new File(dir, "sst-" + (sstableCounter++) + ".dat");
        var sst = new SSTable(sstFile, data);
        sstables.add(sst);
        System.out.println("Flushed to SSTable: " + sstFile.getName() + " (" + data.size() + " entries)");
    }
}
