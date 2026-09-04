package tw.superarms.service;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import tw.superarms.*;
import tw.superarms.data.*;
import tw.superarms.util.TextUtil;

public final class ItemService {
  public static final NamespacedKey DEF = new NamespacedKey(SuperArmsPlugin.getInstance(), "def"),
      EXP = new NamespacedKey(SuperArmsPlugin.getInstance(), "expiresAt"),
      OWNER = new NamespacedKey(SuperArmsPlugin.getInstance(), "owner"),
      BOUGHT = new NamespacedKey(SuperArmsPlugin.getInstance(), "boughtAt");

  public static ItemStack create(WeaponDef d, UUID owner) {
    ItemStack i = new ItemStack(d.material());
    ItemMeta m = i.getItemMeta();
    m.displayName(TextUtil.component(d.name()));
    List<Component> lore = new ArrayList<>();
    for (String l : d.lore()) lore.add(TextUtil.component(l));
    long exp = d.timeoutMillis() == 0 ? 0 : System.currentTimeMillis() + d.timeoutMillis();
    if (d.timeoutMillis() > 0) lore.add(TextUtil.component("<green>附魔有效至 " + TextUtil.date(exp)));
    m.lore(lore);
    m.setUnbreakable(d.unbreakable());
    if (d.customModelData() != null) m.setCustomModelData(d.customModelData());
    for (var e : d.enchantments().entrySet()) {
      Enchantment x = Enchantment.getByKey(NamespacedKey.minecraft(e.getKey().toLowerCase()));
      if (x != null) m.addEnchant(x, e.getValue(), true);
    }
    if (d.glow()) {
      m.addEnchant(Enchantment.LURE, 1, true);
      m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    PersistentDataContainer p = m.getPersistentDataContainer();
    p.set(DEF, PersistentDataType.STRING, d.id().toString());
    p.set(EXP, PersistentDataType.LONG, exp);
    p.set(OWNER, PersistentDataType.STRING, owner.toString());
    p.set(BOUGHT, PersistentDataType.LONG, System.currentTimeMillis());
    i.setItemMeta(m);
    return i;
  }

  public static UUID def(ItemStack i) {
    if (i == null || !i.hasItemMeta()) return null;
    String s = i.getItemMeta().getPersistentDataContainer().get(DEF, PersistentDataType.STRING);
    try {
      return s == null ? null : UUID.fromString(s);
    } catch (Exception e) {
      return null;
    }
  }

  public static long expires(ItemStack i) {
    if (i == null || !i.hasItemMeta()) return 0;
    Long x = i.getItemMeta().getPersistentDataContainer().get(EXP, PersistentDataType.LONG);
    return x == null ? 0 : x;
  }

  public static ItemStack expire(ItemStack src, WeaponDef d) {
    ItemStack i = src.clone();
    ItemMeta m = i.getItemMeta();
    for (String n : d.enchantments().keySet()) {
      Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(n.toLowerCase()));
      if (e != null) m.removeEnchant(e);
    }
    m.removeEnchant(Enchantment.LURE);
    List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
    if (!l.isEmpty()) l.remove(l.size() - 1);
    l.add(TextUtil.component("<red>附魔已失效"));
    m.lore(l);
    i.setItemMeta(m);
    return i;
  }
}
