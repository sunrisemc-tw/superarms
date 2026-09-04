package tw.superarms.economy;

import org.bukkit.entity.Player;

public interface CurrencyProvider {
  boolean has(Player p, double a);

  boolean withdraw(Player p, double a);

  boolean deposit(Player p, double a);

  String format(double a);
}
