import models.Chunk;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkServer {
    private final ConcurrentHashMap<String, Chunk> chunkStore = new ConcurrentHashMap<>();

    public void storeChunk(String chunkId, String data) {
        chunkStore.put(chunkId, new Chunk(chunkId, data));
        System.out.println("Stored chunk: " + chunkId);
    }

    public String readChunk(String chunkId) {
        return chunkStore.getOrDefault(chunkId, new Chunk(chunkId, "")).getData();
    }
}
