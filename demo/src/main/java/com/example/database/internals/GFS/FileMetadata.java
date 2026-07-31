import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Master metadata for one file: ordered chunk IDs. */
public final class FileMetadata {
    private final String filename;
    private final CopyOnWriteArrayList<String> chunkIds = new CopyOnWriteArrayList<>();
    private volatile long version;

    public FileMetadata(String filename) {
        this.filename = Objects.requireNonNull(filename, "filename");
    }

    public String getFilename() {
        return filename;
    }

    public List<String> getChunkIds() {
        return Collections.unmodifiableList(new ArrayList<>(chunkIds));
    }

    public void addChunk(String chunkId) {
        Objects.requireNonNull(chunkId, "chunkId");
        chunkIds.add(chunkId);
        version++;
    }

    public int chunkCount() {
        return chunkIds.size();
    }

    public long getVersion() {
        return version;
    }
}
