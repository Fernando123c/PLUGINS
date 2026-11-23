package com.rbfernan.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Escribe archivos sin trabar el servidor, pero sin perder nada:
 * - cada archivo se escribe en otro hilo,
 * - si el servidor se apaga/reinicia, lo que falte se escribe al instante (flush en onDisable),
 * - se escribe primero a un .tmp y luego se reemplaza (si se corta a la mitad no se corrompe).
 */
public final class DiskWriter {
    private static final Map<File, String> pending = new ConcurrentHashMap<>();
    private static Plugin plugin;

    private DiskWriter() {}

    public static void init(Plugin p) { plugin = p; }

    public static void writeAsync(File f, String text) {
        pending.put(f, text);
        if (plugin == null || !plugin.isEnabled()) { flush(); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String t = pending.remove(f);
            if (t != null) write(f, t);
        });
    }

    public static void writeNow(File f, String text) {
        pending.remove(f);
        write(f, text);
    }

    /** Escribe todo lo pendiente ya mismo (se llama al apagar el plugin). */
    public static void flush() {
        for (File f : pending.keySet()) {
            String t = pending.remove(f);
            if (t != null) write(f, t);
        }
    }

    private static synchronized void write(File f, String text) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (plugin != null) plugin.getLogger().severe("No se pudo guardar " + f.getName() + ": " + e.getMessage());
        }
    }
}
