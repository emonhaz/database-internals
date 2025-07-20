import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ConnectionPoolUnitTest {

    @Test
    public void testAcquireAndRelease() throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(1);
        Connection conn = pool.acquire();

        assertNotNull(conn);
        assertEquals(0, pool.availableConnections());

        pool.release(conn);
        assertEquals(1, pool.availableConnections());
    }

    @Test
    public void testAcquireBlocksWhenEmpty() throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(1);
        Connection conn = pool.acquire();

        Thread t = new Thread(() -> {
            try {
                pool.acquire(); // should block
                fail("Should have blocked but didn't");
            } catch (InterruptedException ignored) {}
        });

        t.start();
        Thread.sleep(500); // allow thread to block

        assertTrue(t.isAlive()); // Still blocked

        pool.release(conn); // Unblock
        t.join(1000);
        assertFalse(t.isAlive()); // Now finished
    }
}
