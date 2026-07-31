import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for the LSM tree demo. */
public class LSMTreeUnitTest {

    @TempDir
    Path tempDir;

    @Test
    public void putGetOverwriteAndDelete() throws Exception {
        try (LSMTree tree = new LSMTree(tempDir.toString(), 100, 10)) {
            tree.put("a", "1");
            tree.put("b", "2");
            assertEquals("1", tree.get("a"));
            tree.put("a", "1b");
            assertEquals("1b", tree.get("a"));
            tree.delete("a");
            assertNull(tree.get("a"));
            assertEquals("2", tree.get("b"));
        }
    }

    @Test
    public void flushPersistsAndNewestWinsAcrossSstables() throws Exception {
        try (LSMTree tree = new LSMTree(tempDir.toString(), 2, 10)) {
            tree.put("apple", "fruit");
            tree.put("banana", "yellow"); // flush
            tree.put("apple", "tech");
            tree.put("cherry", "red"); // flush
            assertEquals("tech", tree.get("apple"));
            assertEquals("yellow", tree.get("banana"));
            assertTrue(tree.sstableCount() >= 1);
        }
    }

    @Test
    public void tombstoneHidesOlderSstableValue() throws Exception {
        try (LSMTree tree = new LSMTree(tempDir.toString(), 2, 10)) {
            tree.put("k", "old");
            tree.put("x", "1"); // flush with k=old
            tree.delete("k");
            tree.put("y", "2"); // flush tombstone
            assertNull(tree.get("k"));
        }
    }

    @Test
    public void compactionReducesSstableCountAndKeepsValues() throws Exception {
        try (LSMTree tree = new LSMTree(tempDir.toString(), 2, 100)) {
            for (int i = 0; i < 10; i++) {
                tree.put("k" + i, "v" + i);
            }
            tree.flush();
            int before = tree.sstableCount();
            assertTrue(before >= 2);
            tree.compact();
            assertEquals(1, tree.sstableCount());
            assertEquals("v3", tree.get("k3"));
            assertEquals("v9", tree.get("k9"));
        }
    }

    @Test
    public void recoversFromExistingFiles() throws Exception {
        Path dir = tempDir.resolve("recover");
        Files.createDirectories(dir);
        try (LSMTree tree = new LSMTree(dir.toString(), 2, 10)) {
            tree.put("a", "1");
            tree.put("b", "2");
            tree.put("c", "3");
            tree.flush();
        }
        try (LSMTree reopened = new LSMTree(dir.toString(), 2, 10)) {
            assertTrue(reopened.sstableCount() >= 1);
            assertEquals("1", reopened.get("a"));
            assertEquals("3", reopened.get("c"));
        }
    }

    @Test
    public void concurrentPutsAndGetsRemainConsistent() throws Exception {
        try (LSMTree tree = new LSMTree(tempDir.toString(), 50, 8)) {
            int writers = 4;
            int keysPerWriter = 100;
            ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(writers);
            AtomicInteger failures = new AtomicInteger();

            for (int w = 0; w < writers; w++) {
                final int writer = w;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < keysPerWriter; i++) {
                            String key = "w" + writer + "-" + i;
                            tree.put(key, "v" + i);
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        tree.get("w0-0");
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            assertEquals(0, failures.get());

            List<String> missing = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                for (int i = 0; i < keysPerWriter; i++) {
                    String key = "w" + w + "-" + i;
                    if (!("v" + i).equals(tree.get(key))) {
                        missing.add(key);
                    }
                }
            }
            assertTrue(missing.isEmpty(), "missing/wrong: " + missing);
        }
    }
}
