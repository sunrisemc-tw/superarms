package tw.superarms.data;

import java.util.*;
import org.bukkit.Material;

public final class WeaponDef {
  private UUID id;
  private String name;
  private Material material = Material.DIAMOND_SWORD;
  private Integer customModelData;
  private boolean unbreakable;
  private List<String> lore = new ArrayList<>();
  private Map<String, Integer> enchantments = new LinkedHashMap<>();
  private long timeoutMillis;
  private long sellUntil;
  private String currency = "VAULT";
  private double price;
  private boolean glow = true, announce = true, protection = true;

  public WeaponDef(UUID id) {
    this.id = id;
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void name(String v) {
    name = v;
  }

  public Material material() {
    return material;
  }

  public void material(Material v) {
    material = v;
  }

  public Integer customModelData() {
    return customModelData;
  }

  public void customModelData(Integer v) {
    customModelData = v;
  }

  public boolean unbreakable() {
    return unbreakable;
  }

  public void unbreakable(boolean v) {
    unbreakable = v;
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

  public void timeoutMillis(long v) {
    timeoutMillis = v;
  }

  public long sellUntil() {
    return sellUntil;
  }

  public void sellUntil(long v) {
    sellUntil = v;
  }

  public String currency() {
    return currency;
  }

  public void currency(String v) {
    currency = v;
  }

  public double price() {
    return price;
  }

  public void price(double v) {
    price = v;
  }

  public boolean glow() {
    return glow;
  }

  public void glow(boolean v) {
    glow = v;
  }

  public boolean announce() {
    return announce;
  }

  public void announce(boolean v) {
    announce = v;
  }

  public boolean protection() {
    return protection;
  }

  public void protection(boolean v) {
    protection = v;
  }
}
