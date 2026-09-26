package tw.superarms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.TagVerdict;
import tw.superarms.data.WeaponDef;
import tw.superarms.util.TagSigner;
import tw.superarms.util.TextUtil;

/**
 * 特武物品的標籤管理。
 *
 * <p>標籤（PDC）就是特武的唯一身分：只要標籤在且簽章正確，這件物品就是特武，
 * 其餘欄位（名稱、lore、額外附魔…）不管被怎麼改都照規則走；
 * 標籤被動過（簽章不符）則 fail-closed 直接失效。
 */
public final class ItemService {

    /** 目前標籤版本。v1 = 只有 def/expires_at/owner/boughtAt；v2 = 追加 sig/tag_ver/expired。 */
    public static final int TAG_VERSION = 2;

    public static final NamespacedKey DEF = key("def");
    public static final NamespacedKey EXP = key("expires_at");
    public static final NamespacedKey OWNER = key("owner");
    public static final NamespacedKey BOUGHT = key("boughtAt");
    public static final NamespacedKey SIG = key("sig");
    public static final NamespacedKey TAG_VER = key("tag_ver");
    public static final NamespacedKey EXPIRED = key("expired");

    private static final String VALID_UNTIL_TEXT = "附魔有效至";
    private static final String EXPIRED_TEXT = "附魔已失效";
    private static final PlainTextComponentSerializer PLAIN_TEXT =
            PlainTextComponentSerializer.plainText();

    private static String secret = "";

    private ItemService() {
    }

    /** 由插件 onEnable 注入簽章密鑰。 */
    public static void configure(String tagSecret) {
        secret = tagSecret == null ? "" : tagSecret;
    }

    private static NamespacedKey key(String value) {
        return new NamespacedKey(SuperArmsPlugin.getInstance(), value);
    }

    // ---------------------------------------------------------------- 建立

    public static ItemStack create(WeaponDef weapon, UUID owner) {
        long boughtAt = System.currentTimeMillis();
        long expiresAt = weapon.timeoutMillis() == 0
                ? 0
                : boughtAt + weapon.timeoutMillis();
        ItemStack item = render(weapon, expiresAt);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(DEF, PersistentDataType.STRING, weapon.id().toString());
        pdc.set(EXP, PersistentDataType.LONG, expiresAt);
        pdc.set(OWNER, PersistentDataType.STRING, owner == null ? "" : owner.toString());
        pdc.set(BOUGHT, PersistentDataType.LONG, boughtAt);
        pdc.set(EXPIRED, PersistentDataType.INTEGER, 0);
        applySignature(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack preview(WeaponDef weapon) {
        return render(weapon, 0);
    }

    // ---------------------------------------------------------------- 讀取

    /** 原始 def 字串（未經解析）。 */
    public static String defString(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(DEF, PersistentDataType.STRING);
    }

    public static UUID def(ItemStack item) {
        String value = defString(item);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static long expires(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Long value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(EXP, PersistentDataType.LONG);
        return value == null ? 0 : value;
    }

    /** 是否已標記失效（0 = 未失效）。 */
    public static int expired(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(EXPIRED, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    /** 驗證標籤狀態。 */
    public static TagVerdict verdict(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return TagVerdict.UNTAGGED;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String def = pdc.get(DEF, PersistentDataType.STRING);
        if (def == null || def.isBlank()) {
            return TagVerdict.UNTAGGED;
        }
        Integer version = pdc.get(TAG_VER, PersistentDataType.INTEGER);
        String signature = pdc.get(SIG, PersistentDataType.STRING);
        if (version == null || signature == null) {
            return TagVerdict.LEGACY;
        }
        String payload = payloadOf(pdc);
        return TagSigner.verify(secret, payload, signature)
                ? TagVerdict.VALID
                : TagVerdict.TAMPERED;
    }

    // ---------------------------------------------------------------- 遷移／失效

    /**
     * 一次性遷移：把舊版（無簽章）特武補上簽章，保留既有到期時間與其他欄位。
     */
    public static ItemStack migrate(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        applySignature(meta);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 讓特武失效：拔除「全部」附魔（含玩家自行加上的），移除 glint，
     * 把「附魔有效至」換成「附魔已失效」，並把狀態改為已失效後重新簽章。
     */
    public static ItemStack expireAll(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
        meta.removeEnchant(Enchantment.LURE);

        List<Component> lore = meta.lore() == null
                ? new ArrayList<>()
                : new ArrayList<>(meta.lore());
        lore.removeIf(line -> containsText(line, VALID_UNTIL_TEXT));
        boolean alreadyMarked = lore.stream()
                .anyMatch(line -> containsText(line, EXPIRED_TEXT));
        if (!alreadyMarked) {
            lore.add(TextUtil.component("<red>" + EXPIRED_TEXT));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EXPIRED, PersistentDataType.INTEGER, 1);
        pdc.set(EXP, PersistentDataType.LONG, 0L);
        applySignature(meta);
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------- 內部

    private static void applySignature(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VER, PersistentDataType.INTEGER, TAG_VERSION);
        pdc.set(SIG, PersistentDataType.STRING, TagSigner.sign(secret, payloadOf(pdc)));
    }

    private static String payloadOf(PersistentDataContainer pdc) {
        String def = pdc.get(DEF, PersistentDataType.STRING);
        Long expiresAt = pdc.get(EXP, PersistentDataType.LONG);
        String owner = pdc.get(OWNER, PersistentDataType.STRING);
        Long boughtAt = pdc.get(BOUGHT, PersistentDataType.LONG);
        Integer expired = pdc.get(EXPIRED, PersistentDataType.INTEGER);
        return TagSigner.payload(
                def,
                expiresAt == null ? 0 : expiresAt,
                owner,
                boughtAt == null ? 0 : boughtAt,
                expired == null ? 0 : expired
        );
    }

    public static Enchantment enchantment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (key == null) {
            return null;
        }
        return Registry.ENCHANTMENT.get(key);
    }

    private static ItemStack render(WeaponDef weapon, long expiresAt) {
        Material material = weapon.material() == null
                ? Material.DIAMOND_SWORD
                : weapon.material();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.component(weapon.name()));

        List<Component> lore = new ArrayList<>();
        for (String line : weapon.lore()) {
            lore.add(TextUtil.component(line));
        }
        if (expiresAt > 0) {
            lore.add(TextUtil.component("<green>附魔有效至 " + TextUtil.date(expiresAt)));
        }
        meta.lore(lore);
        meta.setUnbreakable(weapon.unbreakable());
        if (weapon.customModelData() != null) {
            meta.setCustomModelData(weapon.customModelData());
        }
        for (var entry : weapon.enchantments().entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
        // 有真附魔時：讓附魔自然顯示（hover 看得到附魔清單）且自然 glint。
        // 只有「glow=true 但沒任何附魔」時，才用 LURE 偽 glint 並藏起來。
        boolean hasRealEnchant = !weapon.enchantments().isEmpty();
        if (weapon.glow() && !hasRealEnchant) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static boolean containsText(Component component, String text) {
        return PLAIN_TEXT.serialize(component).contains(text);
    }
}
