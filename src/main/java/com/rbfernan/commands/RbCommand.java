package com.rbfernan.commands;

import com.rbfernan.Main;
import com.rbfernan.data.DeathRecord;
import com.rbfernan.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RbCommand extends Command {
    private final Main plugin;

    public RbCommand(String name, Main plugin) {
        super(name);
        this.plugin = plugin;
        setDescription("RbFernan Rollback PRO");
    }

    private void help(CommandSender s, boolean admin) {
        String c = plugin.getComandoPrincipal();
        s.sendMessage(plugin.msg("formato-ayuda-titulo"));
        s.sendMessage(plugin.msg("formato-ayuda-pendiente"));
        if (!admin) return;
        s.sendMessage(plugin.msg("formato-ayuda-admin-gestionar"));
        s.sendMessage(Utils.color(" &7• &f/" + c + " <jugador> deaths [asesino] &8- &7Muertes causadas por un jugador"));
        s.sendMessage(Utils.color(" &7• &f/" + c + " scanner [jugador] &8- &7Reportes del scanner anti-hacker"));
        s.sendMessage(Utils.color(" &7• &f/" + c + " scan <jugador> &8- &7Escanear ahora (si mato a alguien hace poco)"));
        s.sendMessage(plugin.msg("formato-ayuda-admin-reload"));
        s.sendMessage(plugin.msg("formato-ayuda-admin-clear"));
    }

    @Override
    public boolean execute(CommandSender s, String label, String[] args) {
        boolean admin = s.hasPermission("rbfernan.admin");
        if (args.length == 0) { help(s, admin); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pendiente")) {
            if (!(s instanceof Player p)) return true;
            if (plugin.menuListener().claim(p)) p.sendMessage(plugin.msg("reclamado-exito"));
            else p.sendMessage(plugin.msg("no-aprobado"));
            return true;
        }
        if (!admin) { s.sendMessage(plugin.msg("sin-permiso")); return true; }
        switch (sub) {
            case "reload" -> { plugin.reloadAll(); s.sendMessage(plugin.msg("config-recargada")); return true; }
            case "clear" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
                    s.sendMessage(Utils.color(plugin.prefix() + "&c¿Seguro? Esto borra TODAS las muertes guardadas. Usa &f/" + plugin.getComandoPrincipal() + " clear confirmar"));
                    return true;
                }
                plugin.deaths().clearAll();
                s.sendMessage(Utils.color(plugin.prefix() + "&a¡Se eliminaron todos los registros de muertes!"));
                plugin.logAction(s.getName(), "TODOS", "BORRÓ TODO EL HISTORIAL", "N/A");
                return true;
            }
            case "tp" -> {   // lo usan los clicks del registro del scanner
                if (!(s instanceof Player p) || args.length < 5) return true;
                org.bukkit.World w = Bukkit.getWorld(args[1]);
                if (w == null) { p.sendMessage(Utils.color(plugin.prefix() + "&cEse mundo no existe o no esta cargado.")); return true; }
                try {
                    p.teleport(new org.bukkit.Location(w, Integer.parseInt(args[2]) + 0.5, Integer.parseInt(args[3]), Integer.parseInt(args[4]) + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
                    p.sendMessage(Utils.color(plugin.prefix() + "&aTeletransportado a &f" + args[1] + " " + args[2] + " " + args[3] + " " + args[4]));
                } catch (NumberFormatException ex) {
                    p.sendMessage(Utils.color(plugin.prefix() + "&cCoordenadas invalidas."));
                }
                return true;
            }
            case "scanner", "scans" -> {
                if (!(s instanceof Player p)) return true;
                plugin.menus().scans(p, args.length > 1 ? args[1] : "", 1);
                return true;
            }
            case "scan", "escanear" -> {
                if (args.length < 2) { s.sendMessage(Utils.color(plugin.prefix() + "&cUso: /" + plugin.getComandoPrincipal() + " scan <jugador>")); return true; }
                if (!plugin.scanner().manualScan(args[1], s.getName()))
                    s.sendMessage(Utils.color(plugin.prefix() + "&e" + args[1] + " &7no mato a nadie recientemente: no hay movimientos que revisar."));
                return true;
            }
            default -> { }
        }
        if (!(s instanceof Player p)) return true;
        String target = args[0];
        if (args.length >= 2 && (args[1].equalsIgnoreCase("deaths") || args[1].equalsIgnoreCase("muertes"))) {
            if (args.length >= 3) {
                if (plugin.deaths().records(target, args[2]).isEmpty()) {
                    p.sendMessage(Utils.color(plugin.prefix() + "&e" + args[2] + " &7no ha matado a &f" + target + " &7(o no hay registros)."));
                    return true;
                }
                plugin.menus().history(p, target, 1, "INV", args[2]);
            } else plugin.menus().killers(p, target);
            return true;
        }
        plugin.menus().profile(p, target);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender s, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("rbfernan.admin");
        if (args.length == 1) {
            out.add("pendiente");
            if (admin) {
                out.addAll(List.of("scanner", "scan", "reload", "clear"));
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (admin && args.length == 2) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("scanner") || a.equals("scan")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (!List.of("pendiente", "reload", "clear").contains(a)) out.add("deaths");
        } else if (admin && args.length == 3 && args[1].equalsIgnoreCase("deaths")) {
            out.addAll(plugin.deaths().killers(args[0]).keySet());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    @SuppressWarnings("unused")
    private static int count(List<DeathRecord> l) { return l.size(); }
}
