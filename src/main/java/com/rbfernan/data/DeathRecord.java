package com.rbfernan.data;

import java.util.UUID;

/** Datos livianos de una muerte (sin los items). Se guardan en el indice de cada jugador. */
public class DeathRecord {
    public String file;          // nombre del archivo en deaths/
    public String player;
    public UUID playerUuid;
    public String date;          // yyyy-MM-dd_HH-mm-ss
    public String cause;         // causa (o nombre del asesino)
    public String killer;        // nombre del asesino si fue un jugador, si no ""
    public UUID killerUuid;
    public String status;        // PENDING / APPROVED / CLAIMED
    public int items, ecItems, paid;
    public String world;
    public double x, y, z;
    public String scan = "";     // resultado del scanner (si el asesino fue baneado)

    public boolean byPlayer() { return killer != null && !killer.isEmpty(); }
}
