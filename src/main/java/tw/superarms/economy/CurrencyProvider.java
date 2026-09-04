package tw.superarms.economy;

import org.bukkit.entity.Player;

public interface CurrencyProvider {
    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean deposit(Player player, double amount);

    String format(double amount);
}
