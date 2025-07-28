public class GFSRunner {
    public static void main(String[] args) {
        MasterServer master = new MasterServer();
        ChunkServer chunkServer = new ChunkServer();
        Client client = new Client(master, chunkServer);

        String file = "demo.txt";
        String data = "This is a simplified GFS in Java";

        client.writeFile(file, data);
        String readBack = client.readFile(file);

        System.out.println("Read from GFS: " + readBack);
    }
}
