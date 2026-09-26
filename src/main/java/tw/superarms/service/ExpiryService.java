package tw.superarms.service;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.TagVerdict;
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
            Result result = rewriteIfNeeded(inventory.getItem(slot));
            if (result != null) {
                inventory.setItem(slot, result.item());
                if (result.expired()) {
                    notifyExpired(player);
                }
            }
        }
    }

    private void rewriteMainHandNow(Player player) {
        PlayerInventory inventory = player.getInventory();
        Result result = rewriteIfNeeded(inventory.getItemInMainHand());
        if (result != null) {
            inventory.setItemInMainHand(result.item());
            if (result.expired()) {
                notifyExpired(player);
            }
        }
    }

    /**
     * 依標籤狀態決定是否改寫物品。
     *
     * <ul>
     *   <li>無標籤 → 不動作（不是特武）。</li>
     *   <li>標籤遭竄改 → fail-closed，直接拔除全部附魔。</li>
     *   <li>舊版標籤 → 先遷移補簽章（即使尚未到期也要寫回，讓簽章落地）。</li>
     *   <li>已標記失效 → 不動作。</li>
     *   <li>已到期 → 拔除全部附魔。</li>
     * </ul>
     *
     * @return 需要寫回物品時回傳結果，否則 null。
     */
    private Result rewriteIfNeeded(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        String defValue = ItemService.defString(item);
        if (defValue == null || defValue.isBlank()) {
            return null;
        }

        UUID definitionId = ItemService.def(item);
        TagVerdict verdict = ItemService.verdict(item);
        boolean alreadyExpired = ItemService.expired(item) != 0;
        long expiresAt = ItemService.expires(item);
        long now = System.currentTimeMillis();
        // 模板存在檢查：def 解析不出 UUID 或找不到模板 → 視為不存在。
        boolean templateExists = definitionId != null && repo.get(definitionId) != null;

        ExpiryPolicy.Action action = ExpiryPolicy.decide(
                verdict,
                templateExists,
                alreadyExpired,
                expiresAt,
                now
        );
        switch (action) {
            case STRIP -> {
                return new Result(ItemService.expireAll(item), true);
            }
            case EXPIRE -> {
                return new Result(ItemService.expireAll(item), true);
            }
            case SKIP -> {
                // 舊版但有效：把補上的簽章寫回，之後才會被當成 VALID。
                if (verdict == TagVerdict.LEGACY && templateExists) {
                    return new Result(ItemService.migrate(item), false);
                }
                return null;
            }
            default -> {
                return null;
            }
        }
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

    private record Result(ItemStack item, boolean expired) {
    }
}
