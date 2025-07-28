public class Client {
    private final MasterServer master;
    private final ChunkServer chunkServer;

    public Client(MasterServer master, ChunkServer chunkServer) {
        this.master = master;
        this.chunkServer = chunkServer;
    }

    public void writeFile(String filename, String data) {
        final int CHUNK_SIZE = 5;

        for (int i = 0; i < data.length(); i += CHUNK_SIZE) {
            String chunkData = data.substring(i, Math.min(i + CHUNK_SIZE, data.length()));
            String chunkId = master.generateChunkId();
            chunkServer.storeChunk(chunkId, chunkData);
            master.addChunkToFile(filename, chunkId);
        }
    }

    public String readFile(String filename) {
        var metadata = master.getMetadata(filename);
        if (metadata == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String chunkId : metadata.getChunkIds()) {
            sb.append(chunkServer.readChunk(chunkId));
        }
        return sb.toString();
    }
}
