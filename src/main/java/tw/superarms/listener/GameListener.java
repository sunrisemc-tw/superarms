package tw.superarms.listener;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import tw.superarms.*;
import tw.superarms.data.*;
import tw.superarms.service.*;
import tw.superarms.util.TextUtil;

public final class GameListener implements Listener {
  private final SuperArmsPlugin plugin;
  private final WeaponRepository repo;
  private final ShopService shop;
  private final ExpiryService expiry;

  public GameListener(SuperArmsPlugin p, WeaponRepository r, ShopService s, ExpiryService e) {
    plugin = p;
    repo = r;
    shop = s;
    expiry = e;
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    expiry.check(e.getPlayer());
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) return;
    String t = e.getView().getTitle();
    if (t.contains("特武商城")) {
      e.setCancelled(true);
      if (e.getRawSlot() < 45) shop.clickShop(p, e.getRawSlot());
    } else if (t.contains("確認購買")) {
      e.setCancelled(true);
      shop.clickConfirm(p, e.getRawSlot());
    } else if (t.contains("SuperArms 管理")) {
      e.setCancelled(true);
      if (e.getRawSlot() == 11) {
        WeaponDef d = repo.create("<gold>新特武");
        p.sendMessage(
            TextUtil.component(
                plugin
                    .messages()
                    .getString("admin-created", "<green>已建立 %uuid%")
                    .replace("%uuid%", d.id().toString())));
        p.closeInventory();
      } else if (e.getRawSlot() == 15) {
        p.closeInventory();
        for (WeaponDef d : repo.all())
          p.sendMessage(
              TextUtil.component(
                  plugin
                      .messages()
                      .getString("list-line", "<yellow>%uuid% <white>%name%")
                      .replace("%uuid%", d.id().toString())
                      .replace("%name%", d.name())));
      }
    }
  }

  @EventHandler
  public void close(InventoryCloseEvent e) {
    if (e.getPlayer() instanceof Player p) shop.confirms().remove(p.getUniqueId());
  }

  @EventHandler
  public void use(PlayerInteractEvent e) {
    if (e.getItem() != null) expiry.check(e.getPlayer());
  }

  @EventHandler
  public void breakBlock(BlockBreakEvent e) {
    expiry.check(e.getPlayer());
  }

  @EventHandler
  public void damage(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p) expiry.check(p);
  }

  @EventHandler
  public void grind(PrepareGrindstoneEvent e) {
    if (ItemService.def(e.getInventory().getItem(0)) != null
        || ItemService.def(e.getInventory().getItem(1)) != null) e.setResult(null);
  }

  @EventHandler
  public void anvil(PrepareAnvilEvent e) {
    if (ItemService.def(e.getInventory().getItem(0)) != null
        || ItemService.def(e.getInventory().getItem(1)) != null) e.setResult(null);
  }

  @EventHandler
  public void enchant(EnchantItemEvent e) {
    if (ItemService.def(e.getItem()) != null) e.setCancelled(true);
  }
}
