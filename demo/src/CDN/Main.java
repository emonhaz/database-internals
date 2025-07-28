import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        OriginServer origin = new OriginServer();

        List<EdgeServer> edges = List.of(
            new EdgeServer("edge-1", origin),
            new EdgeServer("edge-2", origin),
            new EdgeServer("edge-3", origin)
        );

        LoadBalancer lb = new LoadBalancer(edges, 100); // with 100 virtual nodes

        // Simulated requests
        String[] paths = {"/index.html", "/image.png", "/script.js", "/index.html", "/image.png"};

        for (String path : paths) {
            System.out.println(lb.handleRequest(path));
            Thread.sleep(1000);
        }

        // Wait for TTL expiry
        Thread.sleep(11_000);
        System.out.println("---- After TTL ----");
        for (String path : paths) {
            System.out.println(lb.handleRequest(path));
        }
    }
}
