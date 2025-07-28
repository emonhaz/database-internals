import java.util.List;

public class LoadBalancer {
    private final ConsistentHashing hashing;

    public LoadBalancer(List<EdgeServer> edgeServers, int virtualNodes) {
        this.hashing = new ConsistentHashing(edgeServers, virtualNodes);
    }

    public String handleRequest(String path) {
        EdgeServer server = hashing.getServer(path);
        return server.serve(path);
    }
}
