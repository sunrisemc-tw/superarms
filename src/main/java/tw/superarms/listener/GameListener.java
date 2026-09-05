package tw.superarms.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tw.superarms.gui.GuiHolder;
import tw.superarms.service.AdminService;
import tw.superarms.service.ExpiryService;
import tw.superarms.service.ItemService;
import tw.superarms.service.ShopService;

public final class GameListener implements Listener {

    private final ShopService shop;
    private final AdminService admin;
    private final ExpiryService expiry;

    public GameListener(
            ShopService shop,
            AdminService admin,
            ExpiryService expiry
    ) {
        this.shop = shop;
        this.admin = admin;
        this.expiry = expiry;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        expiry.checkInventory(event.getPlayer());
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (holder.type()) {
            case SHOP -> shop.clickShop(player, holder, slot);
            case CONFIRM -> shop.clickConfirm(player, holder, slot);
            case ADMIN_HOME,
                    ADMIN_MANAGE,
                    ADMIN_LORE_REMOVE,
                    ADMIN_ENCHANT_ADD,
                    ADMIN_ENCHANT_REMOVE,
                    ADMIN_DELETE_CONFIRM,
                    ADMIN_PREVIEW -> admin.click(player, holder, slot);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder
                && holder.type() == GuiHolder.Type.CONFIRM) {
            shop.closeConfirm(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void chat(AsyncPlayerChatEvent event) {
        if (admin.acceptChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        admin.cancelInput(event.getPlayer().getUniqueId());
        shop.closeConfirm(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        admin.cancelInput(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (event.getItem() != null) {
            expiry.checkMainHand(event.getPlayer());
        }
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent event) {
        expiry.checkMainHand(event.getPlayer());
    }

    @EventHandler
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            expiry.checkMainHand(player);
        }
    }

    @EventHandler
    public void grind(PrepareGrindstoneEvent event) {
        if (ItemService.def(event.getInventory().getItem(0)) != null
                || ItemService.def(event.getInventory().getItem(1)) != null) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void anvil(PrepareAnvilEvent event) {
        if (ItemService.def(event.getInventory().getItem(0)) != null
                || ItemService.def(event.getInventory().getItem(1)) != null) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void enchant(EnchantItemEvent event) {
        if (ItemService.def(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }
}
