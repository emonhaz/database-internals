import java.io.*;
import java.util.Map;

public class SSTable {
    private final File file;

    public SSTable(File file, SortedMap<String,String> data) throws IOException {
        this.file = file;
        try (var out = new DataOutputStream(new FileOutputStream(file))) {
            for (var e : data.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue());
            }
        }
    }

    public String get(String key) throws IOException {
        try (var in = new DataInputStream(new FileInputStream(file))) {
            while (in.available() > 0) {
                String k = in.readUTF();
                String v = in.readUTF();
                if (k.equals(key)) return v;
            }
        }
        return null;
    }

    public File getFile() {
        return file;
    }
}
