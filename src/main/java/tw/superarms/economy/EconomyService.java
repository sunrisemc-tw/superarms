package tw.superarms.economy;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import tw.superarms.SuperArmsPlugin;

public final class EconomyService {
  private final Economy vault;
  private final PlayerPointsAPI points;

  public EconomyService(SuperArmsPlugin p) {
    Economy v = null;
    RegisteredServiceProvider<Economy> r =
        Bukkit.getServicesManager().getRegistration(Economy.class);
    if (r != null) v = r.getProvider();
    vault = v;
    PlayerPointsAPI pp = null;
    org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("PlayerPoints");
    if (pl instanceof PlayerPoints x) pp = new PlayerPointsAPI(x);
    points = pp;
  }

  public CurrencyProvider provider(String id) {
    if ("PLAYER_POINTS".equalsIgnoreCase(id) && points != null)
      return new CurrencyProvider() {
        public boolean has(Player p, double a) {
          return points.look(p.getUniqueId()) >= a;
        }

        public boolean withdraw(Player p, double a) {
          return points.take(p.getUniqueId(), (int) Math.ceil(a));
        }

        public boolean deposit(Player p, double a) {
          return points.give(p.getUniqueId(), (int) Math.ceil(a));
        }

        public String format(double a) {
          return String.valueOf((int) a) + " points";
        }
      };
    if (vault != null)
      return new CurrencyProvider() {
        public boolean has(Player p, double a) {
          return vault.has(p, a);
        }

        public boolean withdraw(Player p, double a) {
          return vault.withdrawPlayer(p, a).transactionSuccess();
        }

        public boolean deposit(Player p, double a) {
          return vault.depositPlayer(p, a).transactionSuccess();
        }

        public String format(double a) {
          return vault.format(a);
        }
      };
    return null;
  }
}
