package tw.superarms.util;

import java.util.Locale;
import java.util.Map;

/**
 * 原版附魔 id → 繁體中文名稱（來源：MC 26.2 官方語言檔 zh_tw.json 的 enchantment.minecraft.*）。
 * 玩家端物品的附魔是原版元件、由客戶端翻譯；這裡只負責「管理 GUI / 提示訊息」的中文顯示。
 */
public final class EnchantNames {

    private static final Map<String, String> ZH_TW = Map.ofEntries(
            Map.entry("aqua_affinity", "親水性"),
            Map.entry("bane_of_arthropods", "節肢剋星"),
            Map.entry("binding_curse", "綁定詛咒"),
            Map.entry("blast_protection", "爆炸保護"),
            Map.entry("breach", "破甲"),
            Map.entry("channeling", "喚雷"),
            Map.entry("density", "緻密"),
            Map.entry("depth_strider", "深海漫遊"),
            Map.entry("efficiency", "效率"),
            Map.entry("feather_falling", "輕盈"),
            Map.entry("fire_aspect", "燃燒"),
            Map.entry("fire_protection", "火焰保護"),
            Map.entry("flame", "火焰"),
            Map.entry("fortune", "幸運"),
            Map.entry("frost_walker", "冰霜行者"),
            Map.entry("impaling", "魚叉"),
            Map.entry("infinity", "無限"),
            Map.entry("knockback", "擊退"),
            Map.entry("looting", "掠奪"),
            Map.entry("loyalty", "忠誠"),
            Map.entry("luck_of_the_sea", "海洋的祝福"),
            Map.entry("lunge", "突刺"),
            Map.entry("lure", "魚餌"),
            Map.entry("mending", "修補"),
            Map.entry("multishot", "分裂箭矢"),
            Map.entry("piercing", "貫穿"),
            Map.entry("power", "強力"),
            Map.entry("projectile_protection", "投射物保護"),
            Map.entry("protection", "保護"),
            Map.entry("punch", "衝擊"),
            Map.entry("quick_charge", "快速上弦"),
            Map.entry("respiration", "水中呼吸"),
            Map.entry("riptide", "波濤"),
            Map.entry("sharpness", "鋒利"),
            Map.entry("silk_touch", "絲綢之觸"),
            Map.entry("smite", "不死剋星"),
            Map.entry("soul_speed", "靈魂疾走"),
            Map.entry("sweeping", "橫掃之刃"),
            Map.entry("sweeping_edge", "橫掃之刃"),
            Map.entry("swift_sneak", "迅捷潛行"),
            Map.entry("thorns", "尖刺"),
            Map.entry("unbreaking", "耐久"),
            Map.entry("vanishing_curse", "消失詛咒"),
            Map.entry("wind_burst", "風爆")
    );

    private EnchantNames() {
    }

    /**
     * 取附魔的繁體中文名。
     * - minecraft 命名空間或無命名空間：查表，查不到（新版本新增/原版外）回傳原始 id
     * - 其他命名空間（第三方插件自訂附魔）：直接回傳原始 key，不猜中文
     */
    public static String zh(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String key = rawKey.trim();
        int separator = key.indexOf(':');
        if (separator >= 0) {
            String namespace = key.substring(0, separator).toLowerCase(Locale.ROOT);
            if (!namespace.equals("minecraft")) {
                return key;
            }
            key = key.substring(separator + 1);
        }
        String name = ZH_TW.get(key.toLowerCase(Locale.ROOT));
        return name == null ? key : name;
    }
}
