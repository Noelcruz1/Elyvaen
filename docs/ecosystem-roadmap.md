# Ecosistema Elyvaen — referencias, arquitectura y roadmap

Fecha: 2026-11-21.

Objetivo: convertir el DAP en un ecosistema completo alrededor de la música
local, Deezer y el servidor de casa, sin sacrificar reproducción bit-perfect,
UAC2 ni la biblioteca existente.

## Referencias evaluadas

| Proyecto | Plataforma | Licencia | Papel para Elyvaen |
|---|---|---|---|
| [ArchiveTune](https://github.com/rukamori/ArchiveTune) | Android · Kotlin/Compose | GPL-3.0 | Referencia de UI móvil: bottom nav, tarjetas, estadísticas, letras, temas, player. No es fuente Deezer (es YouTube Music + local). |
| [Sonora](https://github.com/sonorahq/sonora) | Desktop · Rust/GPUI | GPL-3.0 | **Mejor referencia de UI de escritorio** (sidebar, cola/letras, barra now-playing, tablas densas) y **referencia técnica de Deezer**: ARL, gateway, descifrado BF_CBC_STRIPE, playlists, descargas. |
| [Redomi](https://github.com/acszo/Redomi) | Android · Compose | GPL-3.0 | Cliente de song.link/Odesli. Sirve para resolver enlaces Deezer/Spotify/YouTube y abrir o descargar la canción equivalente. No descarga audio. |
| [Metadator](https://github.com/BobbyESP/Metadator) | Android · Compose | **AGPL-3.0** | Editor de etiquetas, portadas y letras con búsqueda en MusicBrainz/Deezer y LRCLIB. Referencia para “salud de biblioteca” y enriquecimiento. **No copiar código**: AGPL obligaría a relicenciar Elyvaen; reimplementar las ideas. |
| [Elixium](https://github.com/jaylex32/Elixium) | Node/TS · CLI/Web | MIT | Descargador Deezer/Qobuz con watchlists y Web UI. Buen backend para el puente del NAS. |
| [streamrip](https://github.com/nathom/streamrip) | Python CLI | GPL-3.0 | Descargador Deezer/Qobuz/Tidal/SoundCloud, bases de datos de descargas y automatización. Alternativa al puente. |
| [Music Presence](https://github.com/ungive/discord-music-presence) | Desktop C++ | Source-available, sin licencia reutilizable | Referencia de Rich Presence. **No se puede ejecutar en Android** y no se puede reutilizar su código; sirve para el protocolo/idea y para un companion en PC. |

## Lo importante de cada una

### Sonora (UI + Deezer)

Sonora demuestra que una app nativa puede combinar servicios y biblioteca local
con una UI de escritorio muy limpia:

- sidebar con Home/Search/Library/Local/History/Settings;
- contenido central con cabecera de álbum/artista y tabla de canciones;
- panel derecho de **Queue** y **Lyrics**;
- barra inferior de now-playing con transporte, seek, volumen y cola;
- estética oscura, tipografía grande y acentos por carátula;
- **proveedor Deezer real**: login con cookie `arl`, catálogo por API pública,
  sesión/gateway web, descifrado de audio y descargas para reproducción offline.

Eso último es la referencia técnica clave. En Rust usa:

- `crates/music/src/deezer/auth.rs`: ARL;
- `crates/music/src/deezer/client.rs`: API/gateway;
- `crates/music/src/deezer/decrypt.rs`: BF_CBC_STRIPE;
- `crates/music/src/deezer/playback.rs`: stream descifrado;
- `crates/state/src/downloads.rs`: cola de descargas y progreso.

La licencia GPL-3.0 es compatible con Elyvaen (GPL-3.0), así que se puede
portar el enfoque conservando atribución. No se copiarán iconos ni branding.

### ArchiveTune (UI móvil)

Aporta ideas directamente aplicables a la versión teléfono:

- Home con “Quick picks”, “Speed dial” y tarjetas grandes;
- player con carátula, fondo degradado, letras vivas y acciones;
- biblioteca con búsqueda, filtros y listas;
- pantalla de **estadísticas** (tiempo escuchado, artistas/álbumes top,
  rachas, descubrimientos);
- temas y personalización;
- scrobbling Last.fm/ListenBrainz y Discord Rich Presence (en su caso,
  mediante su propia integración).

No es una fuente Deezer: es YouTube Music. Se usa como referencia visual y de
producto, no como backend.

### Redomi, Metadator, Elixium, streamrip

- **Redomi**: resolver un enlace compartido de Deezer/Spotify/Tidal/YouTube a
  la canción equivalente. En Elyvaen puede servir para “pegar enlace Deezer”,
  buscar la versión local o añadirla a la cola de descarga.
- **Metadator**: pantalla de salud de biblioteca (sin portada, sin tags, sin
  letra), búsqueda Deezer/MusicBrainz, edición por lotes y letras LRCLIB.
- **Elixium/streamrip**: no son librerías Android. Se ejecutan en el NAS o en
  Termux. La opción limpia es un **puente Elyvaen** en Docker que descargue,
  etiquete y deje los archivos en una carpeta compartida.

### Discord

`discord-music-presence` lee la sesión multimedia del sistema (MPRIS/SMTC) y
publica Rich Presence mediante el IPC local de Discord. En Android no existe
ese IPC y Discord Android no acepta RPC de terceros. Opciones reales:

1. **Webhook de Discord** desde la app o el puente: embeds de “Ahora suena”
   con portada, título, artista y progreso. Fácil, pero no es presencia de
   perfil.
2. **Companion en PC**: un pequeño servicio en Windows/Linux recibe el estado
   por WebSocket/LAN y publica Rich Presence con el cliente de Discord abierto.
   Es la única forma de tener RPC real en el perfil.
3. **Bot/voice channel status**: un bot puede poner el estado en un canal de
   voz, no en el perfil del usuario. Menos útil.

Recomendación: empezar por **webhook** (sin dependencias) y añadir el
companion de PC después. Last.fm/ListenBrainz ya existen en Booming y se
mantienen.

## Arquitectura propuesta

```
┌────────────────────────────────────────────────────┐
│ Elyvaen DAP (Android · Kotlin/Compose/Media3)       │
│                                                    │
│  Fuente local ──┐                                  │
│  Fuente Deezer ─┼── cola unificada ── Media3/UAC2  │
│  Descargas ─────┘                                  │
│                                                    │
│  Home · Search · Library · Album/Artist · Queue     │
│  Downloads · Stats · Metadata · Settings            │
└───────────────────────┬────────────────────────────┘
                        │ LAN / WebDAV / HTTP (opcional)
┌───────────────────────▼────────────────────────────┐
│ Elyvaen Bridge (NAS · Docker)                      │
│                                                    │
│  streamrip / Elixium · ARL Deezer                  │
│  Watchlists · descargas · etiquetas · LRCLIB       │
│  Sincronización de archivos · cola remota          │
│  Discord RPC / webhook · Last.fm / ListenBrainz    │
└────────────────────────────────────────────────────┘
```

La app debe funcionar **sin el puente** para música local y catálogo Deezer.
El puente es opcional para descargas masivas, watchlists, metadatos y presencia.

## Deezer: fases técnicas

### Fase D1 — Catálogo y sesión

- Login con ARL (pegado o capturado en WebView).
- Perfil, playlists, favoritos, búsqueda, álbum/artista.
- Portada y metadatos vía API pública.
- Pantallas Compose de Deezer y selector de fuente.
- Sin audio todavía.

### Fase D2 — Reproducción

- Resolver stream y clave con el gateway.
- Implementar descifrado BF_CBC_STRIPE en Kotlin.
- `DataSource` de Media3 que descifra al leer, o caché local + reproducción.
- Calidad: 128/320/FLAC; probar con la cuenta del usuario.
- Manejo de errores, expiración de ARL, rate limits.

### Fase D3 — Descargas locales

- Cola de descargas con WorkManager.
- Archivos descifrados a `Music/Elyvaen/Deezer/…`.
- Etiquetas, portada y letras con Deezer/MusicBrainz/LRCLIB.
- Escaneo de biblioteca y reproducción offline.
- Progreso, pausa, reintento, espacio libre.

### Fase D4 — Puente NAS

- Contenedor `elyvaen-bridge` con streamrip/Elixium.
- API REST/WebSocket: búsqueda, descarga, estado, watchlists.
- Carpeta compartida / Syncthing / WebDAV.
- La app muestra la cola remota y descarga los archivos al DAP.

### Nota legal

Deezer no ofrece reproducción de audio completo por API pública. Usar ARL y
descifrar streams es una integración no oficial que puede violar los términos
de Deezer y romperse con cambios del servicio. Se implementará como módulo
**experimental y opcional**, para uso personal con la suscripción del usuario,
sin redistribuir contenido ni claves. Para descargas legales y estables,
Qobuz/Tidal/Bandcamp o la compra directa son mejores fuentes.

## UI v3 propuesta

### Shell responsive

- **Retrato**: bottom nav Elyvaen (Inicio, Buscar, Biblioteca, Descargas,
  Ajustes) + mini-player + panel de cola/letras.
- **Paisaje/tablet**: rail lateral tipo Sonora (Home/Search/Library/Local/
  Downloads/Settings), contenido central, panel derecho de cola/letras y barra
  now-playing inferior. Es el modo “DAP de escritorio”.
- **Source switcher** en Home/Biblioteca: Local · Deezer · Descargas.
- **Búsqueda unificada** con resultados agrupados por fuente.
- **Stats** con tiempo escuchado, top artistas/álbumes, racha y descubrimientos.
- **Metadata health** con filtros (sin portada/tags/letra) y arreglo por lotes.

### Pantallas nuevas

1. **Deezer Home**: playlists editoriales, álbumes, artistas, “para ti”.
2. **Deezer Search**: pistas/álbumes/artistas/playlists; botón Descargar.
3. **Album/Artist/Playlist Deezer**: play, queue, download, favorito.
4. **Downloads**: cola local/remota, progreso, almacenamiento, sync.
5. **Stats**: local + Deezer.
6. **Metadata Health**: Metadator-inspired.
7. **Bridge/Presence**: URL del NAS, token, Discord webhook, Last.fm/
   ListenBrainz, estado de conexión.
8. **Link resolver**: share intent de Deezer/Spotify/YouTube → coincidencia
   local o descarga.

### Lo que se toma de cada referencia

- **Sonora**: shell responsive, panel de cola/letras, barra now-playing,
  tablas densas, conmutador de fuentes.
- **ArchiveTune**: Home de tarjetas, stats, letras, personalización, bottom nav.
- **Metadator**: salud de biblioteca y edición masiva.
- **Redomi**: resolución de enlaces y share sheet.
- **Elixium/streamrip**: backend de descargas del puente.
- **Music Presence**: concepto de presencia; companion/webhook, no código.

## Roadmap sugerido

| Fase | Entregable | Valor |
|---|---|---|
| 0 | UI Elyvaen actual + fixes de rendimiento + biblioteca | Base visual |
| 1 | Shell v3: fuentes, búsqueda, descargas, ajustes de ecosistema | Estructura |
| 2 | Deezer catálogo (ARL, perfil, búsqueda, playlists) | Integración visible |
| 3 | Deezer playback nativo (descifrado + Media3) | Streaming |
| 4 | Deezer downloads + metadata/lyrics | Offline real |
| 5 | Puente NAS (streamrip/Elixium) + sync | Descargas masivas |
| 6 | Discord webhook + companion PC + stats | Social |
| 7 | Subsonic/Navidrome y otras fuentes | Ecosistema |

## Implementado en esta iteración (UI v3 · fase 1)

- **Entrada al ecosistema desde Home**: fila de fuentes `Local · Deezer ·
  Descargas`, todas habilitadas.
- **Pantalla Deezer** (`ui/screen/deezer/`):
  - catálogo público: éxitos, álbumes destacados, playlists editoriales y
    artistas populares;
  - búsqueda con pestañas Pistas/Álbumes/Artistas/Listas;
  - detalle de álbum, artista y playlist;
  - reproducción de **vista previa de 30 s** con ExoPlayer;
  - botón “Abrir en Deezer” para usar la suscripción en la app oficial.
- **Navegación** `nav_deezer` en `graph_main.xml`; la barra inferior se oculta
  en esa pantalla y vuelve al volver a Home.
- **Sesión y biblioteca**: login oficial, perfil/plan, artistas, álbumes y
  pistas favoritas, con alta/baja de favoritos.
- **Descargas**: pista o álbum completo, FLAC/MP3, etiquetas y portada,
  MediaStore, historial persistente y reproducción local desde Descargas.
- **Sin romper nada**: la biblioteca local, UAC2, cola, letras, EQ y ajustes
  siguen igual.

### Pendiente inmediato de esta fase

- Cola unificada local + Deezer y streaming sin descarga completa.
- Mover la cola de descargas a un foreground service/WorkManager.
- Favoritos de playlists (lectura ya disponible; falta el alta/baja fiable).
- Verificar listas privadas de la cuenta y letras sincronizadas.

## Deezer nativo experimental (verificado en dispositivo)

Se añadió la base del proveedor privado, siguiendo el enfoque de Sonora:

- `DeezerAccountStore`: guarda el ARL en preferencias privadas.
- `DeezerPrivateClient`: handshake `deezer.ping` → `getUserData` →
  `song.getData` → `media.get_url`, con cookies `arl`/`sid`, `checkForm`,
  `license_token` y selección de formato (`FLAC_24`, `FLAC`, `MP3_320`,
  `MP3_128`).
- Descifrado BF_CBC_STRIPE en Kotlin (Bloque de 2048 bytes, uno de cada tres,
  Blowfish/CBC, IV fijo, clave derivada del id efectivo).
- `DeezerDownloadManager`: descarga a caché, valida la cabecera FLAC/MP3,
  escribe etiquetas y portada con TagLib y publica en `MediaStore` en
  `Music/Elyvaen/Deezer/<artista>/<álbum>`.
- UI: tarjeta de conexión en la pantalla Deezer con **login oficial en
  WebView** (la contraseña se escribe solo en deezer.com) y opción avanzada
  para pegar una cookie ARL; botón de descarga por pista, descarga de
  álbum/playlist/top de artista y estado por pista (en cola, descargando,
  etiquetando, descargado, error).
- **Mi Deezer**: la barra superior abre la biblioteca de la cuenta con
  pestañas Artistas, Álbumes, Listas y Pistas. Los favoritos se leen con
  `deezer.pageProfile` y `favorite_song.getList`; el corazón de las fichas de
  álbum/artista y de la vista previa añade o quita favoritos y refresca la
  biblioteca.
- La **historial de descargas** se guarda en preferencias privadas: al
  reiniciar, Descargas sigue mostrando lo descargado (y una importación única
  recupera archivos antiguos de `Music/Elyvaen/Deezer`). Tocar una fila
  completada reproduce el archivo local con la cola normal de Elyvaen.
- Las descargas se ejecutan en un `CoroutineScope` de aplicación; falta
  pasarlas a un foreground service/WorkManager para que sobrevivan al cierre
  de la app.
- Es una integración **no oficial**; puede romperse si Deezer cambia el web
  player. El ARL es personal y no se registra en logs.

También se añadió la pantalla **Descargas** (`ui/screen/deezer/DownloadsScreen.kt`):
estado vacío, almacenamiento libre/total, descargas en curso, completadas,
reintento y reproducción local. El chip `Descargas` del Home ya abre esa
pantalla.

Se añadió **Estadísticas** (`StatsScreen.kt`, inspirada en ArchiveTune):
tiempo escuchado, reproducciones, pistas únicas, top canciones, top artistas,
top álbumes e historial reciente, con acceso desde el icono de tendencia del
Home.

El **modo kiosco** ahora usa `startLockTask()` de Android además de ocultar
las barras. El interruptor del dashboard DAP persiste la preferencia y el
sistema muestra su confirmación de fijación la primera vez; mantener subir
volumen sale del kiosco. Sin device owner no se puede evitar esa confirmación
del sistema.

Verificado el 2026-11-21 en el Moto G51 con la cuenta **NoelCruz (HiFi)**:
login oficial, catálogo, búsqueda y fichas de álbum/artista/playlist;
biblioteca de artistas, álbumes y pistas favoritas; alta/baja de favoritos;
descarga real de una pista y de un álbum completo en FLAC 16/44.1 con etiquetas
y portada; aparición en MediaStore (`Music/Elyvaen/Deezer/…`), reproducción
local desde Descargas y persistencia tras reiniciar; fijación real del kiosco
(`mLockTaskModeState=PINNED`). La build release quedó instalada y el
mini-player/90 Hz verificados.

## Biblioteca curada (2026-11-22)

- **Ocultar de la biblioteca** para álbumes, artistas y pistas, tanto en el menú
  individual como en selección múltiple. No borra archivos: guarda el id/nombre
  en preferencias privadas y `SongRepository` los excluye de todas las consultas
  de biblioteca, búsqueda, artistas, géneros y años.
- Panel **Contenido oculto** desde el menú de la biblioteca y desde
  Ajustes → Biblioteca, con **Restaurar** por elemento y **Restaurar todo**.
- **Ocultar todos sus álbumes** en el menú de un artista. Es la vía rápida para
  retirar de golpe una discografía entera; después se restauran desde el panel
  solo los discos que sí se quieran conservar.
- **Silenciar álbum** (y **Silenciar todos sus álbumes** en un artista) es el
  modo suave: el disco sigue visible en Álbumes con la etiqueta «Silenciado»,
  pero sus pistas desaparecen de Canciones, búsqueda de pistas y aleatorios.
  El detalle del álbum sigue abriéndose y se puede reproducir explícitamente.
- **Ocultar de la biblioteca** sigue siendo el modo fuerte: desaparece de todas
  las listas, incluida Álbumes. Ambos modos se gestionan desde el panel
  **Contenido oculto**, que separa «Álbumes silenciados» y contenido oculto.
- Verificado en el Moto G51: ocultar el álbum `:)` de The Japanese House y la
  pista `:)` los quita de Álbumes/Canciones; el archivo sigue en MediaStore y
  vuelve al restaurarlo. También se ocultaron y restauraron los 46 álbumes de
  Mili con **Restaurar todo**.
- MediaStore puede dividir a Mili en varias entradas de artista (por ejemplo
  **Mili** y **Mili & KIHOW**, y a veces **Binary Haze Interactive & Mili**).
  Para limpiar toda la discografía hay que aplicar **Silenciar todos sus
  álbumes** o **Ocultar todos sus álbumes** en cada entrada y luego restaurar
  lo que se quiera.
- Para escucha suelta: tras archivar los álbumes que no interesan, la pestaña
  **Canciones**, favoritos y listas propias quedan como biblioteca curada.

## Extras evaluados (2026-11-22)

| Extra propuesto | Base | Licencia | ¿Integrable? | Esfuerzo | Veredicto |
|---|---|---|---|---|---|
| Resolvedor de enlaces Deezer/Spotify/YouTube | Redomi | GPL-3.0 | Sí, nativo | Medio | **Implementado**: share sheet → Odesli/song.link → coincidencia local o abrir en Deezer. |
| Salud de biblioteca y edición por lotes | Metadator | AGPL-3.0 | Solo reimplementar | Medio-alto | **Muy útil** para las 896 pistas: detectar sin portada/título/artista/álbum/letra, duplicados y tags raros; usar TagLib + MusicBrainz + Deezer + LRCLIB. No copiar código AGPL. |
| Cola/letras/Flow y listas inteligentes | Sonora | GPL-3.0 | Portar conceptos | Medio | Sonora es Rust/GPUI de escritorio; no se embebe. Su lógica Deezer ya está portada. Añadir Flow/mezcla por similitud y panel de cola/letras. |
| Quick picks, temas, letras, sleep timer | ArchiveTune | GPL-3.0 | Ideas/portes pequeños | Bajo | Su backend es YouTube Music, no Deezer. Solo adaptar UI y funciones; no traer su motor. |
| Descargas masivas y watchlists | Elixium / streamrip | MIT / GPL-3.0 | Servicio externo | Alto | No son librerías Android: corren en NAS/Termux. Integrar por REST/SSH o carpeta compartida en una fase de puente. |
| Rich Presence de Discord | Music Presence | Source-available | No reutilizable | Bajo / Alto | Discord Android no acepta RPC de terceros. Webhook desde la app es fácil; presencia real requiere companion en PC. |
| “Sueltas” / Flow local | Propio | GPL-3.0 | Sí | Bajo-medio | **Implementado**: sección en Home y vista completa con singles/pistas de álbumes de 1–2 canciones. |

### Orden recomendado

1. **Resolvedor de enlaces** (Redomi): valor inmediato y bajo riesgo.
2. **Salud de biblioteca** (ideas de Metadator, reimplementadas): limpia los tags desconocidos que ya aparecen en la biblioteca.
3. **“Sueltas”/Flow local**: aprovecha el archivado y el silenciado que acabamos de añadir.
4. **Webhook de Discord** y, más adelante, companion de PC.
5. **Puente NAS** con Elixium/streamrip cuando haya servidor.

## Resolvedor de enlaces y Sueltas (2026-11-22)

- **Redomi / song.link**: MainActivity recibe `ACTION_SEND` (texto) y
  `ACTION_VIEW` de Deezer, Spotify, YouTube/YouTube Music, Tidal, Apple Music,
  SoundCloud y Amazon Music. Extrae la primera URL y abre la hoja
  `LinkResolverFragment`, que consulta `api.song.link` y muestra título,
  artista, portada, enlaces por plataforma y una lista de coincidencias en la
  biblioteca local. Tocar una coincidencia la reproduce en la cola normal.
- La clave de cliente de song.link es la misma que usa Redomi (GPL-3.0) y se
  documenta como identificador público, no como credencial de usuario. Si
  song.link la rota, solo hay que actualizar `OdesliClient.API_KEY`.
- **Sueltas**: `ContentType.LooseSongs` y `Repository.looseSongs()` agrupan las
  pistas cuyo álbum tiene una o dos canciones; se excluyen silenciados y
  ocultos. Aparecen como sección “Sueltas” en Home (20 aleatorias) y como vista
  completa con Reproducir/Aleatorio.
- Verificado en el Moto G51: la sección Sueltas muestra 490 canciones y abre su
  lista; el enlace `deezer.com/track/1982222107` resuelve a *Miku* de
  Anamanaguchi y la encuentra en la biblioteca local. La primera prueba con la
  API pública sin clave devolvió 401 `PUBLIC_API_ACCESS_DEPRECATED`; con la
  clave de cliente de Redomi funciona.

## Decisiones pendientes

1. **Deezer primero**: ¿catálogo + playback nativo en el DAP, o puente NAS
   primero? Recomendación: catálogo nativo, luego playback, y el puente para
   descargas masivas.
2. **Discord**: ¿webhook primero o companion de PC desde el inicio?
   Recomendación: webhook primero; companion después.
3. **UI**: ¿priorizar retrato o el modo paisaje tipo Sonora? Recomendación:
   retrato primero, paisaje como segundo entregable.
4. **Fuentes extra**: ¿Subsonic/Navidrome? Sonora lo soporta y es una vía
   legal para tu biblioteca del NAS.
