package com.rbfernan.listeners;

import com.rbfernan.Main;
import com.rbfernan.scanner.Scanner;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Eventos del scanner. Todos salen al instante si no hay nadie vigilado
 * (solo se vigila a quien mato a otro jugador hace poco), asi que casi no consume.
 */
public class ScanListener implements Listener {
    private final Main plugin;

    public ScanListener(Main plugin) { this.plugin = plugin; }

    private Scanner sc() { return plugin.scanner(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        sc().onItemSpawn(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) sc().onPickup(p, e.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (sc().watching(e.getPlayer())) sc().onDrop(e.getPlayer(), e.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!sc().anyWatch() || !(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getInventory();
        InventoryType t = top.getType();
        if (t != InventoryType.CHEST && t != InventoryType.BARREL && t != InventoryType.SHULKER_BOX && t != InventoryType.ENDER_CHEST
                && t != InventoryType.HOPPER && t != InventoryType.DISPENSER && t != InventoryType.DROPPER) return;
        if (top.getHolder() instanceof com.rbfernan.menu.MenuHolder) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        InventoryAction a = e.getAction();
        Location loc = t == InventoryType.ENDER_CHEST ? null : top.getLocation();
        String typeName = t == InventoryType.ENDER_CHEST ? "enderchest" : t.name().toLowerCase().replace('_', ' ');
        boolean intoTop = (clicked == top && (a == InventoryAction.PLACE_ALL || a == InventoryAction.PLACE_ONE
                || a == InventoryAction.PLACE_SOME || a == InventoryAction.SWAP_WITH_CURSOR))
                || (clicked != top && a == InventoryAction.MOVE_TO_OTHER_INVENTORY);
        boolean outOfTop = clicked == top && (a == InventoryAction.PICKUP_ALL || a == InventoryAction.PICKUP_HALF
                || a == InventoryAction.PICKUP_ONE || a == InventoryAction.PICKUP_SOME || a == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || a == InventoryAction.HOTBAR_SWAP || a == InventoryAction.COLLECT_TO_CURSOR);
        if (intoTop && sc().watching(p)) {
            ItemStack it = clicked == top ? e.getCursor() : e.getCurrentItem();
            sc().onDeposit(p, it == null ? null : it.clone(), loc, typeName);
        } else if (outOfTop) {
            ItemStack it = e.getCurrentItem();
            sc().onTake(p, it == null ? null : it.clone(), loc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (sc().watching(p)) sc().onCommand(p, e.getMessage());
        // solo cuenta si quien banea es staff (asi un usuario normal no puede "provocar" un escaneo)
        if (p.isOp() || p.hasPermission("rbfernan.admin") || p.hasPermission("rbfernan.staff")) {
            sc().checkBanCommand(p.getName(), e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent e) {
        sc().checkBanCommand("Consola", e.getCommand());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sc().onQuit(e.getPlayer());
    }
}
