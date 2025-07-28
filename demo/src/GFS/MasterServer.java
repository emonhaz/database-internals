import models.FileMetadata;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class MasterServer {
    private final ConcurrentHashMap<String, FileMetadata> fileTable = new ConcurrentHashMap<>();

    public void createFile(String filename) {
        fileTable.put(filename, new FileMetadata(filename));
        System.out.println("Created file: " + filename);
    }

    public void addChunkToFile(String filename, String chunkId) {
        if (!fileTable.containsKey(filename)) createFile(filename);
        fileTable.get(filename).addChunk(chunkId);
    }

    public FileMetadata getMetadata(String filename) {
        return fileTable.get(filename);
    }

    public String generateChunkId() {
        return UUID.randomUUID().toString();
    }
}
