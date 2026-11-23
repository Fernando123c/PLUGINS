package com.rbfernan.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifica los menus del plugin (no depende del titulo: funciona igual en 1.20 y 1.21). */
public class MenuHolder implements InventoryHolder {
    public enum Type { PROFILE, KILLERS, HISTORY, PREVIEW, VAULTS, SCANS, REPORT }

    public final Type type;
    public String target = "";
    public String invType = "INV";
    public String killer = "";
    public String file = "";
    public int page = 1;
    public final java.util.Map<Integer, String> slotData = new java.util.HashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type) { this.type = type; }

    public void setInventory(Inventory inv) { this.inventory = inv; }

    @Override
    public Inventory getInventory() { return inventory; }
}
