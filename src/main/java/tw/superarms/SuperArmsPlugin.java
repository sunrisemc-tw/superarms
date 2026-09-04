package tw.superarms;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * SuperArms 特武系統（Canvas / Folia 1.21.11）。
 * 功能規格見 repo 根目錄 SPEC.md。
 */
public final class SuperArmsPlugin extends JavaPlugin {

    private static SuperArmsPlugin instance;

    public static SuperArmsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("SuperArms v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SuperArms disabled.");
    }
}
