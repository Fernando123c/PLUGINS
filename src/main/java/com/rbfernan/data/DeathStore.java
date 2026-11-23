package com.rbfernan.data;

import com.rbfernan.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda las muertes. Cada muerte es un archivo deaths/Jugador_fecha.yml (igual que antes, compatible)
 * y ademas un indice liviano deaths/index/jugador.yml para abrir los menus sin cargar todos los items.
 */
public class DeathStore {
    private final Main plugin;
    private final File dir, indexDir;
    private final Map<String, List<DeathRecord>> cache = new HashMap<>();

    public DeathStore(Main plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "deaths");
        this.indexDir = new File(dir, "index");
        dir.mkdirs();
        indexDir.mkdirs();
    }

    public File dir() { return dir; }
    public File file(String name) { return new File(dir, name); }

    private static String key(String player) { return player.toLowerCase(Locale.ROOT); }

    // ------------------------------------------------------------------ guardar una muerte
    public DeathRecord saveDeath(Player p, String cause, Player killer) {
        String date = com.rbfernan.util.Utils.nowFile();
        String name = p.getName() + "_" + date + ".yml";
        File f = new File(dir, name);
        YamlConfiguration c = new YamlConfiguration();
        ItemStack[] inv = p.getInventory().getContents();
        ItemStack[] ec = p.getEnderChest().getContents();
        DeathRecord r = new DeathRecord();
        r.file = name; r.player = p.getName(); r.playerUuid = p.getUniqueId(); r.date = date; r.cause = cause;
        r.killer = killer != null ? killer.getName() : "";
        r.killerUuid = killer != null ? killer.getUniqueId() : null;
        r.status = "PENDING";
        r.world = p.getWorld().getName();
        r.x = p.getLocation().getX(); r.y = p.getLocation().getY(); r.z = p.getLocation().getZ();
        r.items = count(inv); r.ecItems = count(ec); r.paid = paid(inv) + paid(ec);

        c.set("info.player", r.player);
        c.set("info.uuid", r.playerUuid.toString());
        c.set("info.date", date);
        c.set("info.causa", cause);
        c.set("info.killer", r.killer);
        c.set("info.killer-uuid", r.killerUuid != null ? r.killerUuid.toString() : "");
        c.set("info.status", "PENDING");
        c.set("info.location.world", r.world);
        c.set("info.location.x", r.x);
        c.set("info.location.y", r.y);
        c.set("info.location.z", r.z);
        c.set("info.location.yaw", p.getLocation().getYaw());
        c.set("info.location.pitch", p.getLocation().getPitch());
        c.set("inventory", inv);
        c.set("enderchest", ec);
        // se convierte a texto aqui (rapido) y se escribe al disco en otro hilo (sin lag)
        String text = c.saveToString();
        writeAsync(f, text);
        records(p.getName()).add(0, r);
        saveIndexAsync(p.getName());
        return r;
    }

    private int count(ItemStack[] a) {
        int n = 0;
        for (ItemStack i : a) if (i != null && i.getType() != Material.AIR) n++;
        return n;
    }

    private int paid(ItemStack[] a) {
        int n = 0;
        for (ItemStack i : a) if (i != null && i.getType() != Material.AIR && plugin.paid().isPaid(i)) n++;
        return n;
    }

    private void writeAsync(File f, String text) { com.rbfernan.util.DiskWriter.writeAsync(f, text); }

    // ------------------------------------------------------------------ leer
    /** Muertes de un jugador, de la mas nueva a la mas vieja (desde el indice; rapido). */
    public List<DeathRecord> records(String player) {
        // (no usar computeIfAbsent: loadIndex tambien guarda en el cache y eso daba ConcurrentModificationException)
        List<DeathRecord> list = cache.get(key(player));
        if (list == null) {
            list = loadIndex(player);
            cache.put(key(player), list);
        }
        return list;
    }

    public List<DeathRecord> records(String player, String killerFilter) {
        if (killerFilter == null || killerFilter.isEmpty()) return records(player);
        List<DeathRecord> out = new ArrayList<>();
        for (DeathRecord r : records(player)) if (r.killer != null && r.killer.equalsIgnoreCase(killerFilter)) out.add(r);
        return out;
    }

    /** Asesinos (jugadores) de alguien: nombre -> cantidad de muertes. */
    public Map<String, List<DeathRecord>> killers(String player) {
        Map<String, List<DeathRecord>> m = new java.util.LinkedHashMap<>();
        for (DeathRecord r : records(player)) if (r.byPlayer()) m.computeIfAbsent(r.killer, k -> new ArrayList<>()).add(r);
        return m;
    }

    public DeathRecord find(String player, String file) {
        for (DeathRecord r : records(player)) if (r.file.equals(file)) return r;
        return null;
    }

    private List<DeathRecord> loadIndex(String player) {
        File idx = new File(indexDir, key(player) + ".yml");
        List<DeathRecord> list = new ArrayList<>();
        if (idx.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(idx);
            ConfigurationSection s = y.getConfigurationSection("deaths");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    ConfigurationSection d = s.getConfigurationSection(k);
                    if (d == null) continue;
                    DeathRecord r = new DeathRecord();
                    r.file = d.getString("file", "");
                    if (!new File(dir, r.file).exists()) continue;
                    r.player = d.getString("player", player);
                    r.playerUuid = uuid(d.getString("uuid"));
                    r.date = d.getString("date", "");
                    r.cause = d.getString("cause", "?");
                    r.killer = d.getString("killer", "");
                    r.killerUuid = uuid(d.getString("killer-uuid"));
                    r.status = d.getString("status", "PENDING");
                    r.items = d.getInt("items"); r.ecItems = d.getInt("ec-items"); r.paid = d.getInt("paid");
                    r.world = d.getString("world", ""); r.x = d.getDouble("x"); r.y = d.getDouble("y"); r.z = d.getDouble("z");
                    r.scan = d.getString("scan", "");
                    list.add(r);
                }
            }
            // archivos nuevos que no estan en el indice (por si acaso)
            java.util.Set<String> known = new java.util.HashSet<>();
            for (DeathRecord r : list) known.add(r.file);
            File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).startsWith(key(player) + "_") && n.endsWith(".yml"));
            boolean changed = false;
            if (files != null) for (File f : files) if (!known.contains(f.getName())) { list.add(fromFile(f, player)); changed = true; }
            sort(list);
            if (changed) { cache.put(key(player), list); saveIndexAsync(player); }
            return list;
        }
        // registros viejos sin indice: se lee cada archivo una sola vez y se crea el indice
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).startsWith(key(player) + "_") && n.endsWith(".yml"));
        if (files != null) for (File f : files) list.add(fromFile(f, player));
        sort(list);
        cache.put(key(player), list);
        if (!list.isEmpty()) saveIndexAsync(player);
        return list;
    }

    private DeathRecord fromFile(File f, String player) {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        DeathRecord r = new DeathRecord();
        r.file = f.getName();
        r.player = c.getString("info.player", player);
        r.playerUuid = uuid(c.getString("info.uuid"));
        r.date = c.getString("info.date", dateFromName(f.getName()));
        r.cause = c.getString("info.causa", "Desconocida");
        r.killer = c.getString("info.killer", "");
        if (r.killer.isEmpty() && c.getString("info.killer") == null) r.killer = oldKiller(r.cause);
        r.killerUuid = uuid(c.getString("info.killer-uuid"));
        r.status = c.getString("info.status", "PENDING");
        List<?> inv = c.getList("inventory"), ec = c.getList("enderchest");
        r.items = countList(inv); r.ecItems = countList(ec);
        r.paid = paidList(inv) + paidList(ec);
        r.world = c.getString("info.location.world", "");
        r.x = c.getDouble("info.location.x"); r.y = c.getDouble("info.location.y"); r.z = c.getDouble("info.location.z");
        r.scan = c.getString("info.scan", "");
        return r;
    }

    /** Registros viejos: la "causa" era el nombre del asesino si fue un jugador, o la causa en minusculas. */
    private static String oldKiller(String cause) {
        if (cause == null || !cause.matches("[A-Za-z0-9_]{3,16}")) return "";
        if (cause.equals("Natural/Entorno")) return "";
        for (org.bukkit.event.entity.EntityDamageEvent.DamageCause dc : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
            if (dc.name().replace("_", " ").toLowerCase(Locale.ROOT).equals(cause)) return "";
        }
        return cause;
    }

    private int countList(List<?> l) {
        int n = 0;
        if (l != null) for (Object o : l) if (o instanceof ItemStack i && i.getType() != Material.AIR) n++;
        return n;
    }

    private int paidList(List<?> l) {
        int n = 0;
        if (l != null) for (Object o : l) if (o instanceof ItemStack i && i.getType() != Material.AIR && plugin.paid().isPaid(i)) n++;
        return n;
    }

    private static String dateFromName(String n) {
        String s = n.replace(".yml", "");
        return s.length() >= 19 ? s.substring(s.length() - 19) : "";
    }

    private static void sort(List<DeathRecord> list) {
        list.sort(Comparator.comparing((DeathRecord r) -> r.date == null ? "" : r.date).reversed());
    }

    private static UUID uuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public void saveIndexAsync(String player) {
        String text = indexText(player);
        if (text != null) writeAsync(new File(indexDir, key(player) + ".yml"), text);
    }

    /** Se usa al aprobar/revocar/reclamar: se guarda en el momento (si el servidor se reinicia no se pierde). */
    public void saveIndexNow(String player) {
        String text = indexText(player);
        if (text != null) com.rbfernan.util.DiskWriter.writeNow(new File(indexDir, key(player) + ".yml"), text);
    }

    private String indexText(String player) {
        List<DeathRecord> list = cache.get(key(player));
        if (list == null) return null;
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (DeathRecord r : list) {
            String p = "deaths.d" + (i++);
            y.set(p + ".file", r.file);
            y.set(p + ".player", r.player);
            y.set(p + ".uuid", r.playerUuid != null ? r.playerUuid.toString() : "");
            y.set(p + ".date", r.date);
            y.set(p + ".cause", r.cause);
            y.set(p + ".killer", r.killer);
            y.set(p + ".killer-uuid", r.killerUuid != null ? r.killerUuid.toString() : "");
            y.set(p + ".status", r.status);
            y.set(p + ".items", r.items);
            y.set(p + ".ec-items", r.ecItems);
            y.set(p + ".paid", r.paid);
            y.set(p + ".world", r.world);
            y.set(p + ".x", r.x); y.set(p + ".y", r.y); y.set(p + ".z", r.z);
            if (r.scan != null && !r.scan.isEmpty()) y.set(p + ".scan", r.scan);
        }
        return y.saveToString();
    }

    // ------------------------------------------------------------------ cambios
    public YamlConfiguration loadData(DeathRecord r) {
        return YamlConfiguration.loadConfiguration(new File(dir, r.file));
    }

    public void saveData(DeathRecord r, YamlConfiguration c) {
        com.rbfernan.util.DiskWriter.writeNow(new File(dir, r.file), c.saveToString());
    }

    public void setStatus(DeathRecord r, String status) {
        YamlConfiguration c = loadData(r);
        c.set("info.status", status);
        saveData(r, c);
        r.status = status;
        saveIndexNow(r.player);
    }

    public void setScan(DeathRecord r, String verdict) {
        r.scan = verdict;
        saveIndexAsync(r.player);
    }

    public ItemStack[] items(YamlConfiguration c, String type) {
        List<?> l = c.getList(type.equals("EC") ? "enderchest" : "inventory");
        if (l == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = l.get(i) instanceof ItemStack it ? it : null;
        return out;
    }

    /** Muertes recientes (en los ultimos "minutes") causadas por un asesino. */
    public List<DeathRecord> killedBy(String killer, List<String> victims) {
        List<DeathRecord> out = new ArrayList<>();
        for (String v : victims) for (DeathRecord r : records(v)) if (killer.equalsIgnoreCase(r.killer)) out.add(r);
        return out;
    }

    public void clearAll() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) f.delete();
        File[] idx = indexDir.listFiles();
        if (idx != null) for (File f : idx) f.delete();
        cache.clear();
    }

    public void forget(String player) { cache.remove(key(player)); }
}
