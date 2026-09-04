package tw.superarms.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import tw.superarms.SuperArmsPlugin;
import tw.superarms.data.WeaponDef;
import tw.superarms.data.WeaponRepository;
import tw.superarms.service.AdminService;
import tw.superarms.service.ItemService;
import tw.superarms.service.ShopService;
import tw.superarms.util.TextUtil;

public final class SuperArmsCommand implements CommandExecutor, TabCompleter {

    private final SuperArmsPlugin plugin;
    private final WeaponRepository repo;
    private final ShopService shop;
    private final AdminService admin;

    public SuperArmsCommand(
            SuperArmsPlugin plugin,
            WeaponRepository repo,
            ShopService shop,
            AdminService admin
    ) {
        this.plugin = plugin;
        this.repo = repo;
        this.shop = shop;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 0) {
            if (sender instanceof Player player && isAdmin(player)) {
                admin.openHome(player);
            } else {
                sendNoPermission(sender);
            }
            return true;
        }

        String subcommand = arguments[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("shop")) {
            if (sender instanceof Player player) {
                shop.open(player);
            }
            return true;
        }
        if (subcommand.equals("buy")) {
            handleBuy(sender, arguments);
            return true;
        }
        if (subcommand.equals("list")) {
            listWeapons(sender);
            return true;
        }
        if (subcommand.equals("reload")) {
            handleReload(sender);
            return true;
        }
        if (subcommand.equals("arms")) {
            handleArms(sender, arguments);
            return true;
        }

        openWeapon(sender, arguments[0]);
        return true;
    }

    private void handleBuy(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player) || arguments.length < 2) {
            return;
        }
        if (!shop.canBuy(player)) {
            return;
        }
        WeaponDef weapon = findWeapon(arguments[1]);
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }
        shop.buyDirect(player, weapon);
    }

    private void listWeapons(CommandSender sender) {
        for (WeaponDef weapon : repo.all()) {
            String line = plugin.messages().getString(
                    "list-detail",
                    "<yellow>%uuid% | %name% | %currency% %price% | %sell%"
            );
            sender.sendMessage(
                    TextUtil.component(
                            line.replace("%uuid%", weapon.id().toString())
                                    .replace("%name%", weapon.name())
                                    .replace("%currency%", weapon.currency())
                                    .replace("%price%", String.valueOf(weapon.price()))
                                    .replace(
                                            "%sell%",
                                            weapon.sellUntil() == 0
                                                    ? "unlimited"
                                                    : TextUtil.date(weapon.sellUntil())
                                    )
                    )
            );
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("superarms.admin")) {
            sendNoPermission(sender);
            return;
        }
        plugin.reloadConfig();
        plugin.reloadMessages();
        repo.load();
        shop.clearConfirms();
        admin.clearInputs();
        sender.sendMessage(message("reload", "<green>已重新載入"));
    }

    private void handleArms(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player) || !isAdmin(player)) {
            sendNoPermission(sender);
            return;
        }
        if (arguments.length < 2) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }
        WeaponDef weapon = findWeapon(arguments[1]);
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }
        player.getInventory().addItem(ItemService.preview(weapon));
    }

    private void openWeapon(CommandSender sender, String idValue) {
        if (!(sender instanceof Player player) || !isAdmin(player)) {
            sendNoPermission(sender);
            return;
        }
        WeaponDef weapon = findWeapon(idValue);
        if (weapon == null) {
            player.sendMessage(message("not-found", "<red>找不到該武器"));
            return;
        }
        admin.openManage(player, weapon.id());
    }

    private WeaponDef findWeapon(String idValue) {
        try {
            return repo.get(UUID.fromString(idValue));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("superarms.admin");
    }

    private void sendNoPermission(CommandSender sender) {
        sender.sendMessage(message("no-permission", "<red>你沒有權限"));
    }

    private net.kyori.adventure.text.Component message(String key, String fallback) {
        return TextUtil.component(plugin.messages().getString(key, fallback));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length == 1) {
            return Arrays.asList("shop", "buy", "list", "reload", "arms");
        }
        if (arguments.length == 2
                && (arguments[0].equalsIgnoreCase("buy")
                || arguments[0].equalsIgnoreCase("arms"))) {
            List<String> ids = new ArrayList<>();
            for (WeaponDef weapon : repo.all()) {
                ids.add(weapon.id().toString());
            }
            return ids;
        }
        return List.of();
    }
}
