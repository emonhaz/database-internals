import java.util.HashMap;
import java.util.Map;

public class OriginServer {
    private final Map<String, String> store = new HashMap<>();

    public OriginServer() {
        store.put("/index.html", "<html>Hello from origin!</html>");
        store.put("/image.png", "BinaryImageContent");
        store.put("/script.js", "console.log('From origin');");
    }

    public String fetch(String path) {
        System.out.println("⚙️ Fetching from origin: " + path);
        return store.getOrDefault(path, "404 Not Found");
    }
}
