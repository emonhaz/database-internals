package models;

import java.util.ArrayList;
import java.util.List;

public class FileMetadata {
    private final String filename;
    private final List<String> chunkIds;

    public FileMetadata(String filename) {
        this.filename = filename;
        this.chunkIds = new ArrayList<>();
    }

    public String getFilename() {
        return filename;
    }

    public List<String> getChunkIds() {
        return chunkIds;
    }

    public void addChunk(String chunkId) {
        chunkIds.add(chunkId);
    }
}
