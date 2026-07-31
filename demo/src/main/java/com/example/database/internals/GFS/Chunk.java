import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable chunk payload with an integrity checksum. */
public final class Chunk {
    private final String chunkId;
    private final byte[] data;
    private final String checksum;

    public Chunk(String chunkId, byte[] data) {
        this.chunkId = Objects.requireNonNull(chunkId, "chunkId");
        this.data = Objects.requireNonNull(data, "data").clone();
        this.checksum = sha256Hex(this.data);
    }

    public Chunk(String chunkId, String data) {
        this(chunkId, Objects.requireNonNull(data, "data").getBytes(StandardCharsets.UTF_8));
    }

    public String getChunkId() {
        return chunkId;
    }

    public byte[] getData() {
        return data.clone();
    }

    public String getDataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    public String getChecksum() {
        return checksum;
    }

    public int size() {
        return data.length;
    }

    public boolean matchesChecksum() {
        return checksum.equals(sha256Hex(data));
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
