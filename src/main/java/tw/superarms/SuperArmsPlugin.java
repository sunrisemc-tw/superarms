package tw.superarms;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tw.superarms.command.SuperArmsCommand;
import tw.superarms.data.WeaponRepository;
import tw.superarms.economy.EconomyService;
import tw.superarms.listener.GameListener;
import tw.superarms.service.AdminService;
import tw.superarms.service.ExpiryService;
import tw.superarms.service.ItemService;
import tw.superarms.service.ShopService;
import tw.superarms.util.TagSigner;

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

        ItemService.configure(loadOrCreateSecret());

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

    /**
     * 讀取（或首次生成）特武標籤簽章密鑰。
     *
     * <p>密鑰存放於 {@code plugins/SuperArms/tag.key}，不隨外掛打包、也不寫入 config.yml
     * （避免 saveConfig 抹掉註解）。此檔遺失會使所有已簽章特武被判為遭竄改而失效，
     * 因此務必納入備份。
     */
    private String loadOrCreateSecret() {
        File file = new File(getDataFolder(), "tag.key");
        try {
            if (file.exists()) {
                String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    return existing;
                }
            }
            String created = TagSigner.newSecret();
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Unable to create data directory");
            }
            Files.writeString(file.toPath(), created, StandardCharsets.UTF_8);
            getLogger().warning(
                    "已生成新的特武簽章密鑰 tag.key；請勿刪除，遺失會使所有已簽章特武失效，請納入備份。"
            );
            return created;
        } catch (IOException exception) {
            getLogger().severe(
                    "無法讀寫 tag.key（" + exception.getMessage()
                            + "）；本回合改用臨時密鑰，重啟後既有特武將無法驗證。"
            );
            return TagSigner.newSecret();
        }
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
