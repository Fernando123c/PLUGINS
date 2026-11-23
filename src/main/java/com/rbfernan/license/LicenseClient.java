package com.rbfernan.license;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Verifica la licencia del plugin contra el panel (Supabase).
 * El plugin manda: licencia, nombre del servidor, puerto, version de Minecraft, version del plugin
 * y un identificador propio del servidor (server-id.txt). La IP REAL la detecta el panel desde la conexion,
 * asi que aunque cambien el nombre en el config, el staff ve desde donde se conecta de verdad.
 */
public final class LicenseClient {

    public static final class Result {
        public final boolean ok;        // la licencia existe y esta activa
        public final boolean approved;  // este servidor esta aprobado para usarla
        public final String status;     // pending / approved / rejected / invalid / error
        public final String message;    // texto para la consola
        public Result(boolean ok, boolean approved, String status, String message) {
            this.ok = ok; this.approved = approved; this.status = status; this.message = message;
        }
    }

    private LicenseClient() {}

    // Conexion al panel: FIJA dentro del plugin. No se lee del config para que nadie
    // pueda redirigirla a su propia base de datos y saltarse la licencia.
    private static final String PANEL_URL = "https://qgvoarbhgfigjtazfssb.supabase.co";
    private static final String PANEL_KEY = "sb_publishable_85hHLQlg8cYWS3lJo1TT2A_EbEUuHkX";

    public static Result check(Plugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        String base = PANEL_URL;
        String anon = PANEL_KEY;
        String key = cfg.getString("licencia.license-key", cfg.getString("license-key", ""));
        String serverName = cfg.getString("licencia.nombre-servidor", plugin.getServer().getName());

        if (base == null || base.isEmpty() || anon == null || anon.isEmpty())
            return new Result(false, false, "error", "El plugin no tiene configurado el panel de licencias.");
        if (key == null || key.trim().isEmpty() || key.contains("AQUI"))
            return new Result(false, false, "invalid", "Falta poner tu licencia en el config.yml (licencia.license-key).");

        int port = plugin.getServer().getPort();
        String mc = plugin.getServer().getBukkitVersion();
        String version = plugin.getDescription().getVersion();
        String hwid = serverId(plugin);

        String url = base;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        url += "/rest/v1/rpc/plugin_check";

        String body = "{"
                + "\"p_key\":" + jstr(key.trim())
                + ",\"p_name\":" + jstr(serverName)
                + ",\"p_port\":" + port
                + ",\"p_mc\":" + jstr(mc)
                + ",\"p_version\":" + jstr(version)
                + ",\"p_hwid\":" + jstr(hwid)
                + "}";
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("apikey", anon);
            con.setRequestProperty("Authorization", "Bearer " + anon);
            con.setRequestProperty("User-Agent", "RbFernan-Plugin");
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = con.getResponseCode();
            InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
            String resp = read(is);
            con.disconnect();
            if (code >= 400)
                return new Result(false, false, "error", "El panel respondio " + code + ". Revisa licencia.url y la clave publica.");
            boolean ok = jbool(resp, "ok");
            boolean approved = jbool(resp, "approved");
            String status = jstr(resp, "status", ok ? (approved ? "approved" : "pending") : "invalid");
            String msg = jstr(resp, "msg", "");
            if (msg.isEmpty()) msg = ok ? (approved ? "Licencia valida y servidor aprobado."
                    : "Servidor PENDIENTE de aprobacion en el panel.") : "Licencia invalida.";
            return new Result(ok, approved, status, msg);
        } catch (Exception e) {
            return new Result(false, false, "error", "No se pudo conectar con el panel de licencias: " + e.getMessage());
        }
    }

    /** Identificador estable de este servidor, guardado en plugins/<plugin>/server-id.txt */
    private static String serverId(Plugin plugin) {
        try {
            File f = new File(plugin.getDataFolder(), "server-id.txt");
            if (f.exists()) {
                String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return s;
            }
            plugin.getDataFolder().mkdirs();
            String id = UUID.randomUUID().toString();
            Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------ utilidades minimas
    private static String read(InputStream is) {
        if (is == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        } catch (Exception ignored) { }
        return b.toString();
    }

    private static String jstr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n', '\r', '\t' -> b.append(' ');
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }

    private static boolean jbool(String body, String key) {
        int p = valuePos(body, key);
        return p >= 0 && body.startsWith("true", p);
    }

    private static String jstr(String body, String key, String def) {
        int p = valuePos(body, key);
        if (p < 0 || body.charAt(p) != '"') return def;
        StringBuilder out = new StringBuilder();
        for (int i = p + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) { out.append(body.charAt(++i)); continue; }
            if (c == '"') break;
            out.append(c);
        }
        return out.toString();
    }

    private static int valuePos(String body, String key) {
        if (body == null) return -1;
        String k = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int p = body.indexOf(k, from);
            if (p < 0) return -1;
            int q = p + k.length();
            while (q < body.length() && body.charAt(q) == ' ') q++;
            // Solo cuenta si es una CLAVE (le sigue ":"), no un valor como "status":"approved"
            if (q < body.length() && body.charAt(q) == ':') {
                q++;
                while (q < body.length() && body.charAt(q) == ' ') q++;
                return q;
            }
            from = p + k.length();
        }
    }
}
