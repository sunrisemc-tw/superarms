package tw.superarms.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.util.TextUtil;

public final class WeaponRepository {

    private final SuperArmsPlugin plugin;
    private final Map<UUID, WeaponDef> definitions = new LinkedHashMap<>();
    private final File file;

    public WeaponRepository(SuperArmsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "weapons.yml");
    }

    public synchronized void load() {
        try {
            ensureFileExists();
            FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            definitions.clear();
            ConfigurationSection weapons = configuration.getConfigurationSection("weapons");
            if (weapons == null) {
                return;
            }
            for (String idValue : weapons.getKeys(false)) {
                loadWeapon(weapons.getConfigurationSection(idValue), idValue);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to load weapons.yml: " + exception.getMessage());
        }
    }

    public synchronized void save() {
        FileConfiguration output = new YamlConfiguration();
        for (WeaponDef weapon : definitions.values()) {
            String path = "weapons." + weapon.id();
            output.set(path + ".name", weapon.name());
            output.set(path + ".material", weapon.material().name());
            output.set(path + ".custom-model-data", weapon.customModelData());
            output.set(path + ".unbreakable", weapon.unbreakable());
            output.set(path + ".lore", weapon.lore());
            output.set(path + ".enchantments", weapon.enchantments());
            output.set(path + ".timeout", TextUtil.duration(weapon.timeoutMillis()));
            output.set(path + ".sell-until", TextUtil.date(weapon.sellUntil()));
            output.set(path + ".price.currency", weapon.currency());
            output.set(path + ".price.amount", weapon.price());
            output.set(path + ".flags.glow", weapon.glow());
            output.set(path + ".flags.announce", weapon.announce());
            output.set(path + ".flags.protection", weapon.protection());
        }
        try {
            ensureFileExists();
            output.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save weapons.yml: " + exception.getMessage());
        }
    }

    public synchronized Collection<WeaponDef> all() {
        return new ArrayList<>(definitions.values());
    }

    public synchronized WeaponDef get(UUID id) {
        return definitions.get(id);
    }

    public synchronized WeaponDef create(String name) {
        WeaponDef weapon = new WeaponDef(UUID.randomUUID());
        weapon.name(TextUtil.mm(name));
        definitions.put(weapon.id(), weapon);
        save();
        return weapon;
    }

    public synchronized void remove(UUID id) {
        definitions.remove(id);
        save();
    }

    private void ensureFileExists() throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create plugin data directory");
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Unable to create weapons.yml");
        }
    }

    private void loadWeapon(ConfigurationSection section, String idValue) {
        if (section == null) {
            return;
        }
        try {
            UUID id = UUID.fromString(idValue);
            WeaponDef weapon = new WeaponDef(id);
            weapon.name(section.getString("name", idValue));
            Material material = Material.matchMaterial(
                    section.getString("material", "DIAMOND_SWORD")
            );
            weapon.material(material == null ? Material.DIAMOND_SWORD : material);
            weapon.customModelData(
                    section.isInt("custom-model-data")
                            ? section.getInt("custom-model-data")
                            : null
            );
            weapon.unbreakable(section.getBoolean("unbreakable", true));
            weapon.lore().addAll(section.getStringList("lore"));
            ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
            if (enchantments != null) {
                for (String enchantment : enchantments.getKeys(false)) {
                    weapon.enchantments().put(
                            enchantment,
                            enchantments.getInt(enchantment)
                    );
                }
            }
            weapon.timeoutMillis(TextUtil.parseDuration(section.getString("timeout", "0")));
            weapon.sellUntil(TextUtil.parseDate(section.getString("sell-until", "")));
            weapon.currency(section.getString("price.currency", "VAULT"));
            weapon.price(section.getDouble("price.amount", 0));
            weapon.glow(section.getBoolean("flags.glow", true));
            weapon.announce(section.getBoolean("flags.announce", true));
            weapon.protection(section.getBoolean("flags.protection", true));
            definitions.put(id, weapon);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping invalid weapon UUID: " + idValue);
        }
    }
}
