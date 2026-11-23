package com.rbfernan.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Constructor de items para los menus (solo API comun a 1.20 y 1.21). */
public final class Items {
    private Items() {}

    public static ItemStack make(Material m, String name, String... lore) {
        return make(m, name, Arrays.asList(lore));
    }

    public static ItemStack make(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt == null) return i;
        mt.setDisplayName(Utils.color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String l : lore) out.add(Utils.color(l));
            mt.setLore(out);
        }
        try { mt.addItemFlags(ItemFlag.values()); } catch (Throwable ignored) { }
        i.setItemMeta(mt);
        return i;
    }

    public static ItemStack glow(ItemStack i) {
        ItemMeta mt = i.getItemMeta();
        if (mt == null) return i;
        try {
            Enchantment e = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
            if (e != null) mt.addEnchant(e, 1, true);
            mt.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } catch (Throwable ignored) { }
        i.setItemMeta(mt);
        return i;
    }

    /** Cabeza con la skin de un jugador (por UUID: no hace consultas a internet). */
    public static ItemStack head(UUID owner, String name, List<String> lore) {
        ItemStack i = make(Material.PLAYER_HEAD, name, lore);
        if (owner == null) return i;
        try {
            SkullMeta sm = (SkullMeta) i.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            i.setItemMeta(sm);
        } catch (Throwable ignored) { }
        return i;
    }

    public static ItemStack filler(Material m) { return make(m, " "); }

    /** Nombre bonito de un item: su nombre personalizado o el material. */
    public static String label(ItemStack it) {
        if (it == null) return "?";
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) return Utils.strip(m.getDisplayName());
        return Utils.pretty(it.getType().name());
    }

    /** Texto para mostrar al pasar el cursor: nombre, cantidad, encantamientos y lore (con colores, lineas con \n). */
    public static String hoverText(ItemStack it) {
        if (it == null) return "";
        StringBuilder b = new StringBuilder();
        ItemMeta m = it.getItemMeta();
        b.append(m != null && m.hasDisplayName() ? m.getDisplayName() : "\u00a7f" + Utils.pretty(it.getType().name()));
        b.append(" \u00a77x").append(it.getAmount());
        b.append("\n\u00a78").append(it.getType().name());
        if (m != null) {
            for (Map.Entry<Enchantment, Integer> e : m.getEnchants().entrySet()) {
                b.append("\n\u00a77").append(Utils.pretty(e.getKey().getKey().getKey())).append(" ").append(e.getValue());
            }
            if (m.hasLore() && m.getLore() != null) {
                b.append("\n");
                for (String l : m.getLore()) b.append("\n").append(l);
            }
        }
        return b.toString();
    }

    /** Huella de un item (sin cantidad) para reconocerlo despues aunque cambie de dueno. */
    public static String signature(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return "";
        StringBuilder b = new StringBuilder(it.getType().name());
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            b.append('|').append(m.hasDisplayName() ? Utils.strip(m.getDisplayName()) : "");
            try { if (m.hasCustomModelData()) b.append("|cmd").append(m.getCustomModelData()); } catch (Throwable ignored) { }
            Map<String, Integer> ench = new TreeMap<>();
            for (Map.Entry<Enchantment, Integer> e : m.getEnchants().entrySet()) {
                ench.put(e.getKey().getKey().getKey(), e.getValue());
            }
            if (!ench.isEmpty()) b.append('|').append(ench);
        }
        return b.toString();
    }
}
