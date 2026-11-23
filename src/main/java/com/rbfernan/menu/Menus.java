package com.rbfernan.menu;

import com.rbfernan.Main;
import com.rbfernan.data.DeathRecord;
import com.rbfernan.util.Items;
import com.rbfernan.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Menus {
    private final Main plugin;

    // 28 espacios centrales de un menu de 54 (dentro del borde)
    public static final int[] CENTER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public Menus(Main plugin) { this.plugin = plugin; }

    private Inventory create(MenuHolder h, int size, String title) {
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inv = Bukkit.createInventory(h, size, Utils.color(title));
        h.setInventory(inv);
        return inv;
    }

    /** Borde de colores: azul arriba/abajo, celeste a los lados. */
    private void border(Inventory inv) {
        ItemStack dark = Items.filler(Material.BLUE_STAINED_GLASS_PANE);
        ItemStack light = Items.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack fill = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == size / 9 - 1) inv.setItem(i, (col % 2 == 0) ? dark : light);
            else if (col == 0 || col == 8) inv.setItem(i, light);
            else inv.setItem(i, fill);
        }
    }

    private static String statusColor(String s) {
        return switch (s) { case "CLAIMED" -> "&a"; case "APPROVED" -> "&e"; default -> "&c"; };
    }

    private static String statusName(String s) {
        return switch (s) { case "CLAIMED" -> "✔ Reclamado"; case "APPROVED" -> "★ Aprobado"; default -> "● Pendiente"; };
    }

    private UUID uuidOf(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        for (DeathRecord r : plugin.deaths().records(name)) if (r.playerUuid != null) return r.playerUuid;
        return null;
    }

    private void click(Player p) {
        try { p.playSound(p.getLocation(), "ui.button.click", 0.6f, 1.2f); } catch (Throwable ignored) { }
    }

    // ================================================================== PERFIL
    public void profile(Player staff, String target) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.PROFILE);
        h.target = target;
        Inventory inv = create(h, 45, "&8Panel &8» &1&l" + target);
        border(inv);
        List<DeathRecord> recs = plugin.deaths().records(target);
        int pending = 0, approved = 0, claimed = 0, byPlayers = 0, paid = 0;
        for (DeathRecord r : recs) {
            switch (r.status) { case "APPROVED" -> approved++; case "CLAIMED" -> claimed++; default -> pending++; }
            if (r.byPlayer()) byPlayers++;
            paid += r.paid;
        }
        String last = recs.isEmpty() ? "&7Nunca" : "&f" + Utils.niceDate(recs.get(0).date) + " &8(" + recs.get(0).cause + ")";
        Player online = Bukkit.getPlayerExact(target);
        List<String> lore = new ArrayList<>();
        lore.add("&8&m                         ");
        lore.add(" &7Estado: " + (online != null ? "&a● Conectado" : "&c● Desconectado"));
        lore.add(" &7Muertes guardadas: &f" + recs.size());
        lore.add(" &7Matado por jugadores: &f" + byPlayers);
        lore.add(" &7Items de paga perdidos: &6" + paid);
        lore.add("");
        lore.add(" &c● Pendientes: &f" + pending);
        lore.add(" &e★ Aprobados: &f" + approved);
        lore.add(" &a✔ Reclamados: &f" + claimed);
        lore.add("");
        lore.add(" &7Ultima muerte: " + last);
        lore.add("&8&m                         ");
        inv.setItem(4, Items.head(uuidOf(target), "&#00CCFF&l" + target, lore));

        inv.setItem(20, Items.make(Material.CHEST, "&#33FF66&lInventario",
                "&7Historial de muertes y", "&7restaurar el &finventario&7.", "", "&a» Click para abrir"));
        inv.setItem(22, Items.make(Material.ENDER_CHEST, "&#D580FF&lEnderChest",
                "&7Historial de muertes y", "&7restaurar el &fenderchest&7.", "", "&d» Click para abrir"));
        inv.setItem(24, Items.make(Material.PURPLE_SHULKER_BOX, "&#00FFFF&lPlayerVaults",
                "&7Abrir los baules virtuales", "&7del jugador (AxVaults).", "", "&b» Click para abrir"));
        inv.setItem(30, Items.make(Material.NETHERITE_SWORD, "&#FF5555&lFiltrar por asesino",
                "&7Elige quien lo mato y ve", "&7solo esas muertes.", "", "&7Tambien: &f/" + plugin.getComandoPrincipal() + " " + target + " deaths <nick>",
                "", "&c» Click para elegir"));
        int scans = plugin.scanner().reportsAbout(target).size();
        inv.setItem(32, Items.make(Material.SPYGLASS, "&#FFAA00&lScanner anti-hacker",
                "&7Reportes donde aparece este", "&7jugador (como hacker o victima).", "", "&7Reportes: &f" + scans, "", "&6» Click para ver"));
        inv.setItem(40, Items.make(Material.BARRIER, "&c&lCerrar"));
        staff.openInventory(inv);
        click(staff);
    }

    // ================================================================== ASESINOS
    public void killers(Player staff, String target) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.KILLERS);
        h.target = target;
        Inventory inv = create(h, 54, "&8Asesinos de &4&l" + target);
        border(inv);
        Map<String, List<DeathRecord>> ks = plugin.deaths().killers(target);
        int i = 0;
        for (Map.Entry<String, List<DeathRecord>> e : ks.entrySet()) {
            if (i >= CENTER.length) break;
            List<DeathRecord> l = e.getValue();
            int pend = 0, paid = 0;
            for (DeathRecord r : l) { if (r.status.equals("PENDING")) pend++; paid += r.paid; }
            UUID u = l.get(0).killerUuid;
            List<String> lore = List.of("&8&m                    ", " &7Lo mato: &f" + l.size() + " vez/veces", " &7Pendientes: &c" + pend,
                    " &7Items de paga: &6" + paid, " &7Ultima vez: &f" + Utils.niceDate(l.get(0).date), "&8&m                    ", "&e» Click para ver esas muertes");
            inv.setItem(CENTER[i], Items.head(u, "&#FF5555&l" + e.getKey(), lore));
            h.slotData.put(CENTER[i], e.getKey());
            i++;
        }
        if (ks.isEmpty()) inv.setItem(22, Items.make(Material.PAPER, "&7Ningun jugador lo ha matado"));
        inv.setItem(49, Items.make(Material.ARROW, "&e&lVolver al panel"));
        inv.setItem(50, Items.make(Material.BOOK, "&f&lVer todas las muertes"));
        staff.openInventory(inv);
        click(staff);
    }

    // ================================================================== HISTORIAL
    public void history(Player staff, String target, int page, String type, String killer) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.HISTORY);
        h.target = target; h.page = page; h.invType = type; h.killer = killer == null ? "" : killer;
        String t = type.equals("EC") ? "&5EnderChest" : "&2Inventario";
        Inventory inv = create(h, 54, t + " &8» &0" + target + (h.killer.isEmpty() ? "" : " &8/ &4" + h.killer));
        border(inv);
        List<DeathRecord> recs = plugin.deaths().records(target, h.killer);
        int per = CENTER.length;
        int start = (page - 1) * per, end = Math.min(start + per, recs.size());
        for (int i = start, s = 0; i < end; i++, s++) {
            DeathRecord r = recs.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&8&m                         ");
            lore.add(" &7Fecha: &f" + Utils.niceDate(r.date));
            lore.add(" &7Causa: &c" + (r.byPlayer() ? "Asesinado por &f" + r.killer : r.cause));
            lore.add(" &7Estado: " + statusColor(r.status) + statusName(r.status));
            lore.add(" &7Items: &f" + (type.equals("EC") ? r.ecItems : r.items) + (r.paid > 0 ? "  &6(" + r.paid + " de paga)" : ""));
            lore.add(" &7Lugar: &f" + r.world + " " + (int) r.x + " " + (int) r.y + " " + (int) r.z);
            if (r.scan != null && !r.scan.isEmpty()) { lore.add(""); lore.add(" &6Scanner: &e" + r.scan); }
            lore.add("&8&m                         ");
            lore.add("&e» Click para ver y restaurar");
            String name = "&#00CCFF&l#" + (i + 1) + " &f" + Utils.niceDate(r.date);
            ItemStack icon = r.byPlayer() ? Items.head(r.killerUuid, name, lore) : Items.make(causeIcon(r.cause), name, lore);
            if (r.status.equals("APPROVED")) icon = Items.glow(icon);
            inv.setItem(CENTER[s], icon);
            h.slotData.put(CENTER[s], r.file);
        }
        if (recs.isEmpty()) inv.setItem(22, Items.make(Material.PAPER, "&7No hay muertes registradas"));
        if (page > 1) inv.setItem(45, Items.make(Material.ARROW, "&e&l« Pagina anterior"));
        if (end < recs.size()) inv.setItem(53, Items.make(Material.ARROW, "&e&lPagina siguiente »"));
        inv.setItem(48, Items.make(Material.OAK_DOOR, "&e&lVolver al panel"));
        inv.setItem(49, Items.make(Material.NAME_TAG, "&f&lPagina " + page + "/" + Math.max(1, (recs.size() + per - 1) / per),
                "&7Total: &f" + recs.size() + " muertes"));
        inv.setItem(50, h.killer.isEmpty()
                ? Items.make(Material.HOPPER, "&c&lFiltrar por asesino", "&7Click para elegir quien lo mato")
                : Items.make(Material.HOPPER, "&c&lFiltro: &f" + h.killer, "&7Click para quitar el filtro"));
        staff.openInventory(inv);
        click(staff);
    }

    private static Material causeIcon(String cause) {
        String c = cause == null ? "" : cause.toLowerCase();
        if (c.contains("fall")) return Material.FEATHER;
        if (c.contains("lava")) return Material.LAVA_BUCKET;
        if (c.contains("fire") || c.contains("hot floor")) return Material.BLAZE_POWDER;
        if (c.contains("drown")) return Material.WATER_BUCKET;
        if (c.contains("void")) return Material.ENDER_PEARL;
        if (c.contains("explosion")) return Material.TNT;
        if (c.contains("projectile")) return Material.ARROW;
        if (c.contains("starv")) return Material.ROTTEN_FLESH;
        if (c.contains("poison") || c.contains("magic") || c.contains("wither")) return Material.POTION;
        if (c.contains("suffoc") || c.contains("cramming")) return Material.GRAVEL;
        if (c.contains("lightning")) return Material.LIGHTNING_ROD;
        if (c.contains("freeze")) return Material.POWDER_SNOW_BUCKET;
        if (c.contains("entity")) return Material.ZOMBIE_HEAD;
        return Material.SKELETON_SKULL;
    }

    // ================================================================== VISTA PREVIA
    public void preview(Player staff, DeathRecord r, String type) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.PREVIEW);
        h.target = r.player; h.file = r.file; h.invType = type;
        Inventory inv = create(h, 54, (type.equals("EC") ? "&5EnderChest" : "&2Inventario") + " &8» &0" + r.player);
        YamlConfiguration data = plugin.deaths().loadData(r);
        ItemStack[] items = plugin.deaths().items(data, type);
        for (int i = 0; i < items.length && i < 41; i++) if (items[i] != null) inv.setItem(i, items[i]);
        ItemStack sep = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 41; i < 45; i++) inv.setItem(i, sep);
        ItemStack bar = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inv.setItem(i, bar);

        List<String> info = new ArrayList<>();
        info.add("&8&m                         ");
        info.add(" &7Jugador: &f" + r.player);
        info.add(" &7Fecha: &f" + Utils.niceDate(r.date));
        info.add(" &7Causa: &c" + (r.byPlayer() ? "Asesinado por &f" + r.killer : r.cause));
        info.add(" &7Estado: " + statusColor(r.status) + statusName(r.status));
        info.add(" &7Items de paga: &6" + r.paid);
        if (r.scan != null && !r.scan.isEmpty()) info.add(" &6Scanner: &e" + r.scan);
        info.add("&8&m                         ");
        info.add("&7Puedes sacar o poner items arriba;");
        info.add("&7los cambios se guardan al usar un boton.");
        inv.setItem(41, Items.make(Material.PAPER, "&f&lInformacion", info));

        inv.setItem(45, Items.make(Material.ARROW, "&e&lVolver al historial"));
        inv.setItem(46, Items.make(Material.ENDER_PEARL, "&b&lIr a la muerte", "&7Teletransportarte al lugar"));
        inv.setItem(47, Items.make(Material.CHEST, "&a&lEntregar al inventario", "&7Le da todo directo", "&7(debe estar conectado)"));
        inv.setItem(48, Items.make(Material.ENDER_CHEST, "&d&lEntregar al enderchest", "&7Lo mete en su enderchest"));
        inv.setItem(49, Items.make(Material.PURPLE_SHULKER_BOX, "&5&lGenerar shulker", "&7Te da shulkers con los items"));
        inv.setItem(50, Items.make(Material.GOLD_INGOT, "&6&lEntregar SIN items de paga",
                "&7Le da todo menos los items", "&7de paga (regla del servidor:", "&7solo si el hacker los tiene)."));
        inv.setItem(51, Items.glow(Items.make(Material.ENCHANTED_BOOK, "&e&lAprobar pendiente", "&7El jugador lo reclama con", "&f/" + plugin.getComandoPrincipal() + " pendiente")));
        if (r.status.equals("APPROVED"))
            inv.setItem(52, Items.make(Material.BARRIER, "&c&lRevocar aprobacion", "&7Vuelve a &cPENDIENTE"));
        else if (r.status.equals("CLAIMED"))
            inv.setItem(52, Items.make(Material.STRUCTURE_VOID, "&8&lRevocar aprobacion", "&cYa fue reclamado."));
        else
            inv.setItem(52, Items.make(Material.GRAY_DYE, "&7&lRevocar aprobacion", "&7Ya esta pendiente."));
        inv.setItem(53, Items.make(Material.SPYGLASS, "&#FFAA00&lScanner", "&7Ver reportes de este jugador"));
        staff.openInventory(inv);
        click(staff);
    }

    // ================================================================== VAULTS
    public void vaults(Player staff, String target) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.VAULTS);
        h.target = target;
        int max = Math.max(1, Math.min(45, plugin.getConfig().getInt("max-player-vaults", 45)));
        Inventory inv = create(h, 54, "&8Vaults &8» &3&l" + target);
        ItemStack bar = Items.filler(Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inv.setItem(i, bar);
        for (int i = 1; i <= max; i++) {
            inv.setItem(i - 1, Items.make(Material.PURPLE_SHULKER_BOX, "&#00FFFF&lVault #" + i, "&7Click para abrir este", "&7baul del jugador."));
            h.slotData.put(i - 1, String.valueOf(i));
        }
        inv.setItem(49, Items.make(Material.ARROW, "&e&lVolver al panel"));
        staff.openInventory(inv);
        click(staff);
    }

    // ================================================================== SCANNER
    public void scans(Player staff, String about, int page) {
        MenuHolder h = new MenuHolder(MenuHolder.Type.SCANS);
        h.target = about == null ? "" : about; h.page = page;
        Inventory inv = create(h, 54, "&8Scanner" + (h.target.isEmpty() ? "" : " &8» &6" + h.target));
        border(inv);
        List<File> files = h.target.isEmpty() ? plugin.scanner().reports(null) : plugin.scanner().reportsAbout(h.target);
        int per = CENTER.length, start = (page - 1) * per, end = Math.min(start + per, files.size());
        for (int i = start, s = 0; i < end; i++, s++) {
            File f = files.get(i);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            int paid = y.getInt("pagas-total"), has = y.getInt("pagas-tiene");
            String verdict = paid == 0 ? "&7Sin items de paga" : (has >= paid ? "&a&lTIENE TODO" : (has > 0 ? "&e&lTIENE ALGUNOS" : "&c&lNO LOS TIENE"));
            List<String> lore = new ArrayList<>();
            lore.add("&8&m                         ");
            lore.add(" &7Fecha: &f" + y.getString("fecha", "?"));
            lore.add(" &7Staff: &f" + y.getString("staff", "?"));
            ConfigurationSection v = y.getConfigurationSection("victimas");
            lore.add(" &7Victimas: &f" + (v == null ? 0 : v.getKeys(false).size()));
            lore.add(" &7Items de paga: &6" + has + "/" + paid + " " + verdict);
            ConfigurationSection rc = y.getConfigurationSection("receptores");
            if (rc != null && !rc.getKeys(false).isEmpty()) lore.add(" &7Paso items a: &e" + String.join(", ", rc.getKeys(false)));
            lore.add("&8&m                         ");
            lore.add("&6» Click para ver el reporte");
            UUID u = null;
            try { u = UUID.fromString(y.getString("uuid", "")); } catch (IllegalArgumentException ignored) { }
            inv.setItem(CENTER[s], Items.head(u, "&#FF5555&l" + y.getString("hacker", "?"), lore));
            h.slotData.put(CENTER[s], f.getName());
        }
        if (files.isEmpty()) inv.setItem(22, Items.make(Material.PAPER, "&7No hay reportes todavia",
                "&7Se crean solos cuando banean", "&7a alguien que mato jugadores."));
        if (page > 1) inv.setItem(45, Items.make(Material.ARROW, "&e&l« Pagina anterior"));
        if (end < files.size()) inv.setItem(53, Items.make(Material.ARROW, "&e&lPagina siguiente »"));
        int watching = plugin.scanner().watches().size();
        inv.setItem(49, Items.make(Material.COMPASS, "&f&lVigilados ahora: &e" + watching,
                "&7Jugadores que mataron a alguien", "&7hace poco. Solo se revisan si", "&7los banean."));
        if (!h.target.isEmpty()) inv.setItem(48, Items.make(Material.OAK_DOOR, "&e&lVolver al panel"));
        staff.openInventory(inv);
        click(staff);
    }

    public void report(Player staff, String fileName) {
        File f = plugin.scanner().reportFile(fileName);
        if (!f.exists()) { staff.sendMessage(Utils.color("&cEse reporte ya no existe.")); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        MenuHolder h = new MenuHolder(MenuHolder.Type.REPORT);
        h.file = fileName; h.target = y.getString("hacker", "");
        Inventory inv = create(h, 54, "&8Reporte &8» &4&l" + h.target);
        border(inv);
        int paid = y.getInt("pagas-total"), has = y.getInt("pagas-tiene");
        UUID u = null;
        try { u = UUID.fromString(y.getString("uuid", "")); } catch (IllegalArgumentException ignored) { }
        inv.setItem(4, Items.head(u, "&#FF5555&l" + h.target, List.of(
                "&8&m                         ",
                " &7Baneado por: &f" + y.getString("staff", "?"),
                " &7Fecha: &f" + y.getString("fecha", "?"),
                " &7Inventario revisado: &f" + y.getString("snapshot", "?"),
                " &7Items de paga que tiene: &6" + has + "/" + paid,
                " &7Items que tiro al suelo: &f" + y.getInt("tiro-items"),
                "&8&m                         ",
                "&7" + y.getString("razon", ""))));
        // victimas (fila 2)
        ConfigurationSection v = y.getConfigurationSection("victimas");
        int[] vs = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        if (v != null) for (String k : v.getKeys(false)) {
            if (i >= vs.length) break;
            List<String> lore = new ArrayList<>();
            lore.add("&8&m                         ");
            lore.add(" " + v.getString(k + ".veredicto", ""));
            List<String> its = v.getStringList(k + ".items");
            if (!its.isEmpty()) { lore.add(""); lore.add(" &7Items de paga que perdio:"); for (String s : its) lore.add("  " + s); }
            lore.add("&8&m                         ");
            lore.add("&e» Click para abrir esa muerte");
            String name = v.getString(k + ".nombre", "?");
            inv.setItem(vs[i], Items.head(null, "&#33FF66&lVictima: &f" + name, lore));
            h.slotData.put(vs[i], name + "|" + v.getString(k + ".archivo", ""));
            i++;
        }
        // a quien le paso las cosas (fila 4)
        ConfigurationSection rc = y.getConfigurationSection("receptores");
        int[] rs = {28, 29, 30, 31, 32, 33, 34};
        int j = 0;
        if (rc != null) for (String k : rc.getKeys(false)) {
            if (j >= rs.length) break;
            inv.setItem(rs[j], Items.head(null, "&#FFAA00&lRecibio items: &f" + k,
                    List.of(" &7Cantidad: &f" + rc.getInt(k), "", "&e» Click para abrir su panel")));
            h.slotData.put(rs[j], "P|" + k);
            j++;
        }
        if (j == 0) inv.setItem(31, Items.make(Material.GRAY_DYE, "&7No le paso items a nadie (registrado)"));
        // registro
        List<String> log = y.getStringList("registro");
        List<String> lore = new ArrayList<>();
        int from = Math.max(0, log.size() - 20);
        for (int k = from; k < log.size(); k++) {
            String[] p = log.get(k).split("\\|", 5);
            if (p.length >= 3) lore.add("&8" + p[0] + " &6" + p[1] + " &7" + p[2]);
        }
        if (lore.isEmpty()) lore.add("&7Sin movimientos");
        lore.add("");
        lore.add("&e» Click para verlo completo en el chat");
        lore.add("&7(click en una linea = teletransporte, cursor = ver el item)");
        inv.setItem(40, Items.make(Material.WRITABLE_BOOK, "&f&lMovimientos del hacker &7(" + log.size() + ")", lore));
        inv.setItem(49, Items.make(Material.ARROW, "&e&lVolver"));
        staff.openInventory(inv);
        click(staff);
    }

    /**
     * Registro completo en el chat:
     * - pasando el cursor se ve el nombre, encantamientos y lore del item,
     * - haciendo click en una linea con lugar, te teletransporta ahi.
     */
    public void printLog(Player staff, String fileName) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(plugin.scanner().reportFile(fileName));
        staff.sendMessage(Utils.color("&8&m          &r &6&lMovimientos de &f" + y.getString("hacker", "?") + " &8&m          "));
        String cmd = plugin.getComandoPrincipal();
        for (String l : y.getStringList("registro")) {
            String[] p = l.split("\\|", 5);
            if (p.length < 3) continue;
            String loc = p.length >= 4 ? p[3] : "";
            String item = p.length >= 5 ? p[4].replace("\\n", "\n").replace("¦", "|") : "";
            String text = Utils.color("&8[" + p[0] + "] &6" + p[1] + " &7" + p[2].replace("¦", "|"));
            net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
            StringBuilder hover = new StringBuilder();
            if (!item.isEmpty()) hover.append(item);
            String[] c = loc.isEmpty() ? new String[0] : loc.split(",");
            boolean tp = c.length == 4;
            if (tp) {
                if (hover.length() > 0) hover.append("\n\n");
                hover.append("\u00a7b\u00bb Click para ir a \u00a7f").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2]).append(" ").append(c[3]);
                line.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                        "/" + cmd + " tp " + c[0] + " " + c[1] + " " + c[2] + " " + c[3]));
            }
            if (hover.length() > 0) {
                line.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.hover.content.Text(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(hover.toString()))));
            }
            if (tp) {
                net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.color(" &b&l[TP]")));
                btn.setClickEvent(line.getClickEvent());
                btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.hover.content.Text(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Utils.color("&bClick para teletransportarte")))));
                line.addExtra(btn);
            }
            staff.spigot().sendMessage(line);
        }
        staff.sendMessage(Utils.color("&7Pasa el cursor sobre una linea para ver el item. Click en &b[TP] &7para ir al lugar."));
    }
}
