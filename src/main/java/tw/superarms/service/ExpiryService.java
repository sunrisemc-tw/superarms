package tw.superarms.service;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import tw.superarms.*;
import tw.superarms.data.*;

public final class ExpiryService {
  private final SuperArmsPlugin plugin;
  private final WeaponRepository repo;

  public ExpiryService(SuperArmsPlugin p, WeaponRepository r) {
    plugin = p;
    repo = r;
  }

  public void schedule(Player p, ItemStack item) {
    long e = ItemService.expires(item);
    if (e <= 0) return;
    long ticks = Math.max(1, (e - System.currentTimeMillis()) / 50);
    Location l = p.getLocation();
    plugin
        .getServer()
        .getRegionScheduler()
        .runDelayed(
            plugin, l, task -> p.getScheduler().run(plugin, t -> rewriteNow(p), () -> {}), ticks);
  }

  public void check(Player p) {
    p.getScheduler()
        .run(
            plugin,
            t -> {
              rewriteNow(p);
            },
            () -> {});
  }

  private void rewriteNow(Player p) {
    PlayerInventory inv = p.getInventory();
    for (int n = 0; n < inv.getSize(); n++) {
      ItemStack i = inv.getItem(n);
      if (ItemService.expires(i) > 0 && ItemService.expires(i) <= System.currentTimeMillis()) {
        UUID id = ItemService.def(i);
        WeaponDef d = repo.get(id);
        if (d != null) {
          inv.setItem(n, ItemService.expire(i, d));
          p.sendMessage(
              tw.superarms.util.TextUtil.component(
                  plugin.messages().getString("expired-notify", "<yellow>你持有的武器附魔已失效")));
        }
      }
    }
  }
}
