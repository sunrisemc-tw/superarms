package tw.superarms.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.WeaponDef;
import tw.superarms.data.WeaponRepository;
import tw.superarms.gui.GuiHolder;
import tw.superarms.util.TextUtil;

public final class AdminService {

    private static final int PAGE_SIZE = 45;

    private enum PromptType {
        RENAME,
        LORE_ADD,
        ENCHANT_LEVEL,
        PRICE,
        SELL_UNTIL,
        TIMEOUT,
        MATERIAL
    }

    private record Prompt(UUID weaponId, UUID worldId, PromptType type, String value) {
    }

    private final SuperArmsPlugin plugin;
    private final WeaponRepository repo;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public AdminService(SuperArmsPlugin plugin, WeaponRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public void openHome(Player player) {
        openHome(player, 0);
    }

    public void openHome(Player player, int requestedPage) {
        if (!checkPermission(player)) {
            return;
        }

        List<WeaponDef> weapons = new ArrayList<>(repo.all());
        int page = clampPage(requestedPage, weapons.size());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_HOME, null, page);
        Inventory inventory = createInventory(
                holder,
                54,
                "<gold>SuperArms 管理 <gray>(" + weapons.size() + ")"
        );

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, weapons.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, ItemService.preview(weapons.get(index)));
        }

        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "<yellow>上一頁"));
        }
        inventory.setItem(49, button(Material.EMERALD, "<green>新增武器"));
        if (end < weapons.size()) {
            inventory.setItem(53, button(Material.ARROW, "<yellow>下一頁"));
        }
        player.openInventory(inventory);
    }

    public void openManage(Player player, UUID weaponId) {
        if (!checkPermission(player)) {
            return;
        }

        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            openHome(player);
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_MANAGE, weaponId, 0);
        Inventory inventory = createInventory(holder, 54, "<gold>管理武器");
        inventory.setItem(13, ItemService.preview(weapon));
        inventory.setItem(10, button(Material.NAME_TAG, "<yellow>重新命名"));
        inventory.setItem(11, button(Material.WRITABLE_BOOK, "<green>新增 Lore"));
        inventory.setItem(12, button(Material.BOOK, "<red>移除 Lore"));
        inventory.setItem(19, button(Material.ENCHANTED_BOOK, "<green>新增附魔"));
        inventory.setItem(20, button(Material.GRINDSTONE, "<red>移除附魔"));
        inventory.setItem(
                21,
                button(
                        Material.GOLD_INGOT,
                        "<yellow>設定價格",
                        List.of(
                                "<gray>目前幣種: <white>" + weapon.currency(),
                                "<gray>目前金額: <white>" + weapon.price(),
                                "<gray>點擊切換幣種並輸入金額"
                        )
                )
        );
        inventory.setItem(
                22,
                button(
                        Material.CLOCK,
                        "<yellow>設定販售截止",
                        List.of(
                                "<gray>目前: <white>"
                                        + (weapon.sellUntil() == 0
                                        ? "不限"
                                        : TextUtil.date(weapon.sellUntil()))
                        )
                )
        );
        inventory.setItem(
                23,
                button(
                        Material.RECOVERY_COMPASS,
                        "<yellow>設定附魔時限",
                        List.of("<gray>目前: <white>" + TextUtil.duration(weapon.timeoutMillis()))
                )
        );
        inventory.setItem(
                24,
                button(
                        weapon.material(),
                        "<yellow>設定材質",
                        List.of("<gray>目前: <white>" + weapon.material().name())
                )
        );
        inventory.setItem(29, button(Material.PAPER, "<aqua>顯示 UUID"));
        inventory.setItem(
                30,
                button(
                        weapon.unbreakable() ? Material.OBSIDIAN : Material.COBBLESTONE,
                        "<yellow>不可破壞: " + onOff(weapon.unbreakable())
                )
        );
        inventory.setItem(
                31,
                button(
                        weapon.glow() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                        "<yellow>發光: " + onOff(weapon.glow())
                )
        );
        inventory.setItem(33, button(Material.BARRIER, "<red>刪除武器"));
        inventory.setItem(45, button(Material.ARROW, "<yellow>返回列表"));
        player.openInventory(inventory);
    }

    public void click(Player player, GuiHolder holder, int slot) {
        if (!checkPermission(player)) {
            player.closeInventory();
            return;
        }

        switch (holder.type()) {
            case ADMIN_HOME -> clickHome(player, holder.page(), slot);
            case ADMIN_MANAGE -> clickManage(player, holder.weaponId(), slot);
            case ADMIN_LORE_REMOVE -> clickLoreRemove(player, holder, slot);
            case ADMIN_ENCHANT_ADD -> clickEnchantAdd(player, holder, slot);
            case ADMIN_ENCHANT_REMOVE -> clickEnchantRemove(player, holder, slot);
            case ADMIN_DELETE_CONFIRM -> clickDeleteConfirm(player, holder.weaponId(), slot);
            default -> {
            }
        }
    }

    public boolean acceptChat(Player player, String input) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return false;
        }

        player.getScheduler().run(
                plugin,
                task -> applyPrompt(player, prompt, input),
                () -> prompts.remove(player.getUniqueId())
        );
        return true;
    }

    public void cancelInput(UUID playerId) {
        prompts.remove(playerId);
    }

    public void clearInputs() {
        prompts.clear();
    }

    private void clickHome(Player player, int page, int slot) {
        if (slot == 45 && page > 0) {
            openHome(player, page - 1);
            return;
        }
        if (slot == 49) {
            WeaponDef weapon = repo.create("<gold>新特武");
            player.sendMessage(
                    message("admin-created", "<green>已建立 %uuid%")
                            .replaceText(builder -> builder.matchLiteral("%uuid%")
                                    .replacement(weapon.id().toString()))
            );
            openManage(player, weapon.id());
            return;
        }
        if (slot == 53) {
            openHome(player, page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        List<WeaponDef> weapons = new ArrayList<>(repo.all());
        int index = page * PAGE_SIZE + slot;
        if (index < weapons.size()) {
            openManage(player, weapons.get(index).id());
        }
    }

    private void clickManage(Player player, UUID weaponId, int slot) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        switch (slot) {
            case 10 -> prompt(player, weaponId, PromptType.RENAME, null,
                    "<yellow>請輸入新名稱（支援 & 色碼與 MiniMessage）");
            case 11 -> prompt(player, weaponId, PromptType.LORE_ADD, null,
                    "<yellow>請輸入要新增的一行 Lore");
            case 12 -> openLoreRemove(player, weaponId, 0);
            case 19 -> openEnchantAdd(player, weaponId, 0);
            case 20 -> openEnchantRemove(player, weaponId, 0);
            case 21 -> {
                String currency = weapon.currency().equalsIgnoreCase("VAULT")
                        ? "PLAYER_POINTS"
                        : "VAULT";
                weapon.currency(currency);
                repo.save();
                prompt(
                        player,
                        weaponId,
                        PromptType.PRICE,
                        null,
                        "<yellow>幣種已切換為 " + currency + "，請輸入金額"
                );
            }
            case 22 -> prompt(
                    player,
                    weaponId,
                    PromptType.SELL_UNTIL,
                    null,
                    "<yellow>請輸入 yyyy-MM-dd HH:mm；輸入 0 或 - 表示不限"
            );
            case 23 -> prompt(
                    player,
                    weaponId,
                    PromptType.TIMEOUT,
                    null,
                    "<yellow>請輸入時限，例如 7d、24h；0 表示永久"
            );
            case 24 -> prompt(
                    player,
                    weaponId,
                    PromptType.MATERIAL,
                    null,
                    "<yellow>請輸入材質名稱，例如 NETHERITE_SWORD"
            );
            case 29 -> {
                player.closeInventory();
                player.sendMessage(TextUtil.component("<yellow>武器 UUID: <white>" + weaponId));
            }
            case 30 -> {
                weapon.unbreakable(!weapon.unbreakable());
                repo.save();
                openManage(player, weaponId);
            }
            case 31 -> {
                weapon.glow(!weapon.glow());
                repo.save();
                openManage(player, weaponId);
            }
            case 33 -> openDeleteConfirm(player, weaponId);
            case 45 -> openHome(player);
            default -> {
            }
        }
    }

    private void openLoreRemove(Player player, UUID weaponId, int requestedPage) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        int page = clampPage(requestedPage, weapon.lore().size());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_LORE_REMOVE, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>移除 Lore");
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, weapon.lore().size());
        for (int index = start; index < end; index++) {
            inventory.setItem(
                    index - start,
                    button(Material.PAPER, "<red>刪除第 " + (index + 1) + " 行",
                            List.of(weapon.lore().get(index)))
            );
        }
        addPageButtons(inventory, page, end < weapon.lore().size());
        player.openInventory(inventory);
    }

    private void clickLoreRemove(Player player, GuiHolder holder, int slot) {
        if (handlePagedBack(player, holder, slot, this::openLoreRemove)) {
            return;
        }
        WeaponDef weapon = repo.get(holder.weaponId());
        if (weapon == null || slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = holder.page() * PAGE_SIZE + slot;
        if (index < weapon.lore().size()) {
            weapon.lore().remove(index);
            repo.save();
            openManage(player, weapon.id());
        }
    }

    private void openEnchantAdd(Player player, UUID weaponId, int requestedPage) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        List<Enchantment> enchantments = availableEnchantments();
        int page = clampPage(requestedPage, enchantments.size());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_ENCHANT_ADD, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>新增附魔");
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, enchantments.size());
        for (int index = start; index < end; index++) {
            Enchantment enchantment = enchantments.get(index);
            NamespacedKey key = enchantment.getKey();
            inventory.setItem(
                    index - start,
                    button(
                            Material.ENCHANTED_BOOK,
                            "<aqua>" + key,
                            List.of("<gray>原版最高等級: <white>" + enchantment.getMaxLevel())
                    )
            );
        }
        addPageButtons(inventory, page, end < enchantments.size());
        player.openInventory(inventory);
    }

    private void clickEnchantAdd(Player player, GuiHolder holder, int slot) {
        if (handlePagedBack(player, holder, slot, this::openEnchantAdd)) {
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        List<Enchantment> enchantments = availableEnchantments();
        int index = holder.page() * PAGE_SIZE + slot;
        if (index >= enchantments.size()) {
            return;
        }
        String key = enchantmentStorageKey(enchantments.get(index).getKey());
        prompt(
                player,
                holder.weaponId(),
                PromptType.ENCHANT_LEVEL,
                key,
                "<yellow>請輸入 " + key + " 的附魔等級"
        );
    }

    private void openEnchantRemove(Player player, UUID weaponId, int requestedPage) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        List<Map.Entry<String, Integer>> enchantments = new ArrayList<>(
                weapon.enchantments().entrySet()
        );
        int page = clampPage(requestedPage, enchantments.size());
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_ENCHANT_REMOVE, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>移除附魔");
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, enchantments.size());
        for (int index = start; index < end; index++) {
            Map.Entry<String, Integer> entry = enchantments.get(index);
            inventory.setItem(
                    index - start,
                    button(
                            Material.ENCHANTED_BOOK,
                            "<red>移除 " + entry.getKey(),
                            List.of("<gray>目前等級: <white>" + entry.getValue())
                    )
            );
        }
        addPageButtons(inventory, page, end < enchantments.size());
        player.openInventory(inventory);
    }

    private void clickEnchantRemove(Player player, GuiHolder holder, int slot) {
        if (handlePagedBack(player, holder, slot, this::openEnchantRemove)) {
            return;
        }
        WeaponDef weapon = repo.get(holder.weaponId());
        if (weapon == null || slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        List<String> keys = new ArrayList<>(weapon.enchantments().keySet());
        int index = holder.page() * PAGE_SIZE + slot;
        if (index < keys.size()) {
            weapon.enchantments().remove(keys.get(index));
            repo.save();
            openManage(player, weapon.id());
        }
    }

    private void openDeleteConfirm(Player player, UUID weaponId) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_DELETE_CONFIRM, weaponId, 0);
        Inventory inventory = createInventory(holder, 27, "<red>確認刪除武器");
        inventory.setItem(13, ItemService.preview(weapon));
        inventory.setItem(11, button(Material.LIME_CONCRETE, "<green>取消"));
        inventory.setItem(15, button(Material.RED_CONCRETE, "<red>確認刪除"));
        player.openInventory(inventory);
    }

    private void clickDeleteConfirm(Player player, UUID weaponId, int slot) {
        if (slot == 11) {
            openManage(player, weaponId);
            return;
        }
        if (slot == 15) {
            repo.remove(weaponId);
            player.sendMessage(TextUtil.component("<green>已刪除武器 <white>" + weaponId));
            openHome(player);
        }
    }

    private void prompt(
            Player player,
            UUID weaponId,
            PromptType type,
            String value,
            String instruction
    ) {
        if (!checkPermission(player)) {
            return;
        }
        prompts.put(
                player.getUniqueId(),
                new Prompt(weaponId, player.getWorld().getUID(), type, value)
        );
        player.closeInventory();
        player.sendMessage(TextUtil.component(instruction));
        player.sendMessage(TextUtil.component("<gray>下一句聊天訊息只會作為管理輸入，不會公開。"));
    }

    private void applyPrompt(Player player, Prompt prompt, String input) {
        if (!player.isOnline()
                || !player.getWorld().getUID().equals(prompt.worldId())
                || !checkPermission(player)) {
            prompts.remove(player.getUniqueId());
            return;
        }

        WeaponDef weapon = repo.get(prompt.weaponId());
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }

        boolean valid = switch (prompt.type()) {
            case RENAME -> applyRename(weapon, input);
            case LORE_ADD -> applyLore(weapon, input);
            case ENCHANT_LEVEL -> applyEnchantLevel(weapon, prompt.value(), input);
            case PRICE -> applyPrice(weapon, input);
            case SELL_UNTIL -> applySellUntil(weapon, input);
            case TIMEOUT -> applyTimeout(weapon, input);
            case MATERIAL -> applyMaterial(weapon, input);
        };

        if (!valid) {
            prompts.put(player.getUniqueId(), prompt);
            player.sendMessage(TextUtil.component("<red>輸入格式不正確，請重新輸入。"));
            return;
        }

        repo.save();
        player.sendMessage(TextUtil.component("<green>設定已儲存。"));
        openManage(player, weapon.id());
    }

    private boolean applyRename(WeaponDef weapon, String input) {
        if (input.isBlank()) {
            return false;
        }
        weapon.name(TextUtil.mm(input));
        return true;
    }

    private boolean applyLore(WeaponDef weapon, String input) {
        weapon.lore().add(TextUtil.mm(input));
        return true;
    }

    private boolean applyEnchantLevel(WeaponDef weapon, String key, String input) {
        try {
            int level = Integer.parseInt(input.trim());
            if (level <= 0) {
                return false;
            }
            weapon.enchantments().put(key, level);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean applyPrice(WeaponDef weapon, String input) {
        try {
            double amount = Double.parseDouble(input.trim());
            if (!Double.isFinite(amount) || amount < 0) {
                return false;
            }
            weapon.price(amount);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean applySellUntil(WeaponDef weapon, String input) {
        String value = input.trim();
        if (value.isEmpty() || value.equals("0") || value.equals("-")) {
            weapon.sellUntil(0);
            return true;
        }
        OptionalLong timestamp = TextUtil.parseDateInput(value);
        if (timestamp.isEmpty()) {
            return false;
        }
        weapon.sellUntil(timestamp.getAsLong());
        return true;
    }

    private boolean applyTimeout(WeaponDef weapon, String input) {
        OptionalLong duration = TextUtil.parseDurationInput(input.trim());
        if (duration.isEmpty()) {
            return false;
        }
        weapon.timeoutMillis(duration.getAsLong());
        return true;
    }

    private boolean applyMaterial(WeaponDef weapon, String input) {
        Material material = Material.matchMaterial(input.trim().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem()) {
            return false;
        }
        weapon.material(material);
        return true;
    }

    private List<Enchantment> availableEnchantments() {
        Registry<Enchantment> registry = Bukkit.getRegistry(Enchantment.class);
        List<Enchantment> enchantments = new ArrayList<>();
        if (registry != null) {
            registry.forEach(enchantments::add);
        } else {
            enchantments.addAll(List.of(Enchantment.values()));
        }
        enchantments.sort(Comparator.comparing(enchantment -> enchantment.getKey().toString()));
        return enchantments;
    }

    private String enchantmentStorageKey(NamespacedKey key) {
        if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
            return key.getKey().toUpperCase(Locale.ROOT);
        }
        return key.toString();
    }

    private boolean handlePagedBack(
            Player player,
            GuiHolder holder,
            int slot,
            PageOpener opener
    ) {
        if (slot == 45 && holder.page() > 0) {
            opener.open(player, holder.weaponId(), holder.page() - 1);
            return true;
        }
        if (slot == 49) {
            openManage(player, holder.weaponId());
            return true;
        }
        if (slot == 53) {
            opener.open(player, holder.weaponId(), holder.page() + 1);
            return true;
        }
        return false;
    }

    private void addPageButtons(Inventory inventory, int page, boolean hasNext) {
        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "<yellow>上一頁"));
        }
        inventory.setItem(49, button(Material.BARRIER, "<yellow>返回管理"));
        if (hasNext) {
            inventory.setItem(53, button(Material.ARROW, "<yellow>下一頁"));
        }
    }

    private int clampPage(int requestedPage, int itemCount) {
        int lastPage = itemCount == 0 ? 0 : (itemCount - 1) / PAGE_SIZE;
        return Math.max(0, Math.min(requestedPage, lastPage));
    }

    private Inventory createInventory(
            GuiHolder holder,
            int size,
            String title
    ) {
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.component(title));
        holder.inventory(inventory);
        return inventory;
    }

    private ItemStack button(Material material, String name) {
        return button(material, name, List.of());
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.component(name));
        if (!lore.isEmpty()) {
            List<Component> components = lore.stream()
                    .map(TextUtil::component)
                    .toList();
            meta.lore(components);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String onOff(boolean enabled) {
        return enabled ? "<green>開" : "<red>關";
    }

    private boolean checkPermission(Player player) {
        if (player.hasPermission("superarms.admin")) {
            return true;
        }
        player.sendMessage(message("no-permission", "<red>你沒有權限"));
        return false;
    }

    private Component message(String key, String fallback) {
        return TextUtil.component(plugin.messages().getString(key, fallback));
    }

    @FunctionalInterface
    private interface PageOpener {
        void open(Player player, UUID weaponId, int page);
    }
}
