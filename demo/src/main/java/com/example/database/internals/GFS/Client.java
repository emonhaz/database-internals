import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client library: splits files into chunks, writes all replicas, reads with failover.
 */
public final class Client {
    private final MasterServer master;
    private final int chunkSize;
    private final int replicationFactor;

    public Client(MasterServer master) {
        this(master, 8, master.getDefaultReplicationFactor());
    }

    public Client(MasterServer master, int chunkSize, int replicationFactor) {
        this.master = Objects.requireNonNull(master, "master");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be >= 1");
        }
        this.chunkSize = chunkSize;
        this.replicationFactor = replicationFactor;
    }

    public void writeFile(String filename, String data) {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(data, "data");
        writeFile(filename, data.getBytes(StandardCharsets.UTF_8));
    }

    public void writeFile(String filename, byte[] data) {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(data, "data");

        // Replace existing content for demo simplicity.
        master.deleteFile(filename);
        master.createFile(filename);

        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, data.length);
            byte[] chunkData = new byte[end - i];
            System.arraycopy(data, i, chunkData, 0, chunkData.length);

            String chunkId = master.generateChunkId();
            List<ChunkServer> replicas = master.allocateChunk(filename, chunkId, replicationFactor);
            List<RuntimeException> errors = new ArrayList<>();
            int stored = 0;
            for (ChunkServer replica : replicas) {
                try {
                    replica.storeChunk(chunkId, chunkData);
                    stored++;
                } catch (RuntimeException e) {
                    errors.add(e);
                }
            }
            if (stored == 0) {
                throw new IllegalStateException(
                        "Failed to store chunk " + chunkId + " on any replica",
                        errors.isEmpty() ? null : errors.get(0));
            }
        }
    }

    public void appendFile(String filename, String data) {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(data, "data");
        appendFile(filename, data.getBytes(StandardCharsets.UTF_8));
    }

    public void appendFile(String filename, byte[] data) {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(data, "data");
        if (master.getMetadata(filename) == null) {
            master.createFile(filename);
        }

        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, data.length);
            byte[] chunkData = new byte[end - i];
            System.arraycopy(data, i, chunkData, 0, chunkData.length);
            String chunkId = master.generateChunkId();
            List<ChunkServer> replicas = master.allocateChunk(filename, chunkId, replicationFactor);
            int stored = 0;
            for (ChunkServer replica : replicas) {
                try {
                    replica.storeChunk(chunkId, chunkData);
                    stored++;
                } catch (RuntimeException ignored) {
                    // try other replicas
                }
            }
            if (stored == 0) {
                throw new IllegalStateException("Failed to append chunk " + chunkId);
            }
        }
    }

    public String readFile(String filename) {
        byte[] bytes = readFileBytes(filename);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] readFileBytes(String filename) {
        Objects.requireNonNull(filename, "filename");
        FileMetadata metadata = master.getMetadata(filename);
        if (metadata == null) {
            return null;
        }

        List<byte[]> parts = new ArrayList<>();
        int total = 0;
        for (String chunkId : metadata.getChunkIds()) {
            Chunk chunk = readChunkWithFailover(chunkId);
            if (chunk == null) {
                throw new IllegalStateException("Chunk " + chunkId + " unavailable on all replicas");
            }
            byte[] data = chunk.getData();
            parts.add(data);
            total += data.length;
        }

        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private Chunk readChunkWithFailover(String chunkId) {
        List<ChunkServer> replicas = master.getChunkReplicas(chunkId);
        for (ChunkServer replica : replicas) {
            try {
                Chunk chunk = replica.readChunk(chunkId);
                if (chunk != null) {
                    return chunk;
                }
            } catch (RuntimeException ignored) {
                // try next replica
            }
        }
        return null;
    }
}
