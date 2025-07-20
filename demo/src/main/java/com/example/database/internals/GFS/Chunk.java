package models;

public class Chunk {
    private final String chunkId;
    private final String data;

    public Chunk(String chunkId, String data) {
        this.chunkId = chunkId;
        this.data = data;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getData() {
        return data;
    }
}
