package tw.superarms.economy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import tw.superarms.SuperArmsPlugin;

/**
 * 以反射呼叫 Vault / PlayerPoints，避免直接 import 造成
 * NoClassDefFoundError（Vault/PlayerPoints 未安裝時整個插件無法載入）。
 * 兩者皆為 softdepend：沒裝時 provider() 回傳 null，優雅降級。
 */
public final class SoftDependReflector {

    private final SuperArmsPlugin plugin;

    public SoftDependReflector(SuperArmsPlugin plugin) {
        this.plugin = plugin;
    }

    /** 回傳 true 表示 Vault 已安裝且有註冊經濟 provider。 */
    public boolean vaultAvailable() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            return Bukkit.getServicesManager().getRegistration(economyClass) != null;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    /** 回傳 true 表示 PlayerPoints 已安裝。 */
    public boolean playerPointsAvailable() {
        try {
            Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            return Bukkit.getPluginManager().getPlugin("PlayerPoints") != null;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    public boolean vaultHas(OfflinePlayer player, double amount) {
        Object economy = economyRegistration();
        if (economy == null) {
            return false;
        }
        return (Boolean) invoke(economy, "has", new Class<?>[] {OfflinePlayer.class, double.class},
                player, amount);
    }

    public boolean vaultWithdraw(OfflinePlayer player, double amount) {
        Object result = invoke(economyRegistration(), "withdrawPlayer",
                new Class<?>[] {OfflinePlayer.class, double.class}, player, amount);
        return result != null && (Boolean) invoke(result, "transactionSuccess", new Class<?>[] {});
    }

    public boolean vaultDeposit(OfflinePlayer player, double amount) {
        Object result = invoke(economyRegistration(), "depositPlayer",
                new Class<?>[] {OfflinePlayer.class, double.class}, player, amount);
        return result != null && (Boolean) invoke(result, "transactionSuccess", new Class<?>[] {});
    }

    public String vaultFormat(double amount) {
        Object economy = economyRegistration();
        if (economy == null) {
            return String.valueOf(amount);
        }
        return (String) invoke(economy, "format", new Class<?>[] {double.class}, amount);
    }

    public double pointsLook(OfflinePlayer player) {
        return ((Number) invoke(pointsApi(), "look", new Class<?>[] {java.util.UUID.class},
                player.getUniqueId())).doubleValue();
    }

    public boolean pointsTake(OfflinePlayer player, int amount) {
        return (Boolean) invoke(pointsApi(), "take", new Class<?>[] {java.util.UUID.class, int.class},
                player.getUniqueId(), amount);
    }

    public boolean pointsGive(OfflinePlayer player, int amount) {
        return (Boolean) invoke(pointsApi(), "give", new Class<?>[] {java.util.UUID.class, int.class},
                player.getUniqueId(), amount);
    }

    private Object economyRegistration() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration(economyClass);
            return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private Object pointsApi() {
        try {
            Class<?> pointsClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Plugin pointsPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pointsPlugin == null || !pointsClass.isInstance(pointsPlugin)) {
                return null;
            }
            Class<?> apiClass = Class.forName("org.black_ixx.playerpoints.PlayerPointsAPI");
            return apiClass.getConstructor(pointsClass).newInstance(pointsPlugin);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName, Class<?>[] types, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, types);
            return method.invoke(target, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().warning("SuperArms economy reflection failed: " + methodName + " - "
                    + exception.getMessage());
            return null;
        }
    }
}
