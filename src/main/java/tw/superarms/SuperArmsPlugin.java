package tw.superarms;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tw.superarms.command.SuperArmsCommand;
import tw.superarms.data.WeaponRepository;
import tw.superarms.economy.EconomyService;
import tw.superarms.listener.GameListener;
import tw.superarms.service.ExpiryService;
import tw.superarms.service.ShopService;

/** SuperArms 特武系統（Canvas / Folia 1.21.11）。 功能規格見 repo 根目錄 SPEC.md。 */
public final class SuperArmsPlugin extends JavaPlugin {

  private static SuperArmsPlugin instance;
  private FileConfiguration messages;

  public static SuperArmsPlugin getInstance() {
    return instance;
  }

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    saveResource("messages.yml", false);
    messages =
        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.File(getDataFolder(), "messages.yml"));
    WeaponRepository repository = new WeaponRepository(this);
    repository.load();
    EconomyService economy = new EconomyService(this);
    ExpiryService expiry = new ExpiryService(this, repository);
    ShopService shop = new ShopService(this, repository, economy, expiry);
    getServer()
        .getPluginManager()
        .registerEvents(new GameListener(this, repository, shop, expiry), this);
    SuperArmsCommand command = new SuperArmsCommand(this, repository, shop, expiry, economy);
    getCommand("superarms").setExecutor(command);
    getCommand("superarms").setTabCompleter(command);
    getLogger().info("SuperArms v" + getPluginMeta().getVersion() + " enabled.");
  }

  public FileConfiguration messages() {
    return messages;
  }

  public void reloadMessages() {
    messages =
        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.File(getDataFolder(), "messages.yml"));
  }

  @Override
  public void onDisable() {
    getServer().getGlobalRegionScheduler().cancelTasks(this);
    getLogger().info("SuperArms disabled.");
  }
}
