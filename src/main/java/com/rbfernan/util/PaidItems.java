package com.rbfernan.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reconoce los "items de paga" segun el config (material, nombre, lore o modelo personalizado). */
public final class PaidItems {
    private final Set<String> materials = new HashSet<>();
    private final List<String> names = new ArrayList<>();
    private final List<String> lores = new ArrayList<>();
    private boolean customModel;

    public void load(ConfigurationSection c) {
        materials.clear(); names.clear(); lores.clear();
        if (c == null) return;
        for (String m : c.getStringList("materiales")) materials.add(m.toUpperCase(Locale.ROOT));
        for (String n : c.getStringList("nombre-contiene")) names.add(n.toLowerCase(Locale.ROOT));
        for (String l : c.getStringList("lore-contiene")) lores.add(l.toLowerCase(Locale.ROOT));
        customModel = c.getBoolean("modelo-personalizado", false);
    }

    public boolean isPaid(ItemStack it) {
        if (it == null) return false;
        if (materials.contains(it.getType().name())) return true;
        ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        if (m.hasDisplayName() && !names.isEmpty()) {
            String n = Utils.strip(m.getDisplayName()).toLowerCase(Locale.ROOT);
            for (String s : names) if (n.contains(s)) return true;
        }
        if (m.hasLore() && !lores.isEmpty() && m.getLore() != null) {
            for (String line : m.getLore()) {
                String l = Utils.strip(line).toLowerCase(Locale.ROOT);
                for (String s : lores) if (l.contains(s)) return true;
            }
        }
        if (customModel) {
            try { if (m.hasCustomModelData()) return true; } catch (Throwable ignored) { }
        }
        return false;
    }
}
