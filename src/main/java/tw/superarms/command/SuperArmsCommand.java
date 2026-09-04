package tw.superarms.command;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import tw.superarms.*;
import tw.superarms.data.*;
import tw.superarms.economy.*;
import tw.superarms.service.*;
import tw.superarms.util.TextUtil;

public final class SuperArmsCommand implements CommandExecutor, TabCompleter {
  private final SuperArmsPlugin plugin;
  private final WeaponRepository repo;
  private final ShopService shop;
  private final ExpiryService expiry;
  private final EconomyService economy;

  public SuperArmsCommand(
      SuperArmsPlugin p, WeaponRepository r, ShopService s, ExpiryService e, EconomyService c) {
    plugin = p;
    repo = r;
    shop = s;
    expiry = e;
    economy = c;
  }

  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
    if (a.length == 0) {
      if (sender instanceof Player p && p.hasPermission("superarms.admin")) admin(p);
      else sender.sendMessage(msg("no-permission", "<red>你沒有權限"));
      return true;
    }
    String sub = a[0].toLowerCase();
    if (sub.equals("shop")) {
      if (sender instanceof Player p) shop.open(p);
      return true;
    }
    if (sub.equals("buy") && a.length > 1 && sender instanceof Player p) {
      try {
        WeaponDef d = repo.get(UUID.fromString(a[1]));
        if (d == null) {
          p.sendMessage(msg("not-found", "<red>找不到該武器"));
          return true;
        }
        shop.confirms().put(p.getUniqueId(), d);
        shop.clickConfirm(p, 11);
      } catch (Exception ex) {
        p.sendMessage(msg("not-found", "<red>找不到該武器"));
      }
      return true;
    }
    if (sub.equals("list")) {
      for (WeaponDef d : repo.all())
        sender.sendMessage(
            TextUtil.component(
                plugin
                    .messages()
                    .getString(
                        "list-detail", "<yellow>%uuid% | %name% | %currency% %price% | %sell%")
                    .replace("%uuid%", d.id().toString())
                    .replace("%name%", d.name())
                    .replace("%currency%", d.currency())
                    .replace("%price%", String.valueOf(d.price()))
                    .replace(
                        "%sell%",
                        d.sellUntil() == 0 ? "unlimited" : TextUtil.date(d.sellUntil()))));
      return true;
    }
    if (sub.equals("reload") && sender.hasPermission("superarms.admin")) {
      plugin.reloadConfig();
      plugin.reloadMessages();
      repo.load();
      sender.sendMessage(msg("reload", "<green>已重新載入"));
      return true;
    }
    if (sub.equals("arms")
        && a.length > 1
        && sender instanceof Player p
        && p.hasPermission("superarms.admin")) {
      try {
        WeaponDef d = repo.get(UUID.fromString(a[1]));
        if (d != null) p.getInventory().addItem(ItemService.create(d, p.getUniqueId()));
      } catch (Exception ignored) {
      }
      return true;
    }
    if (sender instanceof Player p && p.hasPermission("superarms.admin")) {
      try {
        UUID id = UUID.fromString(a[0]);
        if (repo.get(id) != null) shop.open(p);
      } catch (Exception ignored) {
      }
    }
    return true;
  }

  private void admin(Player p) {
    Inventory i = Bukkit.createInventory(null, 27, TextUtil.component("<gold>SuperArms 管理"));
    ItemStack add = new ItemStack(Material.EMERALD);
    ItemMeta m = add.getItemMeta();
    m.displayName(TextUtil.component("<green>新增武器"));
    add.setItemMeta(m);
    i.setItem(11, add);
    ItemStack list = new ItemStack(Material.BOOK);
    m = list.getItemMeta();
    m.displayName(TextUtil.component("<yellow>武器列表"));
    list.setItemMeta(m);
    i.setItem(15, list);
    p.openInventory(i);
  }

  private net.kyori.adventure.text.Component msg(String k, String d) {
    return TextUtil.component(plugin.messages().getString(k, d));
  }

  public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
    if (a.length == 1) return Arrays.asList("shop", "buy", "list", "reload", "arms");
    if (a.length == 2 && a[0].equalsIgnoreCase("buy")) {
      List<String> x = new ArrayList<>();
      for (WeaponDef d : repo.all()) x.add(d.id().toString());
      return x;
    }
    return List.of();
  }
}
