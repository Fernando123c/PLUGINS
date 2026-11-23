package com.rbfernan.listeners;

import com.rbfernan.Main;
import com.rbfernan.data.DeathRecord;
import com.rbfernan.menu.MenuHolder;
import com.rbfernan.util.Items;
import com.rbfernan.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MenuListener implements Listener {
    private final Main plugin;

    public MenuListener(Main plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        if (h.type != MenuHolder.Type.PREVIEW) { e.setCancelled(true); return; }
        for (int s : e.getRawSlots()) if (s >= 41 && s < 54) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        if (!(e.getWhoClicked() instanceof Player staff)) return;
        int slot = e.getRawSlot();
        if (h.type == MenuHolder.Type.PREVIEW) {
            // arriba (0-40) se pueden mover items; abajo es el inventario del staff
            if (slot < 41 || slot >= 54) return;
            e.setCancelled(true);
            preview(staff, h, slot, e.getInventory());
            return;
        }
        e.setCancelled(true);
        if (slot < 0 || slot >= e.getInventory().getSize()) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR || cur.getType().name().endsWith("STAINED_GLASS_PANE")) return;
        if (!staff.hasPermission("rbfernan.admin")) { staff.closeInventory(); return; }
        switch (h.type) {
            case PROFILE -> {
                if (slot == 20) plugin.menus().history(staff, h.target, 1, "INV", "");
                else if (slot == 22) plugin.menus().history(staff, h.target, 1, "EC", "");
                else if (slot == 24) plugin.menus().vaults(staff, h.target);
                else if (slot == 30) plugin.menus().killers(staff, h.target);
                else if (slot == 32) plugin.menus().scans(staff, h.target, 1);
                else if (slot == 40) staff.closeInventory();
            }
            case KILLERS -> {
                if (slot == 49) plugin.menus().profile(staff, h.target);
                else if (slot == 50) plugin.menus().history(staff, h.target, 1, "INV", "");
                else if (h.slotData.containsKey(slot)) plugin.menus().history(staff, h.target, 1, "INV", h.slotData.get(slot));
            }
            case HISTORY -> {
                if (slot == 45) plugin.menus().history(staff, h.target, h.page - 1, h.invType, h.killer);
                else if (slot == 53) plugin.menus().history(staff, h.target, h.page + 1, h.invType, h.killer);
                else if (slot == 48) plugin.menus().profile(staff, h.target);
                else if (slot == 50) {
                    if (h.killer.isEmpty()) plugin.menus().killers(staff, h.target);
                    else plugin.menus().history(staff, h.target, 1, h.invType, "");
                } else if (h.slotData.containsKey(slot)) {
                    DeathRecord r = plugin.deaths().find(h.target, h.slotData.get(slot));
                    if (r != null) plugin.menus().preview(staff, r, h.invType);
                }
            }
            case VAULTS -> {
                if (slot == 49) { plugin.menus().profile(staff, h.target); return; }
                String num = h.slotData.get(slot);
                if (num == null) return;
                String cmd = plugin.getConfig().getString("comando-abrir-pv", "axvaults admin %player% %vault%")
                        .replace("%player%", h.target).replace("%vault%", num);
                staff.closeInventory();
                Bukkit.dispatchCommand(staff, cmd);
            }
            case SCANS -> {
                if (slot == 45) plugin.menus().scans(staff, h.target, h.page - 1);
                else if (slot == 53) plugin.menus().scans(staff, h.target, h.page + 1);
                else if (slot == 48 && !h.target.isEmpty()) plugin.menus().profile(staff, h.target);
                else if (h.slotData.containsKey(slot)) plugin.menus().report(staff, h.slotData.get(slot));
            }
            case REPORT -> {
                if (slot == 49) plugin.menus().scans(staff, "", 1);
                else if (slot == 40) { staff.closeInventory(); plugin.menus().printLog(staff, h.file); }
                else if (h.slotData.containsKey(slot)) {
                    String d = h.slotData.get(slot);
                    if (d.startsWith("P|")) plugin.menus().profile(staff, d.substring(2));
                    else {
                        String[] p = d.split("\\|", 2);
                        DeathRecord r = p.length == 2 ? plugin.deaths().find(p[0], p[1]) : null;
                        if (r != null) plugin.menus().preview(staff, r, "INV");
                        else plugin.menus().profile(staff, p[0]);
                    }
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ vista previa
    private void preview(Player staff, MenuHolder h, int slot, Inventory inv) {
        DeathRecord r = plugin.deaths().find(h.target, h.file);
        if (r == null) { staff.closeInventory(); staff.sendMessage(Utils.color("&cEse registro ya no existe.")); return; }
        switch (slot) {
            case 45 -> { saveChanges(inv, r, h.invType); plugin.menus().history(staff, r.player, 1, h.invType, ""); }
            case 46 -> {
                World w = Bukkit.getWorld(r.world);
                if (w != null) {
                    YamlConfiguration c = plugin.deaths().loadData(r);
                    staff.teleport(new Location(w, r.x, r.y, r.z, (float) c.getDouble("info.location.yaw"), (float) c.getDouble("info.location.pitch")));
                    staff.sendMessage(plugin.prefix() + Utils.color("&a¡Te teletransportaste al lugar de la muerte!"));
                }
                staff.closeInventory();
            }
            case 47, 48, 49, 50, 51 -> {
                saveChanges(inv, r, h.invType);
                if (slot == 47) giveDirect(staff, r, "INV", false);
                else if (slot == 48) giveDirect(staff, r, "EC", false);
                else if (slot == 49) giveShulker(staff, r, h.invType);
                else if (slot == 50) giveDirect(staff, r, "INV", true);
                else approve(staff, r);
                staff.closeInventory();
            }
            case 52 -> {
                if (r.status.equals("CLAIMED")) staff.sendMessage(plugin.msg("registro-ya-reclamado"));
                else if (r.status.equals("APPROVED")) {
                    plugin.deaths().setStatus(r, "PENDING");
                    staff.sendMessage(plugin.msg("registro-revocado"));
                    plugin.logAction(staff.getName(), r.player, "REVOCÓ APROBACIÓN", r.cause);
                    plugin.menus().preview(staff, r, h.invType);
                }
            }
            case 53 -> plugin.menus().scans(staff, r.player, 1);
            default -> { }
        }
    }

    private void saveChanges(Inventory inv, DeathRecord r, String type) {
        YamlConfiguration c = plugin.deaths().loadData(r);
        ItemStack[] items = new ItemStack[41];
        for (int i = 0; i < 41; i++) items[i] = inv.getItem(i);
        c.set(type.equals("EC") ? "enderchest" : "inventory", items);
        plugin.deaths().saveData(r, c);
    }

    private List<ItemStack> valid(DeathRecord r, String type, boolean skipPaid) {
        YamlConfiguration c = plugin.deaths().loadData(r);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack i : plugin.deaths().items(c, type)) {
            if (i == null || i.getType() == Material.AIR) continue;
            if (skipPaid && plugin.paid().isPaid(i)) continue;
            out.add(i);
        }
        return out;
    }

    /** Entrega directa. staff == null = lo reclamo el jugador con /rbfernan pendiente. */
    public void giveDirect(Player staff, DeathRecord r, String where, boolean skipPaid) {
        Player target = Bukkit.getPlayerExact(r.player);
        if (target == null) { if (staff != null) staff.sendMessage(plugin.msg("jugador-offline")); return; }
        List<ItemStack> items = valid(r, "INV", skipPaid);
        if (where.equals("EC")) {
            Inventory ec = target.getEnderChest();
            for (ItemStack it : items) drop(target, ec.addItem(it));
            if (staff != null) staff.sendMessage(plugin.msg("rollback-enderchest").replace("%player%", target.getName()));
            plugin.logAction(staff != null ? staff.getName() : "AUTOMÁTICO", r.player, "ENTREGA A ENDERCHEST", r.cause);
            return;
        }
        for (ItemStack it : items) {
            String n = it.getType().name();
            if (n.endsWith("_HELMET") || n.contains("_HEAD") || n.contains("_SKULL")) equip(target, it, 3);
            else if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) equip(target, it, 2);
            else if (n.endsWith("_LEGGINGS")) equip(target, it, 1);
            else if (n.endsWith("_BOOTS")) equip(target, it, 0);
            else drop(target, target.getInventory().addItem(it));
        }
        if (staff != null) staff.sendMessage(plugin.msg("rollback-aplicado").replace("%player%", target.getName())
                + (skipPaid ? Utils.color(" &6(sin items de paga)") : ""));
        plugin.logAction(staff != null ? staff.getName() : "AUTOMÁTICO", r.player,
                skipPaid ? "ENTREGA SIN ITEMS DE PAGA" : "ENTREGA AL INVENTARIO", r.cause);
    }

    private void drop(Player p, Map<Integer, ItemStack> fail) {
        for (ItemStack i : fail.values()) p.getWorld().dropItemNaturally(p.getLocation(), i);
    }

    private void equip(Player p, ItemStack item, int slot) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        if (armor[slot] != null && armor[slot].getType() != Material.AIR) drop(p, p.getInventory().addItem(item));
        else { armor[slot] = item; p.getInventory().setArmorContents(armor); }
    }

    private void giveShulker(Player staff, DeathRecord r, String type) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("shulker-settings.material", "PURPLE_SHULKER_BOX"));
        if (mat == null || !mat.name().endsWith("SHULKER_BOX")) mat = Material.PURPLE_SHULKER_BOX;
        List<ItemStack> all = valid(r, type, false);
        List<ItemStack> shulkers = new ArrayList<>(), others = new ArrayList<>();
        for (ItemStack i : all) (i.getType().name().endsWith("SHULKER_BOX") ? shulkers : others).add(i);
        String time = Utils.now();
        int count = 1, idx = 0;
        while (idx < others.size()) {
            ItemStack box = new ItemStack(mat);
            ItemMeta meta = box.getItemMeta();
            meta.setDisplayName(Utils.color(plugin.getConfig().getString("shulker-settings.nombre", "&d&lSHULKER ROLLBACK #%count%")
                    .replace("%count%", String.valueOf(count)).replace("%player%", r.player)));
            List<String> lore = new ArrayList<>();
            for (String l : plugin.getConfig().getStringList("shulker-settings.lore"))
                lore.add(Utils.color(l.replace("%staff%", staff.getName()).replace("%date%", time).replace("%player%", r.player)));
            meta.setLore(lore);
            BlockStateMeta bsm = (BlockStateMeta) meta;
            BlockState st = bsm.getBlockState();
            if (st instanceof ShulkerBox sb) {
                for (int i = 0; i < 27 && idx < others.size(); i++) sb.getInventory().setItem(i, others.get(idx++));
                bsm.setBlockState(sb);
            } else idx = others.size();
            box.setItemMeta(bsm);
            drop(staff, staff.getInventory().addItem(box));
            count++;
        }
        for (ItemStack s : shulkers) drop(staff, staff.getInventory().addItem(s));
        staff.sendMessage(plugin.msg("shulker-entregada"));
        plugin.logAction(staff.getName(), r.player, "GENERÓ SHULKER(S) DE " + type, r.cause);
    }

    private void approve(Player staff, DeathRecord r) {
        plugin.deaths().setStatus(r, "APPROVED");
        staff.sendMessage(plugin.msg("admin-aprobado-aviso").replace("%player%", r.player));
        Player t = Bukkit.getPlayerExact(r.player);
        if (t != null) t.sendMessage(plugin.msg("aprobado"));
        plugin.logAction(staff.getName(), r.player, "APROBÓ PENDIENTE", r.cause);
    }

    /** /rbfernan pendiente */
    public boolean claim(Player p) {
        for (DeathRecord r : plugin.deaths().records(p.getName())) {
            if (!"APPROVED".equals(r.status)) continue;
            giveDirect(null, r, "INV", false);
            plugin.deaths().setStatus(r, "CLAIMED");
            return true;
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static String label(ItemStack i) { return Items.label(i); }
}
