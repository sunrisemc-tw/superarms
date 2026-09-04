package tw.superarms.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import tw.superarms.SuperArmsPlugin;

/**
 * 經濟 provider 選擇器。Vault / PlayerPoints 皆為 softdepend，以反射存取：
 * 未安裝時 provider() 回傳 null，呼叫端（ShopService）會顯示「餘額不足/購買失敗」，
 * 不會讓整個插件在 enable 時崩潰。
 */
public final class EconomyService {

    private final SoftDependReflector reflector;

    public EconomyService(SuperArmsPlugin plugin) {
        this.reflector = new SoftDependReflector(plugin);
    }

    /**
     * 依 currency id 回傳 provider。VAULT / PLAYER_POINTS 對應的插件未安裝時回傳 null。
     */
    public CurrencyProvider provider(String currencyId) {
        if ("PLAYER_POINTS".equalsIgnoreCase(currencyId)) {
            if (!reflector.playerPointsAvailable()) {
                return null;
            }
            return new CurrencyProvider() {
                @Override
                public boolean has(Player player, double amount) {
                    return reflector.pointsLook(player) >= amount;
                }

                @Override
                public boolean withdraw(Player player, double amount) {
                    return reflector.pointsTake(player, (int) Math.ceil(amount));
                }

                @Override
                public boolean deposit(Player player, double amount) {
                    return reflector.pointsGive(player, (int) Math.ceil(amount));
                }

                @Override
                public String format(double amount) {
                    return (int) amount + " points";
                }
            };
        }
        if ("VAULT".equalsIgnoreCase(currencyId)) {
            if (!reflector.vaultAvailable()) {
                return null;
            }
            return new CurrencyProvider() {
                @Override
                public boolean has(Player player, double amount) {
                    return reflector.vaultHas(player, amount);
                }

                @Override
                public boolean withdraw(Player player, double amount) {
                    return reflector.vaultWithdraw(player, amount);
                }

                @Override
                public boolean deposit(Player player, double amount) {
                    return reflector.vaultDeposit(player, amount);
                }

                @Override
                public String format(double amount) {
                    return reflector.vaultFormat(amount);
                }
            };
        }
        return null;
    }
}
