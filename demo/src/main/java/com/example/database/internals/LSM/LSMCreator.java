import java.nio.file.Files;

/** Demo for put / overwrite / delete / flush / compaction against an LSMTree. */
public final class LSMCreator {
    private LSMCreator() {
    }

    public static void main(String[] args) throws Exception {
        String dataDir = args.length > 0 ? args[0] : Files.createTempDirectory("lsm-demo-").toString();
        System.out.println("Using data dir: " + dataDir);

        try (LSMTree tree = new LSMTree(dataDir, 3, 3)) {
            tree.put("apple", "fruit");
            tree.put("aardvark", "animal");
            tree.put("banana", "yellow"); // flush #1
            tree.put("apple", "tech");
            tree.put("cherry", "red");
            tree.put("date", "brown"); // flush #2 + maybe compact

            System.out.println("apple => " + tree.get("apple"));
            System.out.println("banana => " + tree.get("banana"));
            System.out.println("aardvark => " + tree.get("aardvark"));
            System.out.println("cherry => " + tree.get("cherry"));

            tree.delete("banana");
            System.out.println("banana after delete => " + tree.get("banana"));
            System.out.println("sstables=" + tree.sstableCount() + " memtable=" + tree.memtableSize());
        }

        // Re-open to show recovery from existing SSTables.
        try (LSMTree reopened = new LSMTree(dataDir, 3, 3)) {
            System.out.println("reopened apple => " + reopened.get("apple"));
            System.out.println("reopened banana => " + reopened.get("banana"));
            System.out.println("reopened sstables=" + reopened.sstableCount());
        }
    }
}
