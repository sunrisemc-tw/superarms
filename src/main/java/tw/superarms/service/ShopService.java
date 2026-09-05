package tw.superarms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.WeaponDef;
import tw.superarms.data.WeaponRepository;
import tw.superarms.economy.CurrencyProvider;
import tw.superarms.economy.EconomyService;
import tw.superarms.gui.GuiHolder;
import tw.superarms.util.TextUtil;

public final class ShopService {

    private final SuperArmsPlugin plugin;
    private final WeaponRepository repo;
    private final EconomyService economy;
    private final ExpiryService expiry;
    private final Map<UUID, UUID> confirms = new ConcurrentHashMap<>();

    public ShopService(
            SuperArmsPlugin plugin,
            WeaponRepository repo,
            EconomyService economy,
            ExpiryService expiry
    ) {
        this.plugin = plugin;
        this.repo = repo;
        this.economy = economy;
        this.expiry = expiry;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        if (!checkBuyPermission(player)) {
            return;
        }

        List<WeaponDef> weapons = forSaleWeapons();
        int rows = Math.max(2, Math.min(6, plugin.getConfig().getInt("gui.shop-rows", 6)));
        int pageSize = (rows - 1) * 9;
        int page = clampPage(requestedPage, weapons.size(), pageSize);
        GuiHolder holder = new GuiHolder(
                GuiHolder.Type.SHOP,
                null,
                page,
                String.valueOf(pageSize)
        );
        Inventory inventory = createInventory(
                holder,
                rows * 9,
                plugin.getConfig().getString("gui.shop-title", "<gold>特武商城")
        );
        int start = page * pageSize;
        int end = Math.min(start + pageSize, weapons.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, shopIcon(weapons.get(index)));
        }
        if (page > 0) {
            inventory.setItem(pageSize, button(Material.ARROW, "<yellow>上一頁"));
        }
        if (end < weapons.size()) {
            inventory.setItem(rows * 9 - 1, button(Material.ARROW, "<yellow>下一頁"));
        }
        player.openInventory(inventory);
    }

    public void clickShop(Player player, GuiHolder holder, int slot) {
        if (!checkBuyPermission(player)) {
            player.closeInventory();
            return;
        }
        int pageSize = Integer.parseInt(holder.value());
        int nextSlot = pageSize + 8;
        if (slot == pageSize && holder.page() > 0) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == nextSlot) {
            open(player, holder.page() + 1);
            return;
        }
        if (slot < 0 || slot >= pageSize) {
            return;
        }

        List<WeaponDef> weapons = forSaleWeapons();
        int index = holder.page() * pageSize + slot;
        if (index < weapons.size()) {
            openConfirm(player, weapons.get(index));
        }
    }

    public void clickConfirm(Player player, GuiHolder holder, int slot) {
        UUID expectedId = confirms.get(player.getUniqueId());
        if (expectedId == null || !expectedId.equals(holder.weaponId())) {
            player.closeInventory();
            return;
        }
        if (slot == 15) {
            confirms.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (slot != 11) {
            return;
        }
        confirms.remove(player.getUniqueId());
        WeaponDef weapon = repo.get(holder.weaponId());
        purchase(player, weapon);
    }

    public void buyDirect(Player player, WeaponDef weapon) {
        purchase(player, weapon);
    }

    public boolean canBuy(Player player) {
        return checkBuyPermission(player);
    }

    public void closeConfirm(UUID playerId) {
        confirms.remove(playerId);
    }

    public void clearConfirms() {
        confirms.clear();
    }

    private void openConfirm(Player player, WeaponDef weapon) {
        if (!checkBuyPermission(player)) {
            return;
        }
        if (!weapon.isForSale(System.currentTimeMillis())) {
            player.sendMessage(message("weapon-expired-sell", "<gray>該武器已停止販售"));
            open(player);
            return;
        }

        confirms.put(player.getUniqueId(), weapon.id());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.CONFIRM, weapon.id(), 0);
        Inventory inventory = createInventory(
                holder,
                27,
                plugin.getConfig().getString("gui.confirm-title", "<gold>確認購買")
        );
        inventory.setItem(13, shopIcon(weapon));
        inventory.setItem(11, button(Material.LIME_CONCRETE, "<green>確認購買"));
        inventory.setItem(15, button(Material.RED_CONCRETE, "<red>取消"));
        player.openInventory(inventory);
    }

    private void purchase(Player player, WeaponDef weapon) {
        if (!checkBuyPermission(player)) {
            return;
        }
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }
        if (!weapon.isForSale(System.currentTimeMillis())) {
            player.sendMessage(message("weapon-expired-sell", "<gray>該武器已停止販售"));
            return;
        }

        CurrencyProvider provider = economy.provider(weapon.currency());
        if (provider == null || !provider.has(player, weapon.price())) {
            player.sendMessage(message("buy-fail", "<red>餘額不足或經濟服務不可用"));
            return;
        }
        if (!hasSpace(player)) {
            player.sendMessage(message("buy-full-inventory", "<red>背包已滿，購買取消"));
            return;
        }
        if (!provider.withdraw(player, weapon.price())) {
            player.sendMessage(message("buy-fail", "<red>扣款失敗"));
            return;
        }

        ItemStack item = ItemService.create(weapon, player.getUniqueId());
        player.getInventory().addItem(item);
        expiry.schedule(player, item);
        player.sendMessage(message("buy-success", "<green>購買成功！已放入背包"));
        if (weapon.announce()
                && plugin.getConfig().getBoolean("buy.broadcast.enabled", true)) {
            Bukkit.broadcast(
                    TextUtil.component(
                            plugin.getConfig()
                                    .getString(
                                            "buy.broadcast.format",
                                            "<yellow>%player% 購買了 %weapon%！"
                                    )
                                    .replace("%player%", player.getName())
                                    .replace("%weapon%", TextUtil.mm(weapon.name()))
                    )
            );
        }
        plugin.getLogger().info(player.getName() + " bought " + weapon.id());
        player.closeInventory();
    }

    private List<WeaponDef> forSaleWeapons() {
        long now = System.currentTimeMillis();
        return repo.all().stream()
                .filter(weapon -> weapon.isForSale(now))
                .toList();
    }

    private ItemStack shopIcon(WeaponDef weapon) {
        ItemStack item = ItemService.preview(weapon);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null
                ? new ArrayList<>()
                : new ArrayList<>(meta.lore());
        if (weapon.timeoutMillis() > 0) {
            lore.add(TextUtil.component(
                    "<yellow>附魔時限: <white>" + TextUtil.duration(weapon.timeoutMillis())
            ));
        }
        CurrencyProvider provider = economy.provider(weapon.currency());
        String price = provider == null
                ? weapon.currency() + " " + weapon.price()
                : provider.format(weapon.price());
        lore.add(TextUtil.component("<yellow>價格: " + price));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.component(name));
        item.setItemMeta(meta);
        return item;
    }

    private Inventory createInventory(GuiHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.component(title));
        holder.inventory(inventory);
        return inventory;
    }

    private int clampPage(int requestedPage, int itemCount, int pageSize) {
        int lastPage = itemCount == 0 ? 0 : (itemCount - 1) / pageSize;
        return Math.max(0, Math.min(requestedPage, lastPage));
    }

    private boolean hasSpace(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private boolean checkBuyPermission(Player player) {
        boolean required = plugin.getConfig().getBoolean("buy.permission-required", false);
        if (!required || player.hasPermission("superarms.buy")) {
            return true;
        }
        player.sendMessage(message("no-permission", "<red>你沒有權限"));
        return false;
    }

    private Component message(String key, String fallback) {
        return TextUtil.component(plugin.messages().getString(key, fallback));
    }
}
