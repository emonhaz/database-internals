/**
 * Demo: 3 chunkservers, replication factor 2, write/read with one server marked dead.
 */
public final class GFSRunner {
    private GFSRunner() {
    }

    public static void main(String[] args) {
        MasterServer master = new MasterServer(2, 60_000L);
        ChunkServer cs1 = new ChunkServer("cs-1");
        ChunkServer cs2 = new ChunkServer("cs-2");
        ChunkServer cs3 = new ChunkServer("cs-3");
        master.registerChunkServer(cs1);
        master.registerChunkServer(cs2);
        master.registerChunkServer(cs3);

        Client client = new Client(master, 5, 2);
        String file = "demo.txt";
        String data = "This is a simplified GFS in Java";

        client.writeFile(file, data);
        System.out.println("Wrote file with replication. chunks="
                + master.getMetadata(file).chunkCount()
                + " healthyServers=" + master.healthyServerCount());

        String readBack = client.readFile(file);
        System.out.println("Read from GFS: " + readBack);

        // Simulate chunkserver failure; reads should still succeed via replicas.
        cs1.markDead();
        System.out.println("Marked cs-1 dead. healthyServers=" + master.healthyServerCount());
        String afterFailure = client.readFile(file);
        System.out.println("Read after cs-1 failure: " + afterFailure);

        client.appendFile(file, "!");
        System.out.println("After append: " + client.readFile(file));
    }
}
