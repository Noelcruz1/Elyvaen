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
| 7 | **Tailscale + NAS directa (SMB/WebDAV)** | Hub fuera de casa |
| 8 | Subsonic/Navidrome y otras fuentes | Ecosistema |

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

- **Implementado en 1.15.0:** streaming completo opcional mediante SmartLogin y
  `gcast/streamUrls`, con token cifrado y reproducción en Media3. Queda pulir
  una cola remota con varias pistas y la carátula del mini player.
- **Implementado en 1.16.0:** selector persistente FLAC / MP3 320 / MP3 128 en
  el diálogo de cuenta, con fallback automático a la siguiente calidad.
- Unificar visualmente la cola local + Deezer (la pista remota ya se conserva
  en la cola del reproductor).
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
- **Streaming completo 1.15.0:** SmartLogin y `streamUrls` inspirados en Pixer
  (MIT), token cifrado y fallback a vista previa. Ver `docs/ui-v2.md` y
  `third_party/pixer/LICENSE`.

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

## Salud de biblioteca (2026-11-22)

- `MetadataHealthFragment` analiza las pistas visibles y calcula un porcentaje
  de metadatos completos, excluyendo silenciados y ocultos.
- Detecta: sin título, sin artista, sin álbum, sin número de pista, sin
  carátula y grupos de posibles duplicados (título + artista normalizados).
- El **año no se usa en el análisis rápido**: en este dispositivo MediaStore
  deja `YEAR` nulo en FLAC aunque el archivo tenga `DATE` (se comprobó con
  *505* y *Asterisk*); marcarlo daba cientos de falsos positivos. Se sigue
  pudiendo editar en el editor.
- El score principal mide la **identidad** (título, artista y álbum); las
  incidencias restantes se muestran aparte para no ocultar trabajo pendiente.
- La pantalla tiene filtros por problema y una lista con la etiqueta de cada
  incidencia. Tocar una pista abre el editor de etiquetas existente; al volver,
  la salud se recalcula.
- **Detectar carátulas** comprueba la portada embebida con TagLib y, si no
  existe, la carátula que MediaStore tiene para el álbum (la que usa la app).
  Los archivos ilegibles se tratan como desconocidos, no como faltantes.
- **Autocompletar** rellena solo álbum (y año si también falta) de hasta 10
  pistas con artista conocido. Consulta MusicBrainz con una pausa de 1,1 s y,
  si no responde o no encuentra el tema, usa Deezer como respaldo. Nunca
  sobrescribe etiquetas existentes y muestra un resumen. Las pistas sin artista
  se dejan para edición manual para no aplicar coincidencias dudosas.
- Verificado en el Moto G51: 893 pistas, **98 % de identidad**, 10 incidencias
  (8 sin artista, varias sin número de pista), **0 carátulas faltantes** tras
  el escaneo y ningún álbum vacío que autocompletar. Las carátulas visibles
  de MediaStore evitan falsos positivos.
- Acceso desde la tarjeta **Salud de biblioteca** de Home y desde el menú de la
  biblioteca.
- Inspirado en Metadator, pero **sin copiar código AGPL**: usa MediaStore,
  TagLib, MusicBrainz, Deezer y las pantallas de edición que Elyvaen ya tenía.

## Discord: webhook de “ahora suena” (2026-11-22)

- Ajustes → Red → **Discord** permite pegar la URL de un webhook de canal y
  activar **Enviar lo que suena**.
- Al cambiar de canción, `DiscordWebhookService` publica un embed con título,
  artista, álbum y año. La URL se guarda solo en las preferencias privadas del
  dispositivo y no se escribe en logs. El envío se limita al mismo tema durante
  60 s para no duplicar mensajes al buscar dentro de la pista.
- **No es Rich Presence**: Discord Android no acepta RPC de terceros. Un
  companion en PC podría leer la sesión multimedia del DAP en el futuro; por
  ahora el webhook es la opción portable.
- La integración queda lista para configurar, pero no se pudo publicar en un
  canal real sin una URL de webhook del usuario.

## Elyvaen Hub: fases propias

Objetivo: que el DAP sea un hub completo de música, no solo un reproductor que
depende del PC para cargar archivos.

| Fase | Entregable | Valor |
|---|---|---|
| H1 | Hub de ingesta: USB OTG + subida por Wi-Fi con QR + gestor de almacenamiento · **implementado 2026-11-22** | Elimina el PC del día a día |
| H2 | Fuentes NAS: SMB, WebDAV, SFTP, Subsonic/Navidrome; sync y backup · **SMB/WebDAV/SFTP + descarga + sync/backup implementados 2026-11-23; Subsonic pendiente** | Biblioteca en red |
| H3 | Servidor DLNA/UPnP + WebDAV + control remoto web · **implementado 2026-11-22** | El DAP sirve a la casa |
| H4 | Motor de audio: UAC2 exclusivo, frecuencia nativa, EQ paramétrico, convolución, crossfeed, DoP · **DSP, EQ, crossfeed, convolución y estado UAC2 implementados 2026-11-22; DoP pendiente de kernel/USB DSD** | Nivel audiófilo |
| H5 | AudioVisual: visualizador, letras/karaoke, vídeo local, Cast/DLNA · **visualizador, karaoke, vídeo local y envío DLNA implementados 2026-11-22; Chromecast nativo pendiente** | Experiencia de sala |
| H6 | Modo DAP: batería, térmica, botones, modo dedicado · **implementado 2026-11-22** | Sensación de hardware dedicado |
| **H7** | **Tailscale + NAS directa**: estado de Tailscale, MagicDNS, SMB/WebDAV/SFTP y sync/backup sobre la tailnet · **estado Tailscale/MagicDNS y guardas implementados 2026-11-23; conexión real pendiente de credenciales** | Acceso al NAS sin abrir puertos |

## Fase H7 — Tailscale + NAS directa

**Sí es posible.** La forma recomendada no es embeber Tailscale dentro de
Elyvaen, sino usar la **app oficial de Tailscale en Android** y que Elyvaen
consuma el NAS a través de la VPN del sistema.

### Arquitectura recomendada

1. **Tailscale oficial** (`com.tailscale.ipn`) inicia sesión y mantiene la
   tailnet. Elyvaen detecta:
   - si la app está instalada (`PackageManager`);
   - si hay una VPN activa (`NetworkCapabilities.TRANSPORT_VPN`);
   - el estado de conexión y un botón para abrir Tailscale.
2. **MagicDNS**: Elyvaen usa el nombre del NAS (`nas.tailnet.ts.net`) o su IP
   `100.x.y.z`. No hace falta DDNS ni abrir puertos.
3. **Protocolos NAS**:
   - **SMB2/3** con SMBJ para compartir carpetas;
   - **WebDAV** con Sardine/OkHttp para Synology, Nextcloud, etc.;
   - **SFTP** con sshj para servidores SSH;
   - **Subsonic/Navidrome** por REST como fuente de streaming.
4. **Tailscale Serve** (opcional): publica WebDAV/HTTPS dentro de la tailnet con
   certificado válido. Evitar Funnel salvo necesidad; expone el servicio a
   Internet.
5. **Sync/backup**: elegir carpeta local ↔ carpeta NAS, manual o programado,
   con política de conflictos y caché offline. Respaldar biblioteca, playlists,
   ajustes y base de datos.

### Seguridad

- Las credenciales del NAS se guardan con **EncryptedSharedPreferences /
  Android Keystore**, nunca en texto plano ni en logs.
- Elyvaen **no almacena claves de auth de Tailscale**: el login y las ACLs
  viven en la app oficial.
- Validar host y certificado; no aceptar cualquier certificado por conveniencia.
- Para SMB, usar SMB2/3; evitar SMB1.
- Limitar el servidor de subida Wi-Fi a la red local y usar un token temporal.

### Límites reales

- Android permite **una VPN activa**; Tailscale y otra VPN no conviven.
- La app oficial debe estar conectada; si el usuario la cierra, el NAS deja de
  ser accesible fuera de casa.
- Embeber Tailscale con `tsnet`/gomobile es posible pero pesado: permisos de VPN,
  batería, binarios y mantenimiento. Se deja como opción avanzada, no inicial.
- El rendimiento SMB sobre la tailnet depende de la CPU, la batería y la red;
  para bibliotecas grandes conviene caché local.
- Sin root no hay modo “USB DAC” del DAP hacia el PC; Tailscale/NAS es la vía
  limpia para compartir música.

### Dependencias previstas

- `com.hierynomus:smbj` (SMB2/3)
- `com.github.lookfirst:sardine` o cliente WebDAV sobre OkHttp
- `com.hierynomus:sshj` (SFTP)
- Cliente REST para Subsonic/Navidrome
- `EncryptedSharedPreferences` / Keystore

### Criterios de aceptación de H7

- La pantalla Hub muestra “Tailscale conectado / desconectado” y abre la app.
- Un asistente permite añadir el NAS por MagicDNS con SMB o WebDAV.
- “Probar conexión” funciona con Tailscale activo y falla con un mensaje claro
  sin él.
- Se puede explorar el NAS, descargar una carpeta y reproducir sin PC.
- El backup de biblioteca/playlists se puede enviar al NAS.
- Nada de credenciales en logs; sin root; sin abrir puertos en el router.

### Implementación H7 (2026-11-23)

- **Detección de Tailscale** en `TailscaleStatus`: app oficial instalada,
  transporte VPN activo y dirección de tailnet en el rango `100.64.0.0/10`.
- **Hub**: la tarjeta NAS muestra el estado de Tailscale
  (`No instalado`, `Instalado · VPN inactiva`, `Conectado · 100.x`) y permite
  abrir o instalar la app oficial.
- **MagicDNS**: el formulario reconoce hosts `*.ts.net`, muestra un aviso y
  `Probar conexión` se detiene antes de la red si Tailscale no está instalado
  o conectado. El navegador NAS aplica la misma guarda a explorar, descargar,
  sincronizar y subir copias de seguridad.
- **Mensajes claros**: “Instala Tailscale y conéctate para usar MagicDNS” o
  “Activa Tailscale para acceder a este host”, sin exponer credenciales.
- **Sin cambios en el almacenamiento**: las credenciales siguen cifradas con
  `EncryptedSharedPreferences`; H7 no añade hosts, usuarios ni contraseñas por
  defecto. El NAS real sigue usando SMB/WebDAV de H2b.

### Verificación en el Moto G51

- `pm list packages` no mostró Tailscale en el DAP; Hub y ajustes muestran
  **No instalado** y el botón **Instalar Tailscale**.
- Con el host de prueba `elyvaen-demo.ts.net`, el formulario mostró
  **MagicDNS detectado** y **Probar conexión** devolvió
  “Instala Tailscale y conéctate para usar MagicDNS” de inmediato, sin tocar
  la red y sin modificar el host real guardado.
- No se pudo validar el estado **Conectado** ni la resolución MagicDNS real
  porque falta instalar e iniciar sesión en la app oficial de Tailscale.
- La conexión SMB/WebDAV real, la descarga y el backup quedan pendientes de que
  el usuario introduzca las credenciales del NAS. El formulario del dispositivo
  conserva el host y recurso reales con usuario/contraseña vacíos.
- Captura de referencia: `docs/ui-v2/screens/nas-tailscale.png` (host y recurso
  de demostración; sin datos reales).

### Límites de H7

- Se usa la app oficial de Tailscale; no se embebe `tsnet`/gomobile.
- “Conectado” exige VPN activa y una IPv4 en `100.64.0.0/10`. Otra VPN que use
  el mismo rango podría dar un falso positivo, algo poco habitual.
- MagicDNS se reconoce por el sufijo `.ts.net`; la IPv6 de tailnet no se trata
  explícitamente.
- La prueba real de explorar, descargar, sincronizar y restaurar sobre la
  tailnet requiere credenciales y una sesión de Tailscale activa.

## Fase H1 — Hub de ingesta (2026-11-22)

La tarjeta **Elyvaen Hub** de Home abre una pantalla propia con tres vías para
meter música sin PC y un resumen del almacenamiento. La tarjeta NAS sigue
llevando al formulario SMB/WebDAV.

### Importación por USB, SD o carpeta

- Usa el selector SAF del sistema (`OpenDocumentTree`), así que sirve para un
  USB OTG, una tarjeta SD o cualquier carpeta interna sin pedir permisos
  amplios de almacenamiento.
- Recorre la carpeta de forma recursiva, conserva los subdirectorios y copia
  solo audio compatible: FLAC, MP3, M4A, AAC, OGG, OPUS, WAV, AIFF, WMA, APE,
  DSF, DFF y MKA.
- El destino es `Music/Elyvaen/Import/<carpeta>`. En Android 10+ se publica
  directamente en MediaStore con `IS_PENDING`; no hace falta PC ni un escaneo
  manual. En versiones anteriores se escribe en el almacenamiento público y se
  avisa a `MediaScannerConnection`.
- Los nombres duplicados no se sobrescriben: se añade un sufijo. El USB de
  origen **solo se lee**, nunca se mueve ni se borra nada.
- La pantalla detecta volúmenes extraíbles con `StorageManager` y avisa cuando
  hay un USB montado; el selector permite elegir la carpeta concreta.
- Hay progreso por archivo, cancelación y resumen de importados/errores.

### Subida por Wi-Fi con QR

- `WifiUploadService` es un servicio en primer plano de tipo `dataSync` con
  wake lock parcial: el servidor sigue disponible con la pantalla apagada.
- El servidor embebido (NanoHTTPD) escucha en el puerto 8099 (o el siguiente
  libre) y solo se anuncia con la IP Wi-Fi del DAP.
- La URL lleva un token aleatorio de 12 caracteres. El QR se genera con ZXing;
  también se puede copiar el enlace. Sin el token, la ruta devuelve 404.
- La página web permite arrastrar o elegir varios archivos, los sube de uno en
  uno con progreso y los publica en `Music/Elyvaen/Import/Wi-Fi`.
- Se aceptan los mismos formatos que la importación local y un máximo de 4 GB
  por archivo. El nombre se sanea y el archivo temporal se borra al terminar.
- La notificación muestra el enlace y tiene una acción **Detener**; la pantalla
  del Hub muestra las últimas subidas y los bytes recibidos.
- No se guardan credenciales de Tailscale ni del NAS para esta función: el
  token es efímero y solo vale en la red local.

### Gestor de almacenamiento

- Lista los volúmenes montados (interno, SD o USB), con espacio libre y total.
- Suma la biblioteca de MediaStore y separa **Descargas Deezer** e
  **Importaciones**.
- Calcula la caché de la app y permite vaciarla. La limpieza solo toca
  `cacheDir`, `externalCacheDir` y `codeCacheDir`: no borra música descargada,
  importada ni archivos del usuario.
- Acceso directo a los ajustes de almacenamiento del sistema.

### Verificación en el Moto G51

- Hub abierto desde Home; la tarjeta NAS sigue funcionando.
- Importación SAF de dos WAV a `Music/Elyvaen/Import/HubImportTest/`:
  **2 importados, 0 errores**, visibles en MediaStore y en la biblioteca.
- Subida Wi-Fi real por `adb forward` y HTTP: primer archivo recibido y
  contabilizado; la biblioteca pasó de 897 a 898 sin salir del Hub. Un segundo
  envío con el mismo nombre se guardó como `hub-live (1).wav`, sin sobrescribir.
- QR y enlace visibles; servicio en primer plano, notificación y parada
  verificadas. La caché se limpió (11,01 MB) sin tocar las importaciones.
- El kiosco sigue en `PINNED` y el root de Magisk sigue activo.

## Fase H2a — explorar y descargar del NAS (2026-11-22)

Con el formulario NAS ya validado, el Hub añade una pantalla para navegar por
la biblioteca remota y traer música al DAP sin PC.

### Lo implementado

- **SMB2/3** con SMBJ y **WebDAV** con OkHttp. Se abre una sola sesión por
  operación: listar una carpeta, descargar un archivo o descargar una carpeta
  completa de forma recursiva.
- La pantalla **NAS** muestra carpetas y archivos, tamaño/fecha, ruta actual y
  migas de pan. Se entra desde **Elyvaen Hub → Explorar NAS** o desde el icono
  de ajustes del navegador.
- Tocar un audio lo descarga; **Descargar esta carpeta** recorre la carpeta y
  trae solo formatos compatibles, conservando subcarpetas bajo
  `Music/Elyvaen/NAS/…`.
- Las descargas se publican en MediaStore con el mismo gestor de importación
  de H1. Los nombres existentes no se sobrescriben: se añade un sufijo.
- Progreso por archivo, cancelación, resumen de descargados/errores/ignorados
  y detección de credenciales rechazadas, ruta inexistente o timeout.
- El formulario acepta pegar `http://` o `https://` en el host y lo normaliza;
  el interruptor HTTPS se respeta y se activa solo si el host ya era HTTPS.
- Se habilitó **tráfico HTTP local** en el manifiesto: un NAS doméstico suele
  publicar WebDAV sin TLS en la LAN o en la tailnet. Para exponerlo fuera hace
  falta HTTPS/Tailscale Serve o un proxy con certificado.

### Verificación en el Moto G51

- Se levantó un **Samba 4.24** de prueba en el PC, con un recurso `Music` y
  tres WAV en `Albums/Miku` y `Albums/Orange Range`, más un `cover.txt`.
- Se levantó un **WebDAV** de prueba con Basic auth. El formulario del Hub
  guardó host/puerto/credenciales cifrados y **Probar conexión** devolvió
  “Conexión correcta” en ambos protocolos.
- El navegador listó `/`, `/Albums` y las dos carpetas de artista. La descarga
  de `/Albums` por WebDAV y por SMB terminó con **3 archivos, 0 errores,
  529 KB** y **1 archivo no reproducible ignorado**.
- Los WAV quedaron en `Music/Elyvaen/NAS/Albums/…`; en la segunda descarga se
  crearon variantes `(2)` en vez de sobrescribir. La biblioteca los indexó.
- El menú de ajustes del navegador conserva los datos; el host y las
  credenciales de prueba se borraron del dispositivo al terminar.

## Fase H2b — sincronización y backup al NAS (2026-11-22)

### Sincronización incremental

- **Sincronizar carpeta** recorre la carpeta remota y descarga únicamente el
  audio que aún no está en `Music/Elyvaen/NAS`.
- Si ya existe un archivo con el mismo nombre y tamaño, se cuenta como
  **ya presente** y no se vuelve a bajar.
- Si el nombre existe pero el tamaño cambió, se cuenta como **conflicto** y
  se conserva el archivo local: nunca se sobrescribe.
- **Descargar todo** sigue disponible para forzar una copia completa; en ese
  caso los nombres repetidos reciben sufijos `(1)`, `(2)`, etc.
- El resumen muestra nuevos, ya presentes, conflictos, errores, archivos no
  reproducibles ignorados y bytes transferidos.

### Backup al NAS

- **Subir copia de seguridad** genera un `.bmgbak` real con
  **ajustes, playlists, historial de reproducción y letras**, y lo sube a la
  carpeta NAS que se está explorando.
- Usa el mismo `BackupManager` de la app: la copia se puede restaurar después
  desde la pantalla de backup local eligiendo el archivo descargado.
- El archivo se crea en caché, se sube por SMB, WebDAV o SFTP y se borra de
  caché al terminar. No se guarda ninguna credencial ni ruta en el repositorio.
- La copia se muestra en el navegador NAS como archivo no reproducible; se
  puede descargar a mano para restaurarla.

### Verificación en el Moto G51

- Servidor SMB local de prueba, sin datos reales en el código:
  - primera sincronización de `/Albums`: **3 nuevos, 0 presentes, 0 conflictos,
    0 errores, 529 KB**;
  - segunda sincronización: **0 nuevos, 3 ya presentes, 0 conflictos**;
  - tras cambiar el tamaño de un archivo remoto: **1 conflicto**, sin
    sobrescribir el archivo local.
- **Subir copia de seguridad** subió un `.bmgbak` de **3,89 KB** con
  `metadata.json`, `settings/main.json`, una playlist M3U y
  `play_info/default.json`; el ZIP se abrió y validó desde el PC.
- La descarga por WebDAV y SMB ya estaba validada en H2a. Los archivos,
  credenciales y servidores de prueba se borraron del dispositivo y del
  entorno al terminar.

### Pendiente de H2

- Subsonic/Navidrome por REST como fuente de streaming.
- Restauración directa desde el navegador NAS (hoy se descarga el `.bmgbak`
  y se usa la pantalla de backup existente).
- Sincronización programada y política de conflictos configurable.
- Validar contra el OMV real cuando Tailscale esté instalado y el usuario
  introduzca sus credenciales; **ningún host real se versiona**.

## Fase H2c — SFTP con huella verificada (2026-11-23)

El NAS añade un tercer protocolo para servidores OpenSSH/OMV, con verificación
estricta de la clave del servidor.

### Implementado

- **SFTP con sshj**: puerto 22 por defecto, autenticación por usuario y
  contraseña, listado, descarga, subida, creación de carpetas, sync y backup.
- **Huella SSH SHA256 obligatoria**: se puede pegar o capturar con **Detectar
  huella**. La conexión usa `FingerprintVerifier`; si la huella no coincide, no
  se autentica. La huella se guarda cifrada junto a las credenciales.
- **Compatibilidad Android**: Android incluye un BouncyCastle antiguo que no
  soporta X25519/EC. La app registra el BouncyCastle moderno incluido en el APK
  antes de abrir SSH; sin esto OpenSSH moderno rechaza la negociación.
- El guard de Tailscale/MagicDNS de H7 también se aplica a hosts `*.ts.net`.
- La ruta SFTP puede ser relativa al home del usuario o absoluta.

### Verificación en el Moto G51

- Servidor OpenSSH 10.3 local por `adb reverse` (`127.0.0.1:2222`), usuario de
  prueba y carpeta `/Music` con `Test/` y `demo.flac`; **sin datos reales en el
  repositorio**.
- **Detectar huella** devolvió la misma `SHA256:…` que `ssh-keygen -lf`.
- Huella alterada → **La huella SSH no coincide con el servidor**; no hubo
  autenticación.
- Huella correcta → **Conexión correcta**.
- **Explorar NAS** listó `/Music`, `Test/` y `demo.flac` (10 B).
- **Subir copia de seguridad** subió un `.bmgbak` de **5,98 KB** por SFTP.
- El servidor, usuario, host key, archivos y credenciales de prueba se
  borraron. La configuración real del dispositivo se restauró desde una copia
  cifrada y sigue mostrando SMB.
- Capturas: `docs/ui-v2/screens/nas-sftp.png` y
  `docs/ui-v2/screens/sftp-browser.png` (datos de demostración).
- La app queda en **1.13.0**.

### Límites de H2c

- Solo contraseña; claves SSH y `ssh-agent` quedan pendientes.
- La confianza es TOFU: “Detectar” captura la huella de la primera conexión y
  el usuario debe compararla con el servidor antes de guardar.
- El servidor debe exponer el subsistema `sftp`; no hay fallback SCP.
- Sigue el modelo de descarga/sync, no streaming directo.
- La conexión contra el OMV real queda pendiente de credenciales.

## Fase H3 — DLNA/UPnP, WebDAV y control web (2026-11-22)

El DAP puede servir su biblioteca a la casa sin PC. Se abre desde **Elyvaen Hub
→ Transmitir a la casa** y se ejecuta en un servicio en primer plano con
notificación y acción de parada.

### DLNA/UPnP para TVs

- Servidor SSDP mínimo en `239.255.255.250:1900`, con `MulticastLock`, respuestas
  a `M-SEARCH` y anuncios `ssdp:alive`/`byebye`.
- Descripción UPnP `MediaServer:1`, `ContentDirectory:1` y
  `ConnectionManager:1` en `http://<ip>:8200/dlna/description.xml`.
- Browse jerárquico: **Artistas → pistas**, **Álbumes → pistas**,
  **Canciones** y **Listas**. Devuelve DIDL-Lite con título, artista, álbum,
  duración, tamaño y `protocolInfo` de audio.
- Los archivos se sirven con **Range**, `Accept-Ranges`, `Content-Range` y
  cabeceras DLNA, así que la TV puede buscar dentro del FLAC sin descargarlo
  entero.
- Nombre visible: `Elyvaen (<modelo>)`.

### WebDAV y control web

- **WebDAV de solo lectura** en `/dav/<token>/` con la jerarquía virtual
  `Music/<artista>/<álbum>/<pista>`, `PROPFIND`, `GET`/`HEAD` y Range.
- **Control web** en `/web/<token>/`: muestra lo que suena, permite
  play/pausa, anterior/siguiente, barra de posición y elegir canciones de la
  biblioteca. Usa el `MediaSession` de `PlaybackService`; no crea un segundo
  reproductor.
- El token de 12 caracteres se genera al arrancar y cambia en cada sesión.
  Web y WebDAV lo exigen; DLNA queda abierto solo a la red local porque las TV
  no negocian token.
- El servidor escucha en el puerto 8200 (o el siguiente libre), solo se inicia
  con Wi-Fi y no abre puertos en el router.

### Verificación en el Moto G51

- Descripción UPnP y SCPD servidas correctamente; socket SSDP en `:1900` y
  hilo `elyvaen-ssdp` activos. La prueba con una TV física queda pendiente.
- `ContentDirectory Browse` del raíz: 281 artistas, 488 álbumes, 893 canciones
  y las listas. Browse de `songs` devolvió items con `res` FLAC.
- `GET /dlna/media/<id>` con `Range: bytes=0-1023`: **HTTP 206**, 1024 bytes,
  `Content-Range: bytes 0-1023/22491201`, FLAC 16/44.1 válido.
- WebDAV: `PROPFIND` raíz y `/Music/` con 207; descarga parcial de una pista
  FLAC con 206. La jerarquía usa nombres saneados y carpetas por artista/álbum.
- Control web: página y API respondieron; `toggle` cambió el estado del DAP y
  `play?songId=…` reprodujo otra pista en el reproductor real.
- **Parada del servidor**: puerto 8200 y SSDP 1900 cerrados, notificación
  retirada y UI vuelve a “Activar servidor” sin ANR.

### Límites y pendientes

- La TV debe estar en la misma Wi-Fi; no hay autenticación DLNA estándar.
- No se incluye Chromecast/Google Cast (requiere Play Services y otro modelo
  de sesión); H5 puede añadir Cast/DLNA como *sender* desde el DAP.
- Faltan carátulas en DIDL, búsqueda DLNA y perfiles de transcodificación.
- El control web no gestiona listas ni búsqueda todavía.
- No se versiona ningún nombre de host, share o credencial real.

## Fase H4 — motor de audio (2026-11-22)

El DSP vive dentro del hilo de audio de Media3, después de balance y ReplayGain,
así que no depende de efectos globales de Android ni de una app externa. Se
abre desde **Elyvaen Hub → Motor de audio**.

### Implementado

- **EQ paramétrico de 5 bandas**: low shelf, tres peaks y high shelf. Cada banda
  ajusta frecuencia (20 Hz–20 kHz), ganancia (±12 dB) y Q (0,3–6) cuando es un
  peak. Coeficientes biquad RBJ recalculados en el hilo de audio.
- **Crossfeed**: retardo corto + paso bajo a 700 Hz, cantidad ajustable de 0 a
  100 %, pensado para auriculares.
- **Convolución FIR**: importación de un WAV mono/estéreo mediante SAF, copia a
  almacenamiento privado, normalización, adaptación lineal a la frecuencia de
  salida y truncado a **512 taps** para mantener la CPU bajo control.
- **Soporte PCM 16/24-bit y float** en el procesador DSP, y también en balance
  y ReplayGain para que la salida float no rompa la cadena.
- **Panel UAC2**: estado del enlace, frecuencia del host, modo nativo o
  compatibilidad, streaming y el interruptor **Frecuencia nativa del host**
  conectado al `Uac2Control` existente.
- **DoP** queda documentado como no disponible: este kernel/puente UAC2 no
  expone endpoints DSD nativos y el módulo no simula DSD sobre PCM.

### Verificación en el Moto G51

- La pantalla H4 abrió con el panel UAC2 y el motor DSP.
- Se cargó un impulso WAV real mediante el selector SAF; la app mostró
  **“Impulso cargado”**, lo copió a almacenamiento privado y activó
  convolución y crossfeed.
- Con DSP + EQ + crossfeed + convolución activos, la reproducción siguió en
  estado **playing** y la posición avanzó; no hubo crash, ANR ni errores de
  `AudioTrack`.
- UAC2 estaba apagado durante la prueba, así que el interruptor nativo no se
  pudo validar contra un host real; el estado y la ruta de control ya están
  conectados al servicio existente.
- Después de la prueba se eliminó el impulso de prueba, se apagaron crossfeed,
  convolución y el motor DSP, y se borró el WAV de `Download`.

### Límites de H4

- No hay medición calibrada ni corrección automática de sala; el EQ es manual.
- La topología es fija de 5 bandas; no se pueden añadir/eliminar bandas desde la
  UI todavía.
- El FIR se limita a 512 taps y no usa FFT particionada; suficiente para
  correcciones suaves, no para IR de sala largos.
- DoP/DSD nativo queda pendiente de una ruta UAC2 que lo soporte.
- El panel UAC2 reutiliza el FIFO/servicio existente; no crea un segundo
  reproductor ni toca el kernel desde la app.

## Fase H5 — AudioVisual y envío a TV (2026-11-22)

La pantalla **Elyvaen Hub → Sala / AudioVisual** reúne visualizador, karaoke,
vídeo local y envío DLNA a TVs o altavoces.

### Implementado

- **Visualizador real**: un procesador de paso captura la onda mono del flujo
  PCM de Media3 (16/24-bit o float) cada ~33 ms y la publica en un
  `StateFlow`. La UI dibuja 48 barras con degradado. No usa `RECORD_AUDIO` ni
  el permiso de micrófono; lee el audio que ya está reproduciendo el DAP.
- **Letras / Karaoke**: botón directo a la pantalla de letras sincronizadas
  existente, que ya resalta la línea actual, mantiene la pantalla encendida y
  permite buscar la posición tocando una línea.
- **Vídeo local**: consulta MediaStore.Video, lista título/duración/tamaño y
  abre `VideoPlayerActivity` con ExoPlayer y controles. La actividad pide foco
  de audio de película para pausar la música y mantiene la pantalla encendida.
- **Enviar a TV (DLNA sender)**:
  - Descubre `MediaRenderer:1` por SSDP.
  - Lee la descripción UPnP, localiza el `controlURL` de AVTransport y envía
    `SetAVTransportURI` + `Play`.
  - Arranca automáticamente el servidor H3 si hace falta y usa la URL
    `/dlna/media/<id>` de la canción actual, con metadata DIDL-Lite.
  - La TV debe estar en la misma Wi-Fi; no se abre ningún puerto en el router.

### Verificación en el Moto G51

- El visualizador mostró onda real y cambiante con `*Asterisk` de Orange Range
  mientras el estado de MediaSession era `playing`.
- **Buscar TVs** ejecutó SSDP y devolvió “No se encontraron TVs o dispositivos
  DLNA” en la prueba; no había una TV DLNA disponible en ese momento. La UI y
  el manejo de lista vacía quedaron validados.
- Vídeo local: se copió un MP4 de prueba, apareció en la lista
  (**1 vídeo, 0:10**) y `VideoPlayerActivity` lo reprodujo con controles.
- **Letras / Karaoke** abrió la letra sincronizada y resaltó la línea activa.
- El MP4 temporal y su entrada de MediaStore se borraron al terminar. El
  servicio H3 no quedó ejecutándose.

### Límites de H5

- No hay Chromecast/Google Cast nativo; requiere Cast SDK/Play Services.
- El envío DLNA manda la canción actual; aún no hay play/pausa/seek remotos ni
  selección de cola para el renderer.
- La TV física debe probarse en casa; el descubrimiento SSDP ya está
  implementado y se validó el caso sin dispositivos.
- La lista de vídeo es básica (sin miniaturas, subtítulos ni cast de vídeo).

## Fase H6 — Modo DAP (2026-11-22)

La tarjeta **Elyvaen Hub → Modo DAP** reúne el estado real del hardware y los
controles de dedicación del DAP.

### Implementado

- **Batería real**: nivel, estado (cargando/completa/descargando), salud,
  temperatura, voltaje y corriente. Usa el *sticky broadcast* de Android y
  recurre a `/sys/class/power_supply/battery` si el intent no está disponible.
- **Térmica y CPU**: estado térmico de Android, zona más caliente y governor
  actual (`schedutil`). La app solo lee; no escribe en `/sys`.
- **Modo dedicado**:
  - kiosco / app fijada con `startLockTask()` y confirmación del sistema;
  - mantener pantalla encendida (`FLAG_KEEP_SCREEN_ON`, apagado por defecto);
  - refresco 60/90/120 Hz aplicado a la ventana, con 90 Hz por defecto.
- **Botones**:
  - el botón de asistente/cámara (scancode 766 → `CAMERA`) se puede remapear a
    play/pausa, siguiente, anterior, abrir player, kiosco o nada;
  - el botón multimedia conserva una pulsación play/pausa y dos pulsaciones
    siguiente;
  - mantener Volumen arriba sale del kiosco.
- **Auto-refresco** cada 3 s mientras la pantalla está visible, para que el
  estado de batería, temperatura y kiosco no quede obsoleto tras el diálogo de
  fijación.
- No se pide root al abrir la pantalla. La app no escribe en sysfs ni cambia
  governors: el módulo Magisk del DAP sigue siendo el único que aplica el límite
  de carga y el perfil de CPU.

### Verificación en el Moto G51

- Batería: **100%**, **31.8–32.5 °C**, **4359–4360 mV**, salud **Buena**, estado
  **Completa · conectado**.
- Térmica: estado **Normal**, zona más caliente **37–41 °C**, governor
  **schedutil**.
- Refresco: 60 Hz → `modeId 1`, 90 Hz → `modeId 3`, 120 Hz → `modeId 2` en
  `dumpsys display`.
- Pantalla encendida: la ventana pasó a mostrar `fl=KEEP_SCREEN_ON`; el ajuste
  se guarda y se vuelve a aplicar al abrir la app.
- Kiosco: **Activar** mostró el diálogo de fijación, `mLockTaskModeState=PINNED`
  y el estado pasó a **Activo · app fijada**. **Salir** dejó el estado en
  `NONE`.
- Botón cámara: con la acción **Play/Pausa** alternó `state=2` y `state=3`;
  con **Siguiente** y **Anterior** cambió la pista activa. La acción elegida se
  conserva.
- Botón multimedia: una pulsación alternó reproducción y dos pulsaciones
  avanzaron de pista.
- La captura final está en `docs/ui-v2/screens/dap-mode.png`; la tarjeta de
  botones, en `docs/ui-v2/screens/dap-buttons.png`.

### Límites de H6

- El límite de carga del módulo (`charge_control_limit`) no es legible desde la
  app sin root por SELinux; la fila muestra **Controlado por Magisk** como
  respaldo. El límite de 80% sigue aplicándose por el módulo.
- El kiosco usa *screen pinning*, no device owner: la primera vez Android pide
  confirmación. Si se cancela, la app queda en inmersivo pero sin fijar.
- No hay control de governor desde la app, solo lectura, para no interferir con
  la política térmica del módulo.
- El remapeo del botón de asistente depende del keylayout del módulo Magisk.
- Triple pulsación no se anuncia porque esta versión de Media3 no la expone de
  forma fiable; una y dos pulsaciones están verificadas.

## NAS general y OMV (2026-11-22)

- Se añadió un formulario **NAS y Tailscale** con protocolo SMB o WebDAV, host,
  puerto, recurso/ruta, usuario, contraseña, dominio y HTTPS. **No hay host ni
  credenciales por defecto**: el usuario las escribe y se guardan con
  `EncryptedSharedPreferences` respaldado por Android Keystore.
- La tarjeta de Tailscale detecta si la app oficial está instalada y si hay una
  VPN activa; ofrece abrirla o instalarla. Tailscale es opcional: sin él se usa
  la IP local del NAS.
- **SMB** es mejor para OMV/Samba en LAN o tailnet: SMB2/3, carpetas y archivos
  grandes. **WebDAV** es mejor para HTTPS, Tailscale Serve y servidores tipo
  Nextcloud; suele ser más lento para bibliotecas enormes.
- OMV puede exponer SMB (Samba) y/o WebDAV (plugin openmediavault-webdav).
  Para sincronización masiva se recomienda SMB; para acceso remoto sencillo por
  HTTPS, WebDAV.
- Pendiente de H2: explorar el NAS, descargar carpetas, sync/backup y probar
  contra un OMV real. H7 añadirá el estado de Tailscale, MagicDNS y sync sobre
  la tailnet.

## Root: estado, comunidad y decisión

- El bootloader del Moto G51 ya está **desbloqueado**
  (`ro.boot.flash.locked=0`, `ro.boot.verifiedbootstate=orange`).
- No hay root instalado: no existe `su`; SELinux sigue enforcing.
- Dispositivo: `cypfq`, XT2171, Moto G51 5G, canal `attmx`, Android 12,
  build `S2RYA32.58-13-12-5-1-3`, slot `_a`, particiones dinámicas.
- **Sí hay comunidad y guías para cypfq**:
  - GetDroidTips: root con Magisk parcheando `boot.img`, sin TWRP.
  - XDA: TWRP para cypfq (2024) y crDroid oficial para cypfq (2024);
    también ProjectMatrixx y usuarios que flashearon ROMs.
  - Firmware XT2171 y variantes ATTMX/RETLA/OPENMX en espejos; para root hay
    que usar el **build/canal exacto**, no un boot.img genérico.
- **Riesgo**: no existe un porcentaje oficial de brick. Con bootloader
  desbloqueado, firmware exacto y flasheando solo `boot`/`boot_a`:
  - brick duro (necesita EDL/blankflash): bajo, pero no cero;
  - bootloop/brick blando: recuperable con el boot stock o LMSA/fastboot;
  - el riesgo sube con firmware de otra región/build, relock del bootloader,
    OTA estando rooteado, o flashear bootloader/módem/preloader.
- Beneficios de root para el Hub: montar el NAS a nivel de sistema, modo USB
  gadget/DAC, ajustes de kernel/térmica/batería y parámetros más profundos de
  Qualcomm/Aqstic. **Tailscale, SMB y WebDAV no necesitan root.**
- Costes: OTA y parches se complican, Play Integrity/Widevine pueden fallar
  (el bootloader ya está desbloqueado), más superficie de seguridad.
- **Historial real: Magisk ya se usó en este dispositivo.** La app
  `com.topjohnwu.magisk` v30.7 está instalada desde 2026-05-16. En
  `/sdcard/Download` siguen `Magisk-v30.7.apk`, `boot.img` y
  `magisk_patched-30700_K8Q6T.img`; en el PC hay backups (`cypfq_backup/`) y
  una imagen `build-uac2-test/boot_uac2_magisk.img`. El script
  `scripts/install-magisk-modules.sh` usa `su`, así que el root estuvo activo.
- **Root activo desde 2026-11-22.** Se parcheó `boot_uac2_fu9.img` con Magisk
  v30.7 y se flasheó solo `boot_a`. El kernel sigue siendo el UAC2 custom
  `#9`; `su -c id` devuelve `uid=0(root)` con contexto `magisk`.
- Imagen parcheada guardada en
  `/workspace/Android/cypfq_backup/boot_uac2_fu9_magisk.img`
  (SHA-256 `ece7a7644022a780f6ee45f47a0cb1c934756a88edea8bf1d00d7dca15cecf82`).
  `boot_uac2_fu9.img` sin parchear y `current_boot_a.img` stock quedan como
  rollback.
- Verificado tras reiniciar: Magisk 30.7 activo, kiosco `PINNED`, reproducción
  FLAC 16/44.1 intacta y hilo de audio 192 kHz disponible. Los módulos previos
  (`cypfq_kiosk`, `cypfq_os_core`, `cypfq_dap_system`, `cypfq_adb`, etc.)
  volvieron a cargar; `cypfq_audio_hires` sigue desactivado.
- **Rollback:** `fastboot flash boot_a /workspace/Android/cypfq_backup/boot_uac2_fu9.img`
  (o `current_boot_a.img` para stock) y reiniciar. No bloquear el bootloader.
- **Riesgo:** root ya probado en este equipo y con backups; brick duro bajo si
  solo se flashea `boot_a` con la imagen correcta. Sigue sin haber un
  porcentaje oficial. No instalar OTA estando rooteado: restaurar el boot stock
  primero.
- La app Magisk ofrece actualización 31.1; se mantiene 30.7 porque es la
  versión con la que se generó y validó esta imagen.

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
