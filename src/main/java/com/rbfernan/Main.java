package com.rbfernan;

import com.rbfernan.commands.RbCommand;
import com.rbfernan.data.DeathStore;
import com.rbfernan.license.LicenseClient;
import com.rbfernan.listeners.DeathListener;
import com.rbfernan.listeners.MenuListener;
import com.rbfernan.listeners.ScanListener;
import com.rbfernan.menu.Menus;
import com.rbfernan.scanner.Scanner;
import com.rbfernan.util.PaidItems;
import com.rbfernan.util.Utils;
import com.rbfernan.util.Webhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class Main extends JavaPlugin {
    private String comandoPrincipal;
    private DeathStore deaths;
    private Menus menus;
    private MenuListener menuListener;
    private Scanner scanner;
    private final PaidItems paid = new PaidItems();
    private File logFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        com.rbfernan.util.DiskWriter.init(this);

        LicenseClient.Result lic = LicenseClient.check(this);
        if (!lic.ok || !lic.approved) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  [RbFernan] No se pudo activar: " + lic.message);
            if (lic.status.equals("pending"))
                Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "  [RbFernan] Aprueba este servidor en tu panel web y reinicia.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "");
            onLicenseFail();
            return;
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "  [RbFernan] Licencia OK: " + lic.message);
        startLicenseWatcher();
        paid.load(getConfig().getConfigurationSection("items-de-paga"));
        logFile = new File(getDataFolder(), "staff-logs.yml");
        deaths = new DeathStore(this);
        scanner = new Scanner(this);
        menus = new Menus(this);
        menuListener = new MenuListener(this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(menuListener, this);
        getServer().getPluginManager().registerEvents(new ScanListener(this), this);
        registerCommand();
        getLogger().info("RbFernan PRO v" + getDescription().getVersion() + " activado (1.20.x - 1.21.x).");
    }

    @Override
    public void onDisable() {
        if (scanner != null) scanner.saveNow();
        com.rbfernan.util.DiskWriter.flush();   // nada se pierde si el servidor se reinicia
    }

    public void reloadAll() {
        reloadConfig();
        paid.load(getConfig().getConfigurationSection("items-de-paga"));
    }

    private void registerCommand() {
        comandoPrincipal = getConfig().getString("comando-principal", "rbfernan");
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap map = (CommandMap) f.get(Bukkit.getServer());
            map.register(getDescription().getName().toLowerCase(), new RbCommand(comandoPrincipal, this));
        } catch (Exception e) {
            getLogger().severe("No se pudo registrar /" + comandoPrincipal + ": " + e.getMessage());
        }
    }

    /** Qué hacer cuando la licencia falla o se revoca: desactivar el plugin (por defecto) o apagar el servidor. */
    private void onLicenseFail() {
        String mode = getConfig().getString("licencia.al-fallar", "desactivar").toLowerCase();
        if (mode.startsWith("apag")) Bukkit.shutdown();
        else Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
    }

    /** Revisa la licencia cada cierto tiempo. Si el staff la rechaza o la borra desde la web, el plugin se apaga solo. */
    private void startLicenseWatcher() {
        long min = Math.max(1L, getConfig().getLong("licencia.minutos-revision", 30));
        long ticks = min * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            LicenseClient.Result r = LicenseClient.check(this);
            // Solo apaga si el panel respondio claramente que NO (no por un corte de internet momentaneo).
            if (!r.status.equals("error") && (!r.ok || !r.approved)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  [RbFernan] Licencia revocada o no aprobada: " + r.message);
                    onLicenseFail();
                });
            }
        }, ticks, ticks);
    }

    // ------------------------------------------------------------------ mensajes
    public String prefix() { return Utils.color(getConfig().getString("configuracion.prefix", "")); }

    public String msg(String path) {
        String m = getConfig().getString("mensajes." + path, "");
        return Utils.color(getConfig().getString("configuracion.prefix", "") + m.replace("%cmd%", comandoPrincipal));
    }

    /** Registro del staff: staff-logs.yml + webhook de Discord. */
    public void logAction(String staff, String target, String action, String killer) {
        String time = Utils.now();
        String staffName = staff != null ? staff : "AUTOMÁTICO";
        YamlConfiguration y = YamlConfiguration.loadConfiguration(logFile);
        List<String> logs = y.getStringList("logs");
        logs.add("[" + time + "] " + staffName + " -> " + action + " a " + target);
        while (logs.size() > 2000) logs.remove(0);
        y.set("logs", logs);
        com.rbfernan.util.DiskWriter.writeAsync(logFile, y.saveToString());
        String url = getConfig().getString("discord-webhook", "");
        if (url == null || !url.startsWith("http")) return;
        String json = getConfig().getString("discord-formato-webhook", "{}")
                .replace("%staff%", Utils.json(staffName)).replace("%player%", Utils.json(target))
                .replace("%action%", Utils.json(action)).replace("%killer%", Utils.json(killer != null ? killer : "Desconocido"))
                .replace("%date%", Utils.json(time));
        Webhook.send(this, url, json);
    }

    public String getComandoPrincipal() { return comandoPrincipal; }
    public DeathStore deaths() { return deaths; }
    public Menus menus() { return menus; }
    public MenuListener menuListener() { return menuListener; }
    public Scanner scanner() { return scanner; }
    public PaidItems paid() { return paid; }
}
