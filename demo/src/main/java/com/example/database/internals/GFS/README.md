# GFS-style distributed file store (in-process demo)

Educational model of Google File System ideas:

- **Master** keeps the namespace and chunk → replica map (not file bytes)
- **Chunkservers** store immutable chunk payloads with checksums
- **Client** splits writes, pipes data to replicas, reads with failover

This is all in one JVM (no RPC). Useful for understanding control plane vs data plane.

## Layout

| File | Role |
|------|------|
| `MasterServer.java` | Namespace, placement, heartbeats |
| `ChunkServer.java` | Chunk store + liveness / capacity |
| `Client.java` | Chunked write / append / read |
| `Chunk.java` / `FileMetadata.java` | Models |
| `GFSRunner.java` | Demo main |

## Run

```bash
javac -d target/classes \
  src/main/java/com/example/database/internals/GFS/*.java
java -cp target/classes GFSRunner
```

## Usage

```java
MasterServer master = new MasterServer(/*replication*/ 2, /*heartbeatTimeoutMs*/ 5000);
master.registerChunkServer(new ChunkServer("cs-1"));
master.registerChunkServer(new ChunkServer("cs-2"));
master.registerChunkServer(new ChunkServer("cs-3"));

Client client = new Client(master, /*chunkSize*/ 8, /*replication*/ 2);
client.writeFile("demo.txt", "hello gfs");
String data = client.readFile("demo.txt");
```

## Reliability / scale behaviors

- Configurable chunk size and replication factor
- Placement prefers least-loaded alive servers (+ round-robin)
- Stale heartbeats mark servers dead; reads try other replicas
- SHA-256 checksum verified on every chunk read
- Thread-safe metadata (`ConcurrentHashMap`, `CopyOnWriteArrayList`)

## Not modeled (yet)

Real GFS also has leases, pipeline writes, stale replica detection, master
checkpointing, and a networked RPC layer. Those are left out on purpose for a
small demo.
