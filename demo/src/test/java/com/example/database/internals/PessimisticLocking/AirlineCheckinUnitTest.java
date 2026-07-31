import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for AirlineCheckin lock-mode SQL helpers (no database required). */
public class AirlineCheckinUnitTest {

    @Test
    public void skipLockedSqlUsesSkipLockedAndOrdersById() {
        String sql = AirlineCheckin.LockMode.SKIP_LOCKED.selectFreeSeatSql();
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(sql.contains("ORDER BY id"));
        assertTrue(sql.contains("user_id IS NULL"));
        assertTrue(sql.contains("LIMIT 1"));
        assertFalse(sql.contains("FOR UPDATE SKIP LOCKED SKIP"));
    }

    @Test
    public void blockingSqlUsesPlainForUpdate() {
        String sql = AirlineCheckin.LockMode.BLOCKING.selectFreeSeatSql();
        assertTrue(sql.contains("FOR UPDATE"));
        assertFalse(sql.contains("SKIP LOCKED"));
        assertTrue(sql.contains("ORDER BY id"));
    }

    @Test
    public void parseLockModeDefaultsToSkipLocked() {
        assertEquals(AirlineCheckin.LockMode.SKIP_LOCKED, AirlineCheckin.parseLockMode(new String[]{}));
        assertEquals(AirlineCheckin.LockMode.SKIP_LOCKED,
                AirlineCheckin.parseLockMode(new String[]{"--verbose"}));
        assertEquals(AirlineCheckin.LockMode.BLOCKING,
                AirlineCheckin.parseLockMode(new String[]{"--blocking"}));
    }
}
