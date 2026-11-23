package com.rbfernan.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Envia JSON a un webhook de Discord sin trabar el servidor (hilo aparte). */
public final class Webhook {
    private Webhook() {}

    /** Envia el mensaje con un archivo de texto adjunto (el reporte completo). */
    public static void sendWithFile(Plugin plugin, String url, String json, String fileName, String fileText) {
        if (url == null || !url.startsWith("http") || json == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String boundary = "----RbFernan" + System.nanoTime();
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                con.setRequestProperty("User-Agent", "RbFernan");
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    String head = "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                            + "Content-Type: application/json\r\n\r\n" + json + "\r\n"
                            + "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: text/plain; charset=utf-8\r\n\r\n";
                    os.write(head.getBytes(StandardCharsets.UTF_8));
                    os.write(fileText.getBytes(StandardCharsets.UTF_8));
                    os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
                if (code >= 400) plugin.getLogger().warning("Webhook de Discord respondio " + code + " al enviar el reporte.");
                con.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo enviar el reporte al webhook: " + e.getMessage());
            }
        });
    }

    public static void send(Plugin plugin, String url, String json) {
        if (url == null || !url.startsWith("http") || json == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("User-Agent", "RbFernan");
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
                if (code >= 400) plugin.getLogger().warning("Webhook de Discord respondio " + code + " (revisa el formato o la URL).");
                con.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo enviar el webhook: " + e.getMessage());
            }
        });
    }
}
