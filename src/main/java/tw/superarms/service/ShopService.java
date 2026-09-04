package tw.superarms.service;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import tw.superarms.*;
import tw.superarms.data.*;
import tw.superarms.economy.*;
import tw.superarms.util.TextUtil;

public final class ShopService {
  private final SuperArmsPlugin plugin;
  private final WeaponRepository repo;
  private final EconomyService economy;
  private final ExpiryService expiry;
  private final Map<UUID, WeaponDef> confirms = new HashMap<>();

  public ShopService(SuperArmsPlugin p, WeaponRepository r, EconomyService e, ExpiryService x) {
    plugin = p;
    repo = r;
    economy = e;
    expiry = x;
  }

  public void open(Player p) {
    Inventory inv =
        Bukkit.createInventory(
            null,
            54,
            TextUtil.component(plugin.getConfig().getString("gui.shop-title", "<gold>特武商城")));
    int slot = 0;
    for (WeaponDef d : repo.all()) {
      if (d.sellUntil() > 0 && d.sellUntil() <= System.currentTimeMillis() || slot >= 45) continue;
      ItemStack i = new ItemStack(d.material());
      ItemMeta m = i.getItemMeta();
      m.displayName(TextUtil.component(d.name()));
      List<net.kyori.adventure.text.Component> l = new ArrayList<>();
      for (String x : d.lore()) l.add(TextUtil.component(x));
      CurrencyProvider cp = economy.provider(d.currency());
      l.add(TextUtil.component("<yellow>價格: " + (cp == null ? d.price() : cp.format(d.price()))));
      m.lore(l);
      i.setItemMeta(m);
      inv.setItem(slot++, i);
    }
    p.openInventory(inv);
  }

  public void clickShop(Player p, int slot) {
    int n = 0;
    for (WeaponDef d : repo.all()) {
      if (d.sellUntil() > 0 && d.sellUntil() <= System.currentTimeMillis()) continue;
      if (n++ == slot) {
        confirms.put(p.getUniqueId(), d);
        openConfirm(p, d);
        return;
      }
    }
  }

  private void openConfirm(Player p, WeaponDef d) {
    Inventory i =
        Bukkit.createInventory(
            null,
            27,
            TextUtil.component(plugin.getConfig().getString("gui.confirm-title", "<gold>確認購買")));
    i.setItem(13, ItemService.create(d, p.getUniqueId()));
    ItemStack ok = new ItemStack(Material.LIME_CONCRETE);
    ItemMeta m = ok.getItemMeta();
    m.displayName(TextUtil.component("<green>確認購買"));
    ok.setItemMeta(m);
    i.setItem(11, ok);
    ItemStack no = new ItemStack(Material.RED_CONCRETE);
    m = no.getItemMeta();
    m.displayName(TextUtil.component("<red>取消"));
    no.setItemMeta(m);
    i.setItem(15, no);
    p.openInventory(i);
  }

  public void clickConfirm(Player p, int slot) {
    WeaponDef d = confirms.get(p.getUniqueId());
    if (d == null) return;
    if (slot == 15) {
      confirms.remove(p.getUniqueId());
      p.closeInventory();
      return;
    }
    if (slot != 11) return;
    confirms.remove(p.getUniqueId());
    CurrencyProvider cp = economy.provider(d.currency());
    if (cp == null || !cp.has(p, d.price())) {
      p.sendMessage(msg("buy-fail", "<red>餘額不足"));
      return;
    }
    if (!hasSpace(p)) {
      p.sendMessage(msg("buy-full-inventory", "<red>背包已滿，買取消"));
      return;
    }
    if (!cp.withdraw(p, d.price())) {
      p.sendMessage(msg("buy-fail", "<red>扣款失敗"));
      return;
    }
    ItemStack item = ItemService.create(d, p.getUniqueId());
    p.getInventory().addItem(item);
    expiry.schedule(p, item);
    p.sendMessage(msg("buy-success", "<green>購買成功！已放入背包"));
    if (d.announce() && plugin.getConfig().getBoolean("buy.broadcast.enabled", true))
      Bukkit.broadcast(
          TextUtil.component(
              plugin
                  .getConfig()
                  .getString("buy.broadcast.format", "<yellow>%player% 購買了 %weapon%！")
                  .replace("%player%", p.getName())
                  .replace("%weapon%", TextUtil.mm(d.name()))));
    plugin.getLogger().info(p.getName() + " bought " + d.id());
    p.closeInventory();
  }

  private boolean hasSpace(Player p) {
    for (ItemStack i : p.getInventory().getStorageContents())
      if (i == null || i.getType() == Material.AIR) return true;
    return false;
  }

  private net.kyori.adventure.text.Component msg(String k, String def) {
    return TextUtil.component(plugin.messages().getString(k, def));
  }

  public Map<UUID, WeaponDef> confirms() {
    return confirms;
  }
}
