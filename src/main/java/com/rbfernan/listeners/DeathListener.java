package com.rbfernan.listeners;

import com.rbfernan.Main;
import com.rbfernan.data.DeathRecord;
import com.rbfernan.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathListener implements Listener {
    private final Main plugin;

    // "Combat tag": recuerda quien fue el ultimo jugador que daño a cada jugador y cuando.
    // Sirve para saber el asesino cuando Bukkit no lo da (flechas, caida tras un golpe, fuego/lava/pociones
    // puestas por otro, o cuando la victima muere un instante despues del ultimo golpe).
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();

    public DeathListener(Main plugin) { this.plugin = plugin; }

    private long combatWindowMs() {
        return Math.max(1L, plugin.getConfig().getLong("scanner.combate-segundos", 15)) * 1000L;
    }

    /** Resuelve el jugador atacante de un daño: golpe directo o proyectil disparado por un jugador. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker == victim) return;
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttackAt.put(victim.getUniqueId(), System.currentTimeMillis());
        if (lastAttacker.size() > 5000) { lastAttacker.clear(); lastAttackAt.clear(); }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.scanner().onJoin(p);
        boolean pending = false;
        for (DeathRecord r : plugin.deaths().records(p.getName())) if ("APPROVED".equals(r.status)) { pending = true; break; }
        if (!pending) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            String cmd = plugin.getComandoPrincipal();
            p.sendTitle(Utils.color(plugin.getConfig().getString("mensajes.login-titulo", "&a&l¡ROLLBACK PENDIENTE!")),
                    Utils.color(plugin.getConfig().getString("mensajes.login-subtitulo", "&fUsa &e/%cmd% pendiente").replace("%cmd%", cmd)), 10, 70, 20);
            p.sendMessage(plugin.msg("login-mensaje"));
            try { p.playSound(p.getLocation(), "entity.player.levelup", 0.7f, 1.4f); } catch (Throwable ignored) { }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastAttacker.remove(e.getPlayer().getUniqueId());
        lastAttackAt.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Player killer = p.getKiller();

        // Si Bukkit no da asesino, usar el combat tag (si el golpe fue hace poco y el atacante sigue conectado).
        if (killer == null) {
            UUID att = lastAttacker.get(p.getUniqueId());
            Long when = lastAttackAt.get(p.getUniqueId());
            if (att != null && when != null && System.currentTimeMillis() - when <= combatWindowMs()) {
                Player a = Bukkit.getPlayer(att);
                if (a != null && a != p) killer = a;
            }
        }
        lastAttacker.remove(p.getUniqueId());
        lastAttackAt.remove(p.getUniqueId());

        String cause;
        if (killer != null) cause = killer.getName();
        else if (p.getLastDamageCause() != null) cause = p.getLastDamageCause().getCause().name().replace("_", " ").toLowerCase();
        else cause = "Natural/Entorno";
        DeathRecord r = plugin.deaths().saveDeath(p, cause, killer);
        if (plugin.scanner().watching(p)) plugin.scanner().onWatchedDeath(p);
        if (killer != null && killer != p) plugin.scanner().onKill(killer, p, r);
    }
}
