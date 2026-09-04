package tw.superarms;

import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tw.superarms.command.SuperArmsCommand;
import tw.superarms.data.WeaponRepository;
import tw.superarms.economy.EconomyService;
import tw.superarms.listener.GameListener;
import tw.superarms.service.AdminService;
import tw.superarms.service.ExpiryService;
import tw.superarms.service.ShopService;

public final class SuperArmsPlugin extends JavaPlugin {

    private static SuperArmsPlugin instance;

    private FileConfiguration messages;
    private ShopService shop;
    private AdminService admin;

    public static SuperArmsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);
        reloadMessages();

        WeaponRepository repository = new WeaponRepository(this);
        repository.load();
        EconomyService economy = new EconomyService(this);
        ExpiryService expiry = new ExpiryService(this, repository);
        shop = new ShopService(this, repository, economy, expiry);
        admin = new AdminService(this, repository);

        getServer().getPluginManager().registerEvents(
                new GameListener(shop, admin, expiry),
                this
        );
        SuperArmsCommand command = new SuperArmsCommand(this, repository, shop, admin);
        getCommand("superarms").setExecutor(command);
        getCommand("superarms").setTabCompleter(command);
        getLogger().info("SuperArms v" + getPluginMeta().getVersion() + " enabled.");
    }

    public FileConfiguration messages() {
        return messages;
    }

    public void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(
                new File(getDataFolder(), "messages.yml")
        );
    }

    @Override
    public void onDisable() {
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        if (shop != null) {
            shop.clearConfirms();
        }
        if (admin != null) {
            admin.clearInputs();
        }
        getLogger().info("SuperArms disabled.");
    }
}
