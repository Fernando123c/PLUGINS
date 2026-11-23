# RbFernan Rollback PRO

Plugin de Minecraft (Spigot/Paper/Purpur 1.20.x – 1.21.x) con rollback de muertes, scanner anti-hacker,
**sistema de licencias conectado a un panel web** y ofuscación con ProGuard.

## Qué incluye este repositorio

| Carpeta | Qué es |
|---|---|
| `src/` | Código del plugin |
| `docs/` | Panel web (licencias y servidores) — se publica con GitHub Pages |
| `supabase/schema.sql` | Base de datos del panel |
| `.github/workflows/build.yml` | Compila el `.jar` solo y lo publica en **Releases** |

## Novedades de la versión 3.1

- **Arreglo del bug de kills**: si Minecraft no daba el asesino (flechas, caída tras un golpe, fuego/lava/pociones
  de otro, o muerte un instante después), la kill no se contaba y al banear decía "no mató a nadie". Ahora se usa el
  último jugador que lo golpeó (combat tag, `scanner.combate-segundos`).
- **Licencias por panel web**: el plugin solo funciona si su servidor está **aprobado** en el panel.
- **Detección de IP real** del servidor (aunque cambien el nombre en el config).
- **Aprobar / rechazar / borrar** cada servidor desde la web. Si borras la licencia, el plugin se apaga solo.
- **ProGuard**: el `.jar` sale ofuscado (ilegible al descompilar).

## Cómo funciona la licencia

1. El dueño del servidor pone su licencia en `config.yml` → `licencia.license-key`.
2. Al arrancar, el plugin manda al panel: licencia, nombre e **IP real**, puerto y versión.
3. El panel decide: si está aprobado, carga; si no, se desactiva y lo dice en la consola.
4. Cada `licencia.minutos-revision` vuelve a revisar: si lo rechazas o borras la licencia, se apaga solo.

## Configuración (una vez)

1. **Supabase**: crea un proyecto, ejecuta `supabase/schema.sql` en el SQL Editor, desactiva el registro de usuarios
   nuevos y crea tu usuario de staff. Copia la **Project URL** y la clave **anon/publishable**.
2. Pega esos dos datos en `docs/config.js` (panel) y en `src/main/resources/config.yml` → `licencia.url` y
   `licencia.clave-publica`.
3. **GitHub Pages**: Settings → Pages → rama `main`, carpeta `/docs`.
4. Sube los cambios: GitHub Actions compila el `.jar` y lo deja en **Releases**.

## Compilar a mano (opcional)

Necesitas Java 17+ y Maven: `mvn clean package` → el `.jar` queda en `target/`.
