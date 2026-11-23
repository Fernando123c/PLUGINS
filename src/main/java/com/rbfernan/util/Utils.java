package com.rbfernan.util;

import org.bukkit.ChatColor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {
    private Utils() {}

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    public static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final DateTimeFormatter NICE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** &a, &l ... y &#RRGGBB */
    public static String color(String message) {
        if (message == null) return "";
        Matcher m = HEX.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String strip(String s) {
        return s == null ? "" : ChatColor.stripColor(color(s));
    }

    public static String now() { return NICE_DATE.format(LocalDateTime.now()); }
    public static String nowFile() { return FILE_DATE.format(LocalDateTime.now()); }

    /** "2026-11-15_22-10-05" -> "2026-11-15 22:10:05" */
    public static String niceDate(String fileDate) {
        if (fileDate == null || fileDate.isEmpty()) return "?";
        if (!fileDate.contains("_")) return fileDate;
        String[] p = fileDate.split("_", 2);
        return p[0] + " " + p[1].replace("-", ":");
    }

    /** Escapa texto para meterlo dentro de un JSON. */
    public static String json(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append(' ');
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.toString();
    }

    public static String pretty(String enumName) {
        if (enumName == null) return "?";
        String s = enumName.toLowerCase().replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            b.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return b.toString();
    }
}
