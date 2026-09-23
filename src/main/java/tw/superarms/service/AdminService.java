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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.WeaponDef;
import tw.superarms.data.WeaponRepository;
import tw.superarms.gui.GuiHolder;
import tw.superarms.util.EnchantNames;
import tw.superarms.util.TextUtil;

public final class AdminService {

    private static final int PAGE_SIZE = 45;

    /** 6 列 GUI 內容區每頁 4列 x 7格 = 28（跳過外圈玻璃框）。 */
    private static final int CONTENT_PAGE_SIZE = 28;

    /** 玩家背包中可當匯入來源的格位：0-35 主背包 + 40 副手。 */
    private static final int[] IMPORT_SLOTS = buildImportSlots();

    private static int[] buildImportSlots() {
        int[] slots = new int[37];
        for (int index = 0; index < 36; index++) {
            slots[index] = index;
        }
        slots[36] = 40;
        return slots;
    }

    /** 本系統自己的 Lore 標記：匯入時要跳過，避免把「已失效」帶進新模板。 */
    private static final List<String> SYSTEM_LORE_MARKERS = List.of("附魔有效至", "附魔已失效");

    /** 把內容序號 n 對映到 54 格 GUI 的 slot（內容區跳過左右框）。 */
    private static int contentSlot(int n) {
        int row = n / 7;
        int col = n % 7;
        return (row + 1) * 9 + (col + 1);
    }

    /** 把 slot 對映回內容序號；若 slot 在邊框/底列則回 -1。 */
    private static int contentIndex(int slot) {
        if (slot < 0 || slot >= 45) {
            return -1;
        }
        int row = slot / 9;
        int col = slot % 9;
        if (row == 0 || row == 5 || col == 0 || col == 8) {
            return -1;
        }
        return (row - 1) * 7 + (col - 1);
    }

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
        int page = clampPage(requestedPage, weapons.size(), CONTENT_PAGE_SIZE);
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_HOME, null, page);
        Inventory inventory = createInventory(
                holder,
                54,
                "<gold>SuperArms 管理 <gray>(" + weapons.size() + ")"
        );
        fillFrame(inventory);

        int start = page * CONTENT_PAGE_SIZE;
        int end = Math.min(start + CONTENT_PAGE_SIZE, weapons.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(contentSlot(index - start), ItemService.preview(weapons.get(index)));
        }

        // 底列：上一頁 / 新增武器 / 下一頁
        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "<yellow>上一頁"));
        }
        inventory.setItem(49, button(Material.EMERALD, "<green>新增武器"));
        inventory.setItem(
                51,
                button(
                        Material.HOPPER,
                        "<aqua>匯入背包武器",
                        List.of("<gray>從自己背包挑一件物品，直接變成新特武模板")
                )
        );
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
        fillFrame(inventory);

        // 中央：武器本體預覽（hover 看名稱/Lore）
        inventory.setItem(13, ItemService.preview(weapon));
        // 第一列：名稱/Lore（左）｜武器｜附魔/材質（右）
        inventory.setItem(10, button(Material.NAME_TAG, "<yellow>重新命名"));
        inventory.setItem(11, button(Material.WRITABLE_BOOK, "<green>新增 Lore"));
        inventory.setItem(12, button(Material.BOOK, "<red>移除 Lore"));
        inventory.setItem(14, button(Material.ENCHANTED_BOOK, "<green>新增附魔"));
        List<String> enchantLore = new ArrayList<>();
        if (weapon.enchantments().isEmpty()) {
            enchantLore.add("<gray>目前沒有附魔");
        } else {
            enchantLore.add("<gray>目前已設定：");
            for (Map.Entry<String, Integer> entry : weapon.enchantments().entrySet()) {
                enchantLore.add(
                        "<dark_gray>・<white>" + EnchantNames.zh(entry.getKey())
                                + " <gray>" + entry.getValue() + " 級"
                );
            }
        }
        inventory.setItem(15, button(Material.GRINDSTONE, "<red>移除附魔", enchantLore));
        inventory.setItem(
                16,
                button(
                        weapon.material() == null ? Material.DIAMOND_SWORD : weapon.material(),
                        "<yellow>設定材質",
                        List.of("<gray>目前: <white>"
                                + (weapon.material() == null ? "DIAMOND_SWORD" : weapon.material().name()))
                )
        );
        // 第二列：價格/販售截止/時限（左）｜不可破壞/發光/預覽（右）
        inventory.setItem(
                19,
                button(
                        Material.GOLD_INGOT,
                        "<yellow>設定價格",
                        List.of(
                                "<gray>幣種: <white>" + weapon.currency(),
                                "<gray>金額: <white>" + weapon.price(),
                                "<gray>點擊切換幣種並輸入金額"
                        )
                )
        );
        inventory.setItem(
                20,
                button(
                        Material.CLOCK,
                        "<yellow>販售截止",
                        List.of(
                                "<gray>目前: <white>"
                                        + (weapon.sellUntil() == 0
                                        ? "不限"
                                        : TextUtil.date(weapon.sellUntil())),
                                "<gray>點擊設定 yyyy-MM-dd HH:mm"
                        )
                )
        );
        inventory.setItem(
                21,
                button(
                        Material.RECOVERY_COMPASS,
                        "<yellow>附魔時限",
                        List.of(
                                "<gray>目前: <white>" + TextUtil.duration(weapon.timeoutMillis()),
                                "<gray>點擊設定，例 7d / 24h / 0=永久"
                        )
                )
        );
        inventory.setItem(
                23,
                button(
                        weapon.unbreakable() ? Material.OBSIDIAN : Material.COBBLESTONE,
                        "<yellow>不可破壞: " + onOff(weapon.unbreakable())
                )
        );
        inventory.setItem(
                24,
                button(
                        weapon.glow() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                        "<yellow>發光: " + onOff(weapon.glow())
                )
        );
        inventory.setItem(
                25,
                button(
                        Material.ITEM_FRAME,
                        "<aqua>預覽成品",
                        List.of("<gray>查看玩家購買後拿到的實際物品")
                )
        );
        // 第三列：取得成品 / UUID
        inventory.setItem(
                28,
                button(
                        Material.CHEST_MINECART,
                        "<green>取得成品",
                        List.of("<gray>拿一把成品到背包（含 PDC，可測到期）")
                )
        );
        inventory.setItem(29, button(Material.PAPER, "<aqua>顯示 UUID"));
        inventory.setItem(
                34,
                button(
                        Material.ANVIL,
                        "<aqua>從手上匯入覆蓋",
                        List.of(
                                "<gray>把主手拿著的物品外觀覆蓋到這把特武",
                                "<gray>（材質 / 名稱 / Lore / 附魔 / 發光）",
                                "<dark_gray>價格、時限、販售截止不會被改"
                        )
                )
        );
        // 底列：返回（左）｜刪除（右）
        inventory.setItem(45, button(Material.ARROW, "<yellow>返回列表"));
        inventory.setItem(
                53,
                button(
                        Material.BARRIER,
                        "<red>刪除武器",
                        List.of("<dark_red>刪除後無法復原")
                )
        );
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
            case ADMIN_PREVIEW -> clickPreview(player, holder.weaponId(), slot);
            case ADMIN_IMPORT -> clickImport(player, holder, slot);
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
        if (slot == 51) {
            openImport(player);
            return;
        }
        int content = contentIndex(slot);
        if (content < 0) {
            return;
        }

        List<WeaponDef> weapons = new ArrayList<>(repo.all());
        int index = page * CONTENT_PAGE_SIZE + content;
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
            case 14 -> openEnchantAdd(player, weaponId, 0);
            case 15 -> openEnchantRemove(player, weaponId, 0);
            case 16 -> prompt(
                    player,
                    weaponId,
                    PromptType.MATERIAL,
                    null,
                    "<yellow>請輸入材質名稱，例如 NETHERITE_SWORD"
            );
            case 19 -> {
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
            case 20 -> prompt(
                    player,
                    weaponId,
                    PromptType.SELL_UNTIL,
                    null,
                    "<yellow>請輸入 yyyy-MM-dd HH:mm；輸入 0 或 - 表示不限"
            );
            case 21 -> prompt(
                    player,
                    weaponId,
                    PromptType.TIMEOUT,
                    null,
                    "<yellow>請輸入時限，例如 7d、24h；0 表示永久"
            );
            case 23 -> {
                weapon.unbreakable(!weapon.unbreakable());
                repo.save();
                openManage(player, weaponId);
            }
            case 24 -> {
                weapon.glow(!weapon.glow());
                repo.save();
                openManage(player, weaponId);
            }
            case 25 -> openPreview(player, weaponId);
            case 28 -> giveItem(player, weaponId);
            case 34 -> importFromMainHand(player, weaponId);
            case 29 -> {
                player.closeInventory();
                player.sendMessage(TextUtil.component("<yellow>武器 UUID: <white>" + weaponId));
            }
            case 45 -> openHome(player);
            case 53 -> openDeleteConfirm(player, weaponId);
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

        int page = clampPage(requestedPage, weapon.lore().size(), CONTENT_PAGE_SIZE);
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_LORE_REMOVE, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>移除 Lore");
        fillFrame(inventory);
        int start = page * CONTENT_PAGE_SIZE;
        int end = Math.min(start + CONTENT_PAGE_SIZE, weapon.lore().size());
        for (int index = start; index < end; index++) {
            inventory.setItem(
                    contentSlot(index - start),
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
        int content = contentIndex(slot);
        if (content < 0) {
            return;
        }
        WeaponDef weapon = repo.get(holder.weaponId());
        if (weapon == null) {
            openHome(player);
            return;
        }
        int index = holder.page() * CONTENT_PAGE_SIZE + content;
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

        // 顯示全部附魔（不依材質過濾），不適用的用灰字標註但不擋
        Material material = weapon.material() == null
                ? Material.DIAMOND_SWORD
                : weapon.material();
        List<Enchantment> enchantments = availableEnchantments();

        int page = clampPage(requestedPage, enchantments.size(), CONTENT_PAGE_SIZE);
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_ENCHANT_ADD, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>新增附魔");
        fillFrame(inventory);
        int start = page * CONTENT_PAGE_SIZE;
        int end = Math.min(start + CONTENT_PAGE_SIZE, enchantments.size());
        for (int index = start; index < end; index++) {
            Enchantment enchantment = enchantments.get(index);
            NamespacedKey key = enchantment.getKey();
            Integer current = weapon.enchantments().get(enchantmentStorageKey(key));
            boolean applicable = enchantment.canEnchantItem(new ItemStack(material));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>最高等級: <white>" + enchantment.getMaxLevel());
            if (!applicable) {
                lore.add("<dark_gray>⚠ 不適用於 " + material.name() + "（附了可能沒效果）");
            }
            if (current != null) {
                lore.add("<aqua>已設定: <white>" + current + " 級（再選會覆蓋）");
            }
            lore.add("<dark_gray>id: " + key);
            inventory.setItem(
                    contentSlot(index - start),
                    button(
                            Material.ENCHANTED_BOOK,
                            "<aqua>" + EnchantNames.zh(key.toString()),
                            lore
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
        int content = contentIndex(slot);
        if (content < 0) {
            return;
        }
        WeaponDef weapon = repo.get(holder.weaponId());
        if (weapon == null) {
            openHome(player);
            return;
        }
        List<Enchantment> enchantments = availableEnchantments();
        int index = holder.page() * CONTENT_PAGE_SIZE + content;
        if (index >= enchantments.size()) {
            return;
        }
        String key = enchantmentStorageKey(enchantments.get(index).getKey());
        prompt(
                player,
                holder.weaponId(),
                PromptType.ENCHANT_LEVEL,
                key,
                "<yellow>請輸入 " + EnchantNames.zh(key) + " <dark_gray>(" + key + ") <yellow>的附魔等級"
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
        int page = clampPage(requestedPage, enchantments.size(), CONTENT_PAGE_SIZE);
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_ENCHANT_REMOVE, weaponId, page);
        Inventory inventory = createInventory(holder, 54, "<gold>移除附魔");
        fillFrame(inventory);
        int start = page * CONTENT_PAGE_SIZE;
        int end = Math.min(start + CONTENT_PAGE_SIZE, enchantments.size());
        for (int index = start; index < end; index++) {
            Map.Entry<String, Integer> entry = enchantments.get(index);
            inventory.setItem(
                    contentSlot(index - start),
                    button(
                            Material.ENCHANTED_BOOK,
                            "<red>移除 " + EnchantNames.zh(entry.getKey()),
                            List.of(
                                    "<gray>目前等級: <white>" + entry.getValue(),
                                    "<dark_gray>id: " + entry.getKey()
                            )
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
        int content = contentIndex(slot);
        if (content < 0) {
            return;
        }
        WeaponDef weapon = repo.get(holder.weaponId());
        if (weapon == null) {
            openHome(player);
            return;
        }
        List<String> keys = new ArrayList<>(weapon.enchantments().keySet());
        int index = holder.page() * CONTENT_PAGE_SIZE + content;
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
        fillFrame(inventory);
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

    private void openPreview(Player player, UUID weaponId) {
        if (!checkPermission(player)) {
            return;
        }
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_PREVIEW, weaponId, 0);
        Inventory inventory = createInventory(holder, 27, "<gold>預覽成品");
        fillFrame(inventory);
        // 中央放「購買後實際成品」（含時限 Lore、glint、PDC）
        inventory.setItem(13, ItemService.create(weapon, player.getUniqueId()));
        inventory.setItem(11, button(Material.ARROW, "<yellow>返回管理"));
        player.openInventory(inventory);
    }

    private void clickPreview(Player player, UUID weaponId, int slot) {
        if (slot == 11) {
            openManage(player, weaponId);
        }
    }

    // ==================== 匯入背包物品 ====================

    /**
     * 列出玩家背包（0-35 主背包 + 40 副手）中非空的物品，讓 admin 點選匯入成新特武模板。
     * GUI 開著時 GameListener 會擋掉整個 view 的點擊，所以背包內容不會被中途改動。
     */
    public void openImport(Player player) {
        openImport(player, 0);
    }

    public void openImport(Player player, int requestedPage) {
        if (!checkPermission(player)) {
            return;
        }

        List<Integer> sources = importSources(player);
        int page = clampPage(requestedPage, sources.size(), CONTENT_PAGE_SIZE);
        GuiHolder holder = new GuiHolder(GuiHolder.Type.ADMIN_IMPORT, null, page);
        Inventory inventory = createInventory(holder, 54, "<gold>匯入背包武器");
        fillFrame(inventory);

        int start = page * CONTENT_PAGE_SIZE;
        int end = Math.min(start + CONTENT_PAGE_SIZE, sources.size());
        for (int index = start; index < end; index++) {
            int inventorySlot = sources.get(index);
            ItemStack source = player.getInventory().getItem(inventorySlot);
            if (source == null || source.getType().isAir()) {
                continue;
            }
            ItemStack icon = source.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(meta.lore());
                lore.add(TextUtil.component(
                        "<dark_gray>來源: " + importSlotName(inventorySlot)
                ));
                lore.add(TextUtil.component("<yellow>▶ 點擊匯入為新特武"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(contentSlot(index - start), icon);
        }

        if (sources.isEmpty()) {
            inventory.setItem(
                    22,
                    button(Material.BARRIER, "<red>背包沒有可匯入的物品")
            );
        }

        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, "<yellow>上一頁"));
        }
        inventory.setItem(49, button(Material.ARROW, "<yellow>返回列表"));
        if (end < sources.size()) {
            inventory.setItem(53, button(Material.ARROW, "<yellow>下一頁"));
        }
        player.openInventory(inventory);
    }

    private void clickImport(Player player, GuiHolder holder, int slot) {
        if (slot == 45 && holder.page() > 0) {
            openImport(player, holder.page() - 1);
            return;
        }
        if (slot == 49) {
            openHome(player);
            return;
        }
        if (slot == 53) {
            openImport(player, holder.page() + 1);
            return;
        }
        int content = contentIndex(slot);
        if (content < 0) {
            return;
        }

        List<Integer> sources = importSources(player);
        int index = holder.page() * CONTENT_PAGE_SIZE + content;
        if (index >= sources.size()) {
            return;
        }
        importAsNewWeapon(player, player.getInventory().getItem(sources.get(index)), true);
    }

    /**
     * 指令版（`/superarms import`）：把主手物品匯入成新模板，不開 GUI。
     * 給 console / 基岩版 / 腳本化流程用；GUI 版走 openImport。
     */
    public void importMainHandAsNew(Player player) {
        if (!checkPermission(player)) {
            return;
        }
        ItemStack source = player.getInventory().getItemInMainHand();
        if (source == null || source.getType().isAir()) {
            player.sendMessage(TextUtil.component("<red>主手沒有物品，請把要匯入的物品拿在手上"));
            return;
        }
        importAsNewWeapon(player, source, false);
    }

    /** 背包中非空的來源格（依序）。 */
    private List<Integer> importSources(Player player) {
        List<Integer> slots = new ArrayList<>();
        for (int slot : IMPORT_SLOTS) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private String importSlotName(int slot) {
        return slot == 40 ? "副手" : "第 " + (slot + 1) + " 格";
    }

    private void importAsNewWeapon(Player player, ItemStack source, boolean openManageAfter) {
        if (source == null || source.getType().isAir()) {
            player.sendMessage(TextUtil.component("<red>該格已經沒有物品了"));
            openImport(player);
            return;
        }

        boolean wasSuperArmsItem = ItemService.def(source) != null;
        WeaponDef weapon = repo.create("<gold>新特武");
        String summary = applyImportedAppearance(weapon, source, false);
        repo.save();

        player.sendMessage(
                TextUtil.component("<green>已匯入新特武：<white>" + summary)
        );
        player.sendMessage(
                TextUtil.component("<gray>UUID: <white>" + weapon.id()
                        + " <gray>｜價格/時限預設 0（免費、永久）")
        );
        if (wasSuperArmsItem) {
            player.sendMessage(
                    TextUtil.component("<yellow>注意：該物品本身已是特武成品，已忽略它的時限標記行。")
            );
        }
        if (openManageAfter) {
            openManage(player, weapon.id());
        }
    }

    /** 「從手上匯入覆蓋」：把主手物品的外觀套到既有模板（價格/時限/販售截止不變）。 */
    private void importFromMainHand(Player player, UUID weaponId) {
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            openHome(player);
            return;
        }
        ItemStack source = player.getInventory().getItemInMainHand();
        if (source == null || source.getType().isAir()) {
            player.sendMessage(TextUtil.component("<red>主手沒有物品，請把要匯入的物品拿在手上再試"));
            openManage(player, weaponId);
            return;
        }

        String summary = applyImportedAppearance(weapon, source, true);
        repo.save();
        player.sendMessage(TextUtil.component("<green>已用主手物品覆蓋此特武：<white>" + summary));
        openManage(player, weaponId);
    }

    /**
     * 把物品的外觀欄位覆蓋到模板：材質 / 名稱 / Lore / 附魔 / 自訂模型資料 / 不可破壞 / 發光。
     * 商業欄位（價格、幣種、販售截止、時限、開關）不動。
     *
     * @param keepNameIfAbsent true 時物品沒有自訂名稱就保留模板原本的名稱
     * @return 一行摘要，例如「材質 DIAMOND_SWORD、2 行 Lore、4 個附魔」
     */
    private String applyImportedAppearance(
            WeaponDef weapon,
            ItemStack source,
            boolean keepNameIfAbsent
    ) {
        weapon.material(source.getType());
        weapon.lore().clear();
        weapon.enchantments().clear();

        ItemMeta meta = source.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                weapon.name(TextUtil.serialize(meta.displayName()));
            } else if (!keepNameIfAbsent) {
                weapon.name("<white>" + prettyMaterial(source.getType()));
            }
            weapon.unbreakable(meta.isUnbreakable());
            weapon.customModelData(
                    meta.hasCustomModelData() ? meta.getCustomModelData() : null
            );

            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component line : lore) {
                    if (isSystemLore(line)) {
                        continue;
                    }
                    weapon.lore().add(TextUtil.serialize(line));
                }
            }

            // 本系統的假光澤 = LURE 1 + HIDE_ENCHANTS，匯入時視為「發光」而非一個附魔。
            boolean fakeGlow = meta.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS);
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                String key = enchantmentStorageKey(entry.getKey().getKey());
                if (fakeGlow && key.equals("LURE") && entry.getValue() <= 1) {
                    continue;
                }
                weapon.enchantments().put(key, entry.getValue());
            }
            weapon.glow(!weapon.enchantments().isEmpty() || fakeGlow);
        }

        return "材質 " + source.getType().name()
                + "、" + weapon.lore().size() + " 行 Lore"
                + "、" + weapon.enchantments().size() + " 個附魔";
    }

    private boolean isSystemLore(Component line) {
        String plain = TextUtil.plain(line);
        for (String marker : SYSTEM_LORE_MARKERS) {
            if (plain.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** NETHERITE_SWORD → Netherite Sword（物品沒有自訂名稱時的預設顯示名）。 */
    private String prettyMaterial(Material material) {
        StringBuilder builder = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private void giveItem(Player player, UUID weaponId) {
        if (!checkPermission(player)) {
            return;
        }
        WeaponDef weapon = repo.get(weaponId);
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            openHome(player);
            return;
        }
        if (!hasFreeSlot(player)) {
            player.sendMessage(message("buy-full-inventory", "<red>背包已滿"));
            return;
        }
        player.getInventory().addItem(ItemService.create(weapon, player.getUniqueId()));
        player.sendMessage(
                TextUtil.component("<green>已取得 <white>" + weapon.name() + " <green>成品")
        );
        openManage(player, weaponId);
    }

    private boolean hasFreeSlot(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                return true;
            }
        }
        return false;
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

    /** 邊框用灰色玻璃片（空白名）。 */
    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.component(" "));
        item.setItemMeta(meta);
        return item;
    }

    /** 在 GUI 外圈填上玻璃邊框（內容後放會覆蓋）。支援 54 / 27 格。 */
    private void fillFrame(Inventory inventory) {
        int size = inventory.getSize();
        int rows = size / 9;
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, pane());
            inventory.setItem((rows - 1) * 9 + col, pane());
        }
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, pane());
            inventory.setItem(row * 9 + 8, pane());
        }
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
        return clampPage(requestedPage, itemCount, PAGE_SIZE);
    }

    private int clampPage(int requestedPage, int itemCount, int pageSize) {
        int lastPage = itemCount == 0 ? 0 : (itemCount - 1) / pageSize;
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
