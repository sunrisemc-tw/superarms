package tw.superarms.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;

public final class WeaponDef {
    private final UUID id;
    private String name;
    private Material material = Material.DIAMOND_SWORD;
    private Integer customModelData;
    private boolean unbreakable = true;
    private final List<String> lore = new ArrayList<>();
    private final Map<String, Integer> enchantments = new LinkedHashMap<>();
    private long timeoutMillis;
    private long sellUntil;
    private String currency = "VAULT";
    private double price;
    private boolean glow = true;
    private boolean announce = true;
    private boolean protection = true;

    public WeaponDef(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String value) {
        name = value;
    }

    public Material material() {
        return material;
    }

    public void material(Material value) {
        material = value;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public void customModelData(Integer value) {
        customModelData = value;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public void unbreakable(boolean value) {
        unbreakable = value;
    }

    public List<String> lore() {
        return lore;
    }

    public Map<String, Integer> enchantments() {
        return enchantments;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public void timeoutMillis(long value) {
        timeoutMillis = value;
    }

    public long sellUntil() {
        return sellUntil;
    }

    public void sellUntil(long value) {
        sellUntil = value;
    }

    public boolean isForSale(long now) {
        return sellUntil == 0 || sellUntil > now;
    }

    public String currency() {
        return currency;
    }

    public void currency(String value) {
        currency = value;
    }

    public double price() {
        return price;
    }

    public void price(double value) {
        price = value;
    }

    public boolean glow() {
        return glow;
    }

    public void glow(boolean value) {
        glow = value;
    }

    public boolean announce() {
        return announce;
    }

    public void announce(boolean value) {
        announce = value;
    }

    public boolean protection() {
        return protection;
    }

    public void protection(boolean value) {
        protection = value;
    }
}
