package tw.superarms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnchantNamesTest {

    @Test
    void mapsVanillaEnchantmentIdsToTraditionalChinese() {
        assertEquals("鋒利", EnchantNames.zh("SHARPNESS"));
        assertEquals("鋒利", EnchantNames.zh("sharpness"));
        assertEquals("鋒利", EnchantNames.zh("minecraft:sharpness"));
        assertEquals("耐久", EnchantNames.zh("UNBREAKING"));
        assertEquals("修補", EnchantNames.zh("MENDING"));
        assertEquals("橫掃之刃", EnchantNames.zh("sweeping_edge"));
        assertEquals("消失詛咒", EnchantNames.zh("vanishing_curse"));
    }

    @Test
    void fallsBackToRawIdForUnknownVanillaEnchantment() {
        assertEquals("future_enchant", EnchantNames.zh("minecraft:future_enchant"));
        assertEquals("future_enchant", EnchantNames.zh("future_enchant"));
    }

    @Test
    void keepsThirdPartyNamespaceUntouched() {
        assertEquals("crazyenchantments:blast", EnchantNames.zh("crazyenchantments:blast"));
    }

    @Test
    void handlesBlankInput() {
        assertEquals("", EnchantNames.zh(null));
        assertEquals("", EnchantNames.zh("  "));
    }
}
