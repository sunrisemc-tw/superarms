package tw.superarms.data;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.util.TextUtil;

public final class WeaponRepository {
  private final SuperArmsPlugin plugin;
  private final Map<UUID, WeaponDef> defs = new LinkedHashMap<>();
  private File file;
  private FileConfiguration cfg;

  public WeaponRepository(SuperArmsPlugin p) {
    plugin = p;
    file = new File(p.getDataFolder(), "weapons.yml");
  }

  public void load() {
    try {
      if (!file.exists()) {
        file.getParentFile().mkdirs();
        file.createNewFile();
      }
      cfg = YamlConfiguration.loadConfiguration(file);
      defs.clear();
      ConfigurationSection s = cfg.getConfigurationSection("weapons");
      if (s == null) return;
      for (String k : s.getKeys(false)) {
        try {
          UUID id = UUID.fromString(k);
          String b = "weapons." + k;
          WeaponDef d = new WeaponDef(id);
          d.name(s.getString(b + ".name", k));
          d.material(Material.matchMaterial(s.getString(b + ".material", "DIAMOND_SWORD")));
          d.customModelData(
              s.isInt(b + ".custom-model-data") ? s.getInt(b + ".custom-model-data") : null);
          d.unbreakable(s.getBoolean(b + ".unbreakable", true));
          d.lore().addAll(s.getStringList(b + ".lore"));
          ConfigurationSection es = s.getConfigurationSection(b + ".enchantments");
          if (es != null) for (String e : es.getKeys(false)) d.enchantments().put(e, es.getInt(e));
          d.timeoutMillis(TextUtil.parseDuration(s.getString(b + ".timeout", "0")));
          d.sellUntil(TextUtil.parseDate(s.getString(b + ".sell-until", "")));
          d.currency(s.getString(b + ".price.currency", "VAULT"));
          d.price(s.getDouble(b + ".price.amount", 0));
          d.glow(s.getBoolean(b + ".flags.glow", true));
          d.announce(s.getBoolean(b + ".flags.announce", true));
          d.protection(s.getBoolean(b + ".flags.protection", true));
          defs.put(id, d);
        } catch (Exception ignored) {
        }
      }
    } catch (IOException e) {
      plugin.getLogger().warning("Unable to load weapons.yml: " + e.getMessage());
    }
  }

  public void save() {
    FileConfiguration out = new YamlConfiguration();
    for (WeaponDef d : defs.values()) {
      String b = "weapons." + d.id();
      out.set(b + ".name", d.name());
      out.set(b + ".material", d.material().name());
      out.set(b + ".custom-model-data", d.customModelData());
      out.set(b + ".unbreakable", d.unbreakable());
      out.set(b + ".lore", d.lore());
      out.set(b + ".enchantments", d.enchantments());
      out.set(b + ".timeout", TextUtil.duration(d.timeoutMillis()));
      out.set(b + ".sell-until", TextUtil.date(d.sellUntil()));
      out.set(b + ".price.currency", d.currency());
      out.set(b + ".price.amount", d.price());
      out.set(b + ".flags.glow", d.glow());
      out.set(b + ".flags.announce", d.announce());
      out.set(b + ".flags.protection", d.protection());
    }
    try {
      out.save(file);
    } catch (IOException e) {
      plugin.getLogger().warning("Unable to save weapons.yml: " + e.getMessage());
    }
  }

  public Collection<WeaponDef> all() {
    return defs.values();
  }

  public WeaponDef get(UUID id) {
    return defs.get(id);
  }

  public WeaponDef create(String name) {
    WeaponDef d = new WeaponDef(UUID.randomUUID());
    d.name(TextUtil.mm(name));
    defs.put(d.id(), d);
    save();
    return d;
  }

  public void remove(UUID id) {
    defs.remove(id);
    save();
  }
}
