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
import tw.superarms.data.WeaponDef;
import tw.superarms.util.TextUtil;

public final class ItemService {

    public static final NamespacedKey DEF = new NamespacedKey(
            SuperArmsPlugin.getInstance(),
            "def"
    );
    public static final NamespacedKey EXP = new NamespacedKey(
            SuperArmsPlugin.getInstance(),
            "expires_at"
    );
    public static final NamespacedKey OWNER = new NamespacedKey(
            SuperArmsPlugin.getInstance(),
            "owner"
    );
    public static final NamespacedKey BOUGHT = new NamespacedKey(
            SuperArmsPlugin.getInstance(),
            "boughtAt"
    );

    private static final String VALID_UNTIL_TEXT = "附魔有效至";
    private static final String EXPIRED_TEXT = "附魔已失效";
    private static final PlainTextComponentSerializer PLAIN_TEXT =
            PlainTextComponentSerializer.plainText();

    private ItemService() {
    }

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
        pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
        pdc.set(BOUGHT, PersistentDataType.LONG, boughtAt);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack preview(WeaponDef weapon) {
        return render(weapon, 0);
    }

    public static UUID def(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(DEF, PersistentDataType.STRING);
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

    public static ItemStack expire(ItemStack source, WeaponDef weapon) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null
                ? new ArrayList<>()
                : new ArrayList<>(meta.lore());
        boolean alreadyExpired = lore.stream().anyMatch(line -> containsText(line, EXPIRED_TEXT));

        meta.getPersistentDataContainer().set(EXP, PersistentDataType.LONG, 0L);
        if (alreadyExpired) {
            item.setItemMeta(meta);
            return item;
        }

        for (String name : weapon.enchantments().keySet()) {
            Enchantment enchantment = enchantment(name);
            if (enchantment != null) {
                meta.removeEnchant(enchantment);
            }
        }
        meta.removeEnchant(Enchantment.LURE);
        lore.removeIf(line -> containsText(line, VALID_UNTIL_TEXT));
        lore.add(TextUtil.component("<red>附魔已失效"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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
