package tw.superarms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TextUtilTest {

    @Test
    void parsesSupportedDurationsAndPermanentValue() {
        assertEquals(7L * 86_400_000L, TextUtil.parseDurationInput("7d").orElseThrow());
        assertEquals(24L * 3_600_000L, TextUtil.parseDurationInput("24h").orElseThrow());
        assertEquals(0L, TextUtil.parseDurationInput("0").orElseThrow());
    }

    @Test
    void rejectsInvalidOrOverflowingDurations() {
        assertTrue(TextUtil.parseDurationInput("").isEmpty());
        assertTrue(TextUtil.parseDurationInput("seven days").isEmpty());
        assertTrue(TextUtil.parseDurationInput("999999999999999999999d").isEmpty());
    }

    @Test
    void parsesStrictSellUntilDate() {
        long expected = LocalDateTime.of(2026, 12, 31, 23, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        assertEquals(
                expected,
                TextUtil.parseDateInput("2026-12-31 23:59").orElseThrow()
        );
        assertTrue(TextUtil.parseDateInput("2026-02-30 10:00").isEmpty());
        assertTrue(TextUtil.parseDateInput("2026/12/31 23:59").isEmpty());
    }

    @Test
    void convertsLegacyFormattingToMiniMessage() {
        assertEquals("<green>名稱", TextUtil.mm("&a名稱"));
        assertEquals("<bold>名稱", TextUtil.mm("§l名稱"));
        assertEquals("<red>名稱</red>", TextUtil.mm("<red>名稱</red>"));
    }
}
