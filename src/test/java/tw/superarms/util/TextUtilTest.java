package tw.superarms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import net.kyori.adventure.text.Component;
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

    @Test
    void serializesComponentsBackToMiniMessageWithoutItalic() {
        // 匯入背包物品時，物品名稱/lore 要能被原樣存回 weapons.yml
        String roundTrip = TextUtil.serialize(TextUtil.component("<red>烈焰之刃"));
        assertEquals("<red>烈焰之刃", roundTrip);
        assertEquals("<red>烈焰之刃", TextUtil.mm(roundTrip));

        // 物品名稱裡的尖括號必須被逸出，否則存檔後重新解析會被當成 tag 吃掉
        Component literal = Component.text("a<b>c");
        String escaped = TextUtil.serialize(literal);
        assertEquals("a<b>c", TextUtil.plain(TextUtil.component(escaped)));
    }
}
