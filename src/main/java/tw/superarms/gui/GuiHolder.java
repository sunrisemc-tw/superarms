package tw.superarms.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {

    public enum Type {
        SHOP,
        CONFIRM,
        ADMIN_HOME,
        ADMIN_MANAGE,
        ADMIN_LORE_REMOVE,
        ADMIN_ENCHANT_ADD,
        ADMIN_ENCHANT_REMOVE,
        ADMIN_DELETE_CONFIRM
    }

    private final Type type;
    private final UUID weaponId;
    private final int page;
    private final String value;
    private Inventory inventory;

    public GuiHolder(Type type, UUID weaponId, int page) {
        this(type, weaponId, page, null);
    }

    public GuiHolder(Type type, UUID weaponId, int page, String value) {
        this.type = type;
        this.weaponId = weaponId;
        this.page = page;
        this.value = value;
    }

    public Type type() {
        return type;
    }

    public UUID weaponId() {
        return weaponId;
    }

    public int page() {
        return page;
    }

    public String value() {
        return value;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
