# RbFernan Rollback PRO 3.0 — Cómo funciona

Compatible con **1.20.x, 1.21.x y 1.21.8** (Spigot, Paper o Purpur).

---

## 1. Qué hace el plugin

Cada vez que un jugador muere, el plugin guarda **todo su inventario y su enderchest** en ese momento.
El staff puede ver esas muertes en un panel y devolverle las cosas (rollback).

Además tiene un **scanner anti-hacker**: cuando banean a alguien que mató jugadores, revisa si tiene los
**items de paga** de sus víctimas o a quién se los pasó. Así se cumple la regla:
*los items de paga solo se devuelven si el hacker los tiene*.

---

## 2. Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/rbfernan pendiente` | Todos | Reclamar un rollback que el staff aprobó |
| `/rbfernan <jugador>` | Staff | Abre el panel del jugador |
| `/rbfernan <jugador> deaths` | Staff | Menú con todos los que mataron a ese jugador |
| `/rbfernan <jugador> deaths <asesino>` | Staff | Solo las muertes que causó ese asesino |
| `/rbfernan scanner` | Staff | Todos los reportes del scanner |
| `/rbfernan scanner <jugador>` | Staff | Reportes donde aparece ese jugador |
| `/rbfernan scan <jugador>` | Staff | Escanear ya, sin esperar el ban |
| `/rbfernan reload` | Staff | Recargar el config |
| `/rbfernan clear confirmar` | Staff | Borrar TODAS las muertes guardadas |

Permisos:
- `rbfernan.admin`: panel, rollbacks y scanner (por defecto, los OP).
- `rbfernan.staff`: sus baneos activan el scanner.
- `rbfernan.use`: usar `/rbfernan pendiente` (todos).

---

## 3. El panel (`/rbfernan Fernando`)

**Arriba:** la cabeza del jugador con sus datos:
- si está conectado,
- cuántas muertes tiene guardadas y cuántas fueron por jugadores,
- items de paga perdidos,
- pendientes, aprobados y reclamados,
- su última muerte.

**Botones:**
- **Inventario** (cofre): historial de muertes para devolver el inventario.
- **EnderChest**: lo mismo, para el enderchest.
- **PlayerVaults**: abre sus baúles de AxVaults.
- **Filtrar por asesino** (espada): eliges quién lo mató y ves solo esas muertes.
- **Scanner** (catalejo): reportes donde aparece este jugador.

### Historial
- Cada muerte se muestra con la **cabeza de quien lo mató**. Si murió por otra cosa, sale un ícono según la causa:
  - pluma = caída,
  - balde de lava = lava,
  - TNT = explosión,
  - perla = vacío,
  - etc.
- Colores del estado: 🔴 **Pendiente**, 🟡 **Aprobado** (además brilla) y 🟢 **Reclamado**.
- Si el scanner revisó esa muerte, sale lo que encontró ("TIENE TODO", "NO LOS TIENE"...).

### Ver una muerte (al hacer click)
Arriba ves los items que tenía. **Puedes sacar o poner items** antes de entregar.

| Botón | Qué hace |
|---|---|
| Ir a la muerte | Te teletransporta al lugar donde murió |
| Entregar al inventario | Le da todo directo (tiene que estar conectado) |
| Entregar al enderchest | Lo mete en su enderchest |
| Generar shulker | Te da shulkers con sus items |
| **Entregar SIN items de paga** | Le da todo **menos** los de paga |
| Aprobar pendiente | El jugador lo reclama después con `/rbfernan pendiente` |
| Revocar aprobación | Vuelve a "pendiente" (si aún no lo reclamó) |

> La aprobación se guarda en el momento. Si el servidor se reinicia, el jugador igual puede reclamarla.

---

## 4. Items de paga

Se detectan **por tipo de item**, en `config.yml` → `items-de-paga.materiales`:

```yaml
items-de-paga:
  materiales:
    - BLAZE_ROD          # jaulas
    - DIAMOND_SWORD      # espadas
    - NETHERITE_SWORD
    - DIAMOND_SHOVEL     # palas
    - NETHERITE_SHOVEL
    - DIAMOND_HOE        # azadas
    - NETHERITE_HOE
    - PLAYER_HEAD        # kits de paga (cabezas)
```

Para agregar otro, pon su nombre en inglés y en mayúsculas (ej. `ELYTRA`) y usa `/rbfernan reload`.

---

## 5. Scanner anti-hacker

### Paso 1 — Vigilar (muy liviano)
Solo cuando alguien **mata a otro jugador**, durante 2 horas (`minutos-vigilancia`) se anota en texto:
- qué items de la víctima recogió,
- qué tiró al suelo y **quién lo recogió**,
- dónde guardó cosas (cofres, barriles, shulkers, enderchest) y **quién las sacó**,
- qué comandos usó (por ejemplo `/trade`, `/pay`, `/ah`...).

A los jugadores que no matan a nadie **no se les vigila**.

### Paso 2 — Cuando lo banean
Cuando un staff usa `/ipban`, `/ban`, `/tempban`... (o la consola), el plugin:
1. Revisa su **inventario y enderchest** en ese momento. Si ya estaba desconectado, usa lo que tenía al salir.
2. Compara con los **items de paga que perdieron sus víctimas**.
3. Da un resultado por cada víctima:
   - 🟢 **TIENE TODO**: se le pueden devolver los items de paga a la víctima.
   - 🟡 **TIENE ALGUNOS**: solo devolver los que tiene (✔).
   - 🔴 **NO LOS TIENE**: revisa a quién se los pasó.

### Paso 3 — Dónde se ve
- **En el chat**, a todo el staff conectado.
- **En el panel**, con `/rbfernan scanner`. Al abrir un reporte ves:
  - el hacker,
  - sus víctimas (click para abrir esa muerte y hacer el rollback),
  - a quién le pasó items (click para abrir su panel),
  - el libro con todos sus movimientos.
- **En Discord**, por el webhook:
  - un mensaje con el resumen (verde, amarillo o rojo),
  - **el reporte completo como archivo `.txt`** adjunto: víctimas, items, a quién se los pasó, lo que tenía y todos los movimientos.

> El scanner solo sabe lo que pasó **en este servidor** y **después de instalar esta versión**.

---

## 6. Config importante

| Opción | Para qué |
|---|---|
| `license-key` | Tu licencia |
| `discord-webhook` | Webhook para el registro del staff y el scanner |
| `scanner.discord-webhook` | (Opcional) otro webhook solo para el scanner |
| `scanner.comandos-de-ban` | Qué comandos cuentan como ban |
| `scanner.minutos-vigilancia` | Cuánto tiempo se vigila a quien mató a alguien |
| `scanner.discord-adjuntar-reporte` | Mandar el reporte completo como `.txt` |
| `items-de-paga.materiales` | Qué items son de paga |
| `comando-abrir-pv` | Comando para abrir los PlayerVaults |
| `shulker-settings` | Material, nombre y lore de las shulkers de rollback |

---

## 7. Archivos (en `plugins/RbFernan/`)

| Archivo | Qué guarda |
|---|---|
| `deaths/Jugador_fecha.yml` | Cada muerte, con todos sus items |
| `deaths/index/jugador.yml` | Resumen rápido para que el panel abra sin lag |
| `scans/Hacker_fecha.yml` | Reportes del scanner |
| `scans/vigilados.yml` | Jugadores vigilados (por si el servidor se reinicia) |
| `staff-logs.yml` | Todo lo que hizo el staff |
