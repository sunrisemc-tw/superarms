package tw.superarms.service;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.WeaponDef;
import tw.superarms.data.WeaponRepository;
import tw.superarms.util.TextUtil;

public final class ExpiryService {

    private final SuperArmsPlugin plugin;
    private final WeaponRepository repo;

    public ExpiryService(SuperArmsPlugin plugin, WeaponRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public void schedule(Player player, ItemStack item) {
        long expiresAt = ItemService.expires(item);
        if (expiresAt <= 0) {
            return;
        }
        long ticks = Math.max(1, (expiresAt - System.currentTimeMillis()) / 50);
        Location location = player.getLocation();
        plugin.getServer().getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> player.getScheduler().run(
                        plugin,
                        entityTask -> rewriteInventoryNow(player),
                        () -> {
                        }
                ),
                ticks
        );
    }

    public void checkInventory(Player player) {
        player.getScheduler().run(
                plugin,
                task -> rewriteInventoryNow(player),
                () -> {
                }
        );
    }

    public void checkMainHand(Player player) {
        rewriteMainHandNow(player);
    }

    private void rewriteInventoryNow(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack rewritten = rewriteIfExpired(item);
            if (rewritten != null) {
                inventory.setItem(slot, rewritten);
                notifyExpired(player);
            }
        }
    }

    private void rewriteMainHandNow(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack rewritten = rewriteIfExpired(inventory.getItemInMainHand());
        if (rewritten != null) {
            inventory.setItemInMainHand(rewritten);
            notifyExpired(player);
        }
    }

    private ItemStack rewriteIfExpired(ItemStack item) {
        long expiresAt = ItemService.expires(item);
        if (expiresAt <= 0 || expiresAt > System.currentTimeMillis()) {
            return null;
        }
        UUID definitionId = ItemService.def(item);
        if (definitionId == null) {
            return null;
        }
        WeaponDef weapon = repo.get(definitionId);
        if (weapon == null) {
            return null;
        }
        return ItemService.expire(item, weapon);
    }

    private void notifyExpired(Player player) {
        player.sendMessage(
                TextUtil.component(
                        plugin.messages().getString(
                                "expired-notify",
                                "<yellow>你持有的武器附魔已失效"
                        )
                )
        );
    }
}
