package com.rbfernan.scanner;

import com.rbfernan.Main;
import com.rbfernan.data.DeathRecord;
import com.rbfernan.util.Items;
import com.rbfernan.util.Utils;
import com.rbfernan.util.Webhook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Scanner anti-hacker liviano.
 * - Solo se vigila (en memoria, solo texto) a quien MATA a otro jugador, por un tiempo limitado.
 * - No se analiza nada hasta que ese jugador es baneado (/ipban, /ban...). Ahi se arma el reporte:
 *   si tiene los items de paga de sus victimas, a quien se los paso, si los tiro o guardo, y que comandos uso.
 */
public class Scanner {

    /** Lo que hace un jugador vigilado (solo texto, muy liviano). */
    public static final class Watch {
        public String name; public UUID uuid; public long until;
        public final List<String> log = new ArrayList<>();                 // "hora|tipo|texto"
        public final List<String> victimFiles = new ArrayList<>();         // "victima|archivo"
        public final Map<String, Integer> receivers = new LinkedHashMap<>(); // jugador -> items que recibio
        public final List<String> snapshot = new ArrayList<>();            // huellas de lo que tenia (al ban o al salir)
        public String snapshotWhen = "";
        public int dropped;
    }

    /** Duenio de un item tirado en el suelo. */
    private static final class Owner {
        String watcher;   // jugador vigilado responsable (asesino o el que lo tiro)
        String victim;    // si viene de la muerte de alguien
        long time;
    }

    private static final class PendingDeath {
        Location loc; String victim; String killer; long tick;
    }

    private final Main plugin;
    private final Map<UUID, Watch> watches = new HashMap<>();
    private final Map<UUID, Owner> groundItems = new HashMap<>();
    private final Map<String, String> containers = new HashMap<>();     // "mundo,x,y,z" -> vigilado que guardo ahi
    private final Map<String, Long> containerTime = new HashMap<>();
    private final List<PendingDeath> pendingDeaths = new ArrayList<>();
    private final Map<String, String> lastIp = new HashMap<>();          // nombre -> ip (para /ipban con IP)
    private final File dir, watchFile;
    private long tick;

    public Scanner(Main plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "scans");
        this.watchFile = new File(dir, "vigilados.yml");
        dir.mkdirs();
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick++, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsync, 6000L, 6000L);
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("scanner");
        return s != null ? s : plugin.getConfig().createSection("scanner");
    }

    public boolean enabled() { return cfg().getBoolean("activado", true); }

    public boolean watching(Player p) { return !watches.isEmpty() && watches.containsKey(p.getUniqueId()); }
    public boolean anyWatch() { return !watches.isEmpty() || !containers.isEmpty(); }

    private Watch watch(Player p) { return watches.get(p.getUniqueId()); }

    private void log(Watch w, String type, String text) { log(w, type, text, null, null); }

    /**
     * Linea del registro: "hora|tipo|texto|mundo,x,y,z|info del item".
     * El lugar sirve para el click de teletransporte y la info del item para el texto al pasar el cursor.
     */
    private void log(Watch w, String type, String text, Location loc, ItemStack item) {
        String l = loc == null || loc.getWorld() == null ? "" : loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        String hover = item == null ? "" : Items.hoverText(item).replace("|", "¦").replace("\n", "\\n");
        w.log.add(Utils.now().substring(11) + "|" + type + "|" + text.replace("|", "¦") + "|" + l + "|" + hover);
        int max = cfg().getInt("max-lineas", 300);
        while (w.log.size() > max) w.log.remove(0);
    }

    private static String where(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private String itemText(ItemStack it) {
        if (it == null) return "?";
        String paid = plugin.paid().isPaid(it) ? " &6[PAGA]" : "";
        return it.getAmount() + "x " + Items.label(it) + paid;
    }

    // ------------------------------------------------------------------ eventos (los llama ScanListener)
    public void onKill(Player killer, Player victim, DeathRecord r) {
        if (!enabled()) return;
        Watch w = watches.computeIfAbsent(killer.getUniqueId(), k -> {
            Watch n = new Watch(); n.name = killer.getName(); n.uuid = killer.getUniqueId(); return n;
        });
        w.until = System.currentTimeMillis() + cfg().getLong("minutos-vigilancia", 120) * 60_000L;
        w.victimFiles.add(victim.getName() + "|" + r.file);
        log(w, "KILL", "Mato a &f" + victim.getName() + " &7en " + where(victim.getLocation()) + (r.paid > 0 ? " &6(" + r.paid + " items de paga)" : ""), victim.getLocation(), null);
        PendingDeath pd = new PendingDeath();
        pd.loc = victim.getLocation(); pd.victim = victim.getName(); pd.killer = killer.getName(); pd.tick = tick;
        pendingDeaths.add(pd);
    }

    /** Muerte de un jugador vigilado (sus items caen al suelo). */
    public void onWatchedDeath(Player p) {
        Watch w = watch(p);
        if (w == null) return;
        log(w, "MUERTE", "Murio y tiro sus items en " + where(p.getLocation()), p.getLocation(), null);
        PendingDeath pd = new PendingDeath();
        pd.loc = p.getLocation(); pd.victim = p.getName(); pd.killer = p.getName(); pd.tick = tick;
        pendingDeaths.add(pd);
    }

    /** Un item aparecio en el suelo: si es de una muerte reciente, se recuerda de quien es. */
    public void onItemSpawn(org.bukkit.entity.Item item) {
        if (pendingDeaths.isEmpty()) return;
        Location l = item.getLocation();
        for (Iterator<PendingDeath> it = pendingDeaths.iterator(); it.hasNext(); ) {
            PendingDeath pd = it.next();
            if (tick - pd.tick > 2) { it.remove(); continue; }
            if (pd.loc.getWorld() == l.getWorld() && pd.loc.distanceSquared(l) < 9) {
                Owner o = new Owner();
                o.watcher = pd.killer; o.victim = pd.victim; o.time = System.currentTimeMillis();
                groundItems.put(item.getUniqueId(), o);
                return;
            }
        }
    }

    public void onDrop(Player p, org.bukkit.entity.Item drop) {
        Watch w = watch(p);
        if (w == null) return;
        ItemStack it = drop.getItemStack();
        w.dropped += it.getAmount();
        log(w, "TIRO", "Tiro " + itemText(it) + " &7en " + where(p.getLocation()), p.getLocation(), it);
        Owner o = new Owner();
        o.watcher = p.getName(); o.time = System.currentTimeMillis();
        groundItems.put(drop.getUniqueId(), o);
    }

    public void onPickup(Player picker, org.bukkit.entity.Item item) {
        if (groundItems.isEmpty()) return;
        Owner o = groundItems.remove(item.getUniqueId());
        if (o == null) return;
        Watch w = byName(o.watcher);
        if (w == null) return;
        ItemStack it = item.getItemStack();
        boolean self = picker.getName().equalsIgnoreCase(o.watcher);
        if (o.victim != null && !o.victim.equalsIgnoreCase(o.watcher)) {
            if (self) log(w, "RECOGIO", "Recogio " + itemText(it) + " &7de la muerte de &f" + o.victim, item.getLocation(), it);
            else {
                log(w, "OTRO", "&e" + picker.getName() + " &7recogio " + itemText(it) + " &7de la muerte de &f" + o.victim, item.getLocation(), it);
                w.receivers.merge(picker.getName(), it.getAmount(), Integer::sum);
            }
        } else if (!self) {
            log(w, "PASO", "&e" + picker.getName() + " &7recogio " + itemText(it) + " &7que tiro &f" + o.watcher, item.getLocation(), it);
            w.receivers.merge(picker.getName(), it.getAmount(), Integer::sum);
        }
    }

    /** Guardo items en un contenedor (cofre, barril, shulker, enderchest...). */
    public void onDeposit(Player p, ItemStack it, Location loc, String type) {
        Watch w = watch(p);
        if (w == null || it == null || it.getType() == Material.AIR) return;
        if (loc == null) { log(w, "GUARDO", "Guardo " + itemText(it) + " &7en su " + type, null, it); return; }
        log(w, "GUARDO", "Guardo " + itemText(it) + " &7en " + type + " (" + where(loc) + ")", loc, it);
        String k = key(loc);
        containers.put(k, p.getName());
        containerTime.put(k, System.currentTimeMillis());
    }

    /** Alguien saco items de un contenedor donde un vigilado guardo cosas. */
    public void onTake(Player p, ItemStack it, Location loc) {
        if (loc == null || containers.isEmpty() || it == null || it.getType() == Material.AIR) return;
        String owner = containers.get(key(loc));
        if (owner == null || owner.equalsIgnoreCase(p.getName())) return;
        Watch w = byName(owner);
        if (w == null) return;
        log(w, "PASO", "&e" + p.getName() + " &7saco " + itemText(it) + " &7del contenedor donde guardo cosas (" + where(loc) + ")", loc, it);
        w.receivers.merge(p.getName(), it.getAmount(), Integer::sum);
    }

    public void onCommand(Player p, String cmd) {
        Watch w = watch(p);
        if (w == null) return;
        if (cmd.length() > 120) cmd = cmd.substring(0, 120) + "...";
        log(w, "COMANDO", "&f" + cmd);
    }

    public void onJoin(Player p) {
        if (p.getAddress() != null && p.getAddress().getAddress() != null) lastIp.put(p.getName().toLowerCase(Locale.ROOT), p.getAddress().getAddress().getHostAddress());
    }

    public void onQuit(Player p) {
        Watch w = watch(p);
        if (w != null) snapshot(w, p, "al desconectarse");
    }

    private void snapshot(Watch w, Player p, String when) {
        w.snapshot.clear();
        for (ItemStack i : p.getInventory().getContents()) if (i != null && i.getType() != Material.AIR) w.snapshot.add(Items.signature(i) + "#" + i.getAmount() + "#inv#" + Items.label(i));
        for (ItemStack i : p.getEnderChest().getContents()) if (i != null && i.getType() != Material.AIR) w.snapshot.add(Items.signature(i) + "#" + i.getAmount() + "#ec#" + Items.label(i));
        w.snapshotWhen = Utils.now() + " (" + when + ")";
    }

    private Watch byName(String name) {
        if (name == null) return null;
        for (Watch w : watches.values()) if (w.name.equalsIgnoreCase(name)) return w;
        return null;
    }

    private static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ------------------------------------------------------------------ baneos
    /** Revisa si un comando es un ban; si lo es, escanea al baneado. */
    public void checkBanCommand(String sender, String commandLine) {
        if (!enabled()) return;
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return;
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        List<String> bans = new ArrayList<>();
        for (String s : cfg().getStringList("comandos-de-ban")) bans.add(s.toLowerCase(Locale.ROOT));
        if (!bans.contains(label)) return;
        String target = null;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("-")) continue;   // flags tipo -s
            target = parts[i];
            break;
        }
        if (target == null) return;
        List<String> names = new ArrayList<>();
        if (target.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            for (Map.Entry<String, String> e : lastIp.entrySet()) if (e.getValue().equals(target)) names.add(e.getKey());
        } else names.add(target);
        for (String n : names) {
            Watch w = byName(n);
            if (w == null) {
                if (cfg().getBoolean("avisar-si-no-hay-datos", false)) notifyStaff("&7Scanner: &f" + n + " &7fue baneado pero no mato a nadie recientemente. No hay nada que revisar.");
                continue;
            }
            Player online = Bukkit.getPlayerExact(w.name);
            if (online != null) snapshot(w, online, "al momento del ban");   // antes de que lo saquen
            final String staff = sender;
            Bukkit.getScheduler().runTaskLater(plugin, () -> scan(w, staff, "Ban detectado: /" + line), 2L);
        }
    }

    /** Escaneo manual (/rbfernan scan jugador). */
    public boolean manualScan(String name, String staff) {
        Watch w = byName(name);
        if (w == null) return false;
        Player online = Bukkit.getPlayerExact(w.name);
        if (online != null) snapshot(w, online, "escaneo manual");
        scan(w, staff, "Escaneo manual");
        return true;
    }

    // ------------------------------------------------------------------ reporte
    public static final class VictimResult {
        public String victim, file, verdict;
        public int paidLost, hackerHas;
        public List<String> paidNames = new ArrayList<>();
    }

    private void scan(Watch w, String staff, String reason) {
        List<VictimResult> results = new ArrayList<>();
        Map<String, Integer> snap = new HashMap<>();
        for (String s : w.snapshot) {
            String[] p = s.split("#");
            int amount = p.length > 1 ? parseInt(p[1]) : 1;
            snap.merge(p[0], amount, Integer::sum);
        }
        int totalPaid = 0, totalHas = 0;
        for (String vf : w.victimFiles) {
            String[] p = vf.split("\\|", 2);
            if (p.length < 2) continue;
            DeathRecord r = plugin.deaths().find(p[0], p[1]);
            VictimResult vr = new VictimResult();
            vr.victim = p[0]; vr.file = p[1];
            if (r == null) { vr.verdict = "Registro no encontrado"; results.add(vr); continue; }
            YamlConfiguration data = plugin.deaths().loadData(r);
            List<ItemStack> lost = new ArrayList<>(Arrays.asList(plugin.deaths().items(data, "INV")));
            for (ItemStack it : lost) {
                if (it == null || it.getType() == Material.AIR || !plugin.paid().isPaid(it)) continue;
                vr.paidLost++;
                String sig = Items.signature(it);
                Integer have = snap.get(sig);
                boolean has = have != null && have > 0;
                if (has) { vr.hackerHas++; snap.put(sig, have - it.getAmount()); }
                vr.paidNames.add((has ? "&a✔ " : "&c✘ ") + "&f" + it.getAmount() + "x " + Items.label(it));
            }
            if (vr.paidLost == 0) vr.verdict = "&7Sin items de paga";
            else if (vr.hackerHas >= vr.paidLost) vr.verdict = "&a&lTIENE TODO &7(" + vr.hackerHas + "/" + vr.paidLost + ") &a-> se pueden devolver";
            else if (vr.hackerHas > 0) vr.verdict = "&e&lTIENE ALGUNOS &7(" + vr.hackerHas + "/" + vr.paidLost + ")";
            else vr.verdict = "&c&lNO LOS TIENE &7(0/" + vr.paidLost + ") &c-> revisa a quien se los paso";
            totalPaid += vr.paidLost; totalHas += vr.hackerHas;
            plugin.deaths().setScan(r, Utils.strip(vr.verdict) + " | hacker: " + w.name);
            results.add(vr);
        }
        String date = Utils.nowFile();
        YamlConfiguration y = new YamlConfiguration();
        y.set("hacker", w.name);
        y.set("uuid", w.uuid != null ? w.uuid.toString() : "");
        y.set("staff", staff);
        y.set("razon", reason);
        y.set("fecha", Utils.now());
        y.set("snapshot", w.snapshotWhen.isEmpty() ? "No se pudo ver su inventario (no estaba conectado)" : w.snapshotWhen);
        y.set("tiro-items", w.dropped);
        y.set("pagas-total", totalPaid);
        y.set("pagas-tiene", totalHas);
        int i = 0;
        for (VictimResult vr : results) {
            String p = "victimas.v" + (i++);
            y.set(p + ".nombre", vr.victim);
            y.set(p + ".archivo", vr.file);
            y.set(p + ".veredicto", vr.verdict);
            y.set(p + ".pagas", vr.paidLost);
            y.set(p + ".tiene", vr.hackerHas);
            y.set(p + ".items", vr.paidNames);
        }
        for (Map.Entry<String, Integer> e : w.receivers.entrySet()) y.set("receptores." + e.getKey(), e.getValue());
        y.set("registro", new ArrayList<>(w.log));
        String fname = w.name + "_" + date + ".yml";
        writeAsync(new File(dir, fname), y.saveToString());

        String summary = totalPaid == 0 ? "sus victimas no tenian items de paga"
                : (totalHas >= totalPaid ? "TIENE todos los items de paga (" + totalHas + "/" + totalPaid + ")"
                : "tiene " + totalHas + " de " + totalPaid + " items de paga");
        notifyStaff("&c&lSCANNER &8» &f" + w.name + " &7fue baneado. Resultado: &e" + summary
                + (w.receivers.isEmpty() ? "" : " &7| Paso items a: &e" + String.join(", ", w.receivers.keySet()))
                + " &8(/" + plugin.getComandoPrincipal() + " scanner " + w.name + ")");
        sendDiscord(w, staff, reason, results, totalPaid, totalHas);
        watches.remove(w.uuid);
        saveAsync();
    }

    private void notifyStaff(String msg) {
        String m = Utils.color(plugin.getConfig().getString("configuracion.prefix", "") + msg);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("rbfernan.admin")) p.sendMessage(m);
        Bukkit.getConsoleSender().sendMessage(m);
    }

    private void sendDiscord(Watch w, String staff, String reason, List<VictimResult> results, int totalPaid, int totalHas) {
        String url = cfg().getString("discord-webhook", "");
        if (url == null || url.isEmpty()) url = plugin.getConfig().getString("discord-webhook", "");
        if (url == null || !url.startsWith("http")) return;
        StringBuilder victims = new StringBuilder();
        for (VictimResult vr : results) {
            victims.append("**").append(vr.victim).append("** → ").append(Utils.strip(vr.verdict)).append("\n");
            for (String n : vr.paidNames) victims.append("　").append(Utils.strip(n)).append("\n");
        }
        if (victims.length() == 0) victims.append("Sin victimas registradas");
        StringBuilder rec = new StringBuilder();
        for (Map.Entry<String, Integer> e : w.receivers.entrySet()) rec.append(e.getKey()).append(" (").append(e.getValue()).append(" items)\n");
        if (rec.length() == 0) rec.append("Nadie");
        StringBuilder log = new StringBuilder();
        int from = Math.max(0, w.log.size() - 15);
        for (int i = from; i < w.log.size(); i++) {
            String[] p = w.log.get(i).split("\\|", 5);
            if (p.length >= 3) log.append("`").append(p[0]).append("` ").append(Utils.strip(p[2])).append("\n");
        }
        if (log.length() == 0) log.append("Sin movimientos");
        int color = totalPaid == 0 ? 0x808080 : (totalHas >= totalPaid ? 0x2ECC71 : (totalHas > 0 ? 0xF1C40F : 0xE74C3C));
        String json = "{\"embeds\":[{"
                + "\"title\":\"" + Utils.json(cfg().getString("discord-titulo", "🔎 Scanner RbFernan | Hacker baneado")) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + field("🚫 Baneado", w.name, true) + ","
                + field("🛡️ Staff", staff, true) + ","
                + field("📅 Fecha", Utils.now(), true) + ","
                + field("💎 Items de paga", totalPaid == 0 ? "Ninguno" : totalHas + " / " + totalPaid + " los tiene el hacker", false) + ","
                + field("⚔️ Victimas", cut(victims.toString()), false) + ","
                + field("🤝 Paso items a", cut(rec.toString()), false) + ","
                + field("📦 Inventario revisado", w.snapshotWhen.isEmpty() ? "No (estaba desconectado)" : w.snapshotWhen, false) + ","
                + field("📝 Ultimos movimientos", cut(log.toString()), false)
                + "],\"footer\":{\"text\":\"" + Utils.json(reason + " | Reporte completo en el archivo adjunto") + "\"}}]}";
        if (cfg().getBoolean("discord-adjuntar-reporte", true))
            Webhook.sendWithFile(plugin, url, json, "scanner_" + w.name + "_" + Utils.nowFile() + ".txt", fullText(w, staff, reason, results, totalPaid, totalHas));
        else Webhook.send(plugin, url, json);
    }

    /** Reporte completo en texto (va adjunto en Discord). */
    private String fullText(Watch w, String staff, String reason, List<VictimResult> results, int totalPaid, int totalHas) {
        StringBuilder b = new StringBuilder();
        b.append("===== SCANNER RBFERNAN =====\n");
        b.append("Baneado: ").append(w.name).append("\n");
        b.append("Staff: ").append(staff).append("\n");
        b.append("Fecha: ").append(Utils.now()).append("\n");
        b.append("Motivo: ").append(reason).append("\n");
        b.append("Inventario revisado: ").append(w.snapshotWhen.isEmpty() ? "No (estaba desconectado)" : w.snapshotWhen).append("\n");
        b.append("Items de paga que tiene: ").append(totalHas).append(" / ").append(totalPaid).append("\n");
        b.append("Items que tiro al suelo: ").append(w.dropped).append("\n\n");
        b.append("----- VICTIMAS -----\n");
        for (VictimResult vr : results) {
            b.append("* ").append(vr.victim).append(": ").append(Utils.strip(vr.verdict)).append("\n");
            for (String n : vr.paidNames) b.append("    ").append(Utils.strip(n)).append("\n");
        }
        if (results.isEmpty()) b.append("(ninguna)\n");
        b.append("\n----- LE PASO ITEMS A -----\n");
        for (Map.Entry<String, Integer> e : w.receivers.entrySet()) b.append("* ").append(e.getKey()).append(" (").append(e.getValue()).append(" items)\n");
        if (w.receivers.isEmpty()) b.append("(nadie registrado)\n");
        b.append("\n----- LO QUE TENIA AL REVISARLO -----\n");
        for (String s : w.snapshot) {
            String[] p = s.split("#", 4);
            if (p.length == 4) b.append("* ").append(p[1]).append("x ").append(p[3]).append(" (").append(p[2].equals("ec") ? "enderchest" : "inventario").append(")\n");
        }
        if (w.snapshot.isEmpty()) b.append("(no se pudo revisar)\n");
        b.append("\n----- TODOS LOS MOVIMIENTOS -----\n");
        for (String l : w.log) {
            String[] p = l.split("\\|", 5);
            if (p.length >= 3) {
                b.append("[").append(p[0]).append("] ").append(p[1]).append(": ").append(Utils.strip(p[2]));
                if (p.length >= 5 && !p[4].isEmpty()) b.append("  {").append(Utils.strip(p[4].replace("\\n", " / "))).append("}");
                b.append("\n");
            }
        }
        if (w.log.isEmpty()) b.append("(sin movimientos)\n");
        return b.toString();
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + Utils.json(name) + "\",\"value\":\"" + Utils.json(value == null || value.isEmpty() ? "-" : value) + "\",\"inline\":" + inline + "}";
    }

    private static String cut(String s) { return s.length() > 1000 ? s.substring(0, 997) + "..." : s; }

    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; } }

    // ------------------------------------------------------------------ reportes guardados
    public List<File> reports(String hacker) {
        File[] f = dir.listFiles((d, n) -> n.endsWith(".yml") && !n.equals("vigilados.yml")
                && (hacker == null || n.toLowerCase(Locale.ROOT).startsWith(hacker.toLowerCase(Locale.ROOT) + "_")));
        List<File> out = f == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(f));
        out.sort((a, b) -> b.getName().compareTo(a.getName()));
        return out;
    }

    /** Reportes donde este jugador fue hacker o victima. */
    public List<File> reportsAbout(String player) {
        List<File> out = new ArrayList<>();
        String low = player.toLowerCase(Locale.ROOT);
        for (File f : reports(null)) {
            if (f.getName().toLowerCase(Locale.ROOT).startsWith(low + "_")) { out.add(f); continue; }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection v = y.getConfigurationSection("victimas");
            if (v != null) for (String k : v.getKeys(false)) if (player.equalsIgnoreCase(v.getString(k + ".nombre"))) { out.add(f); break; }
        }
        return out;
    }

    public File reportFile(String name) { return new File(dir, name); }

    public java.util.Collection<Watch> watches() { return watches.values(); }

    // ------------------------------------------------------------------ mantenimiento
    private void cleanup() {
        long now = System.currentTimeMillis();
        watches.values().removeIf(w -> w.until < now);
        groundItems.values().removeIf(o -> now - o.time > 15 * 60_000L);
        containerTime.entrySet().removeIf(e -> {
            if (now - e.getValue() > cfg().getLong("minutos-vigilancia", 120) * 60_000L) { containers.remove(e.getKey()); return true; }
            return false;
        });
        if (lastIp.size() > 5000) lastIp.clear();
    }

    private void writeAsync(File f, String text) { com.rbfernan.util.DiskWriter.writeAsync(f, text); }

    private YamlConfiguration serialize() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Watch w : watches.values()) {
            String p = "w" + (i++);
            y.set(p + ".name", w.name);
            y.set(p + ".uuid", w.uuid.toString());
            y.set(p + ".until", w.until);
            y.set(p + ".log", w.log);
            y.set(p + ".victims", w.victimFiles);
            y.set(p + ".snapshot", w.snapshot);
            y.set(p + ".snapshot-when", w.snapshotWhen);
            y.set(p + ".dropped", w.dropped);
            for (Map.Entry<String, Integer> e : w.receivers.entrySet()) y.set(p + ".receivers." + e.getKey(), e.getValue());
        }
        return y;
    }

    public void saveAsync() { writeAsync(watchFile, serialize().saveToString()); }

    public void saveNow() {
        com.rbfernan.util.DiskWriter.writeNow(watchFile, serialize().saveToString());
    }

    private void load() {
        if (!watchFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(watchFile);
        long now = System.currentTimeMillis();
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null || s.getLong("until") < now) continue;
            Watch w = new Watch();
            w.name = s.getString("name");
            try { w.uuid = UUID.fromString(s.getString("uuid", "")); } catch (IllegalArgumentException e) { continue; }
            w.until = s.getLong("until");
            w.log.addAll(s.getStringList("log"));
            w.victimFiles.addAll(s.getStringList("victims"));
            w.snapshot.addAll(s.getStringList("snapshot"));
            w.snapshotWhen = s.getString("snapshot-when", "");
            w.dropped = s.getInt("dropped");
            ConfigurationSection r = s.getConfigurationSection("receivers");
            if (r != null) for (String n : r.getKeys(false)) w.receivers.put(n, r.getInt(n));
            watches.put(w.uuid, w);
        }
    }
}
