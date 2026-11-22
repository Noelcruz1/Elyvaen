# Elyvaen UI v2 — APlayer structure, Symphony skin

Estado: **fase 1 y 2 implementadas y verificadas en el Moto G51** (2026-11-17).

## Dirección

- **No se simplifica Booming**: se conservan todas las pantallas, acciones,
  ajustes, cola, letras, EQ, DAC/UAC2, ADB, auto-arranque y biblioteca.
- **Estructura funcional**: APlayer (home de secciones + biblioteca por
  pestañas + lista densa + player con letras/cola).
- **Estética**: Symphony (negro cálido, superficies de cristal, tipografía
  grande, controles tipo píldora) con acentos Elyvaen/Miku (teal `#5FE6D8`,
  rosa `#FF8DBB`, lavanda `#C9B8FF`).
- **Navegación**: bottom nav existente, ahora con indicador tipo píldora y
  fondo de cristal; sin gestos secretos ni pantallas eliminadas.
- **Rendimiento**: animaciones de transform/opacity, sin blur dinámico por
  defecto, carátulas con Coil; el player usa el botón Morphic existente.

## Paridad funcional

| Área | Estado | Dónde |
|---|---|---|
| Home con secciones y acciones | Rediseñado | `ui/screen/library/home/ElyvaenHomeComponents.kt` |
| Búsqueda, ajustes, panel DAP | Conservados | top bar del Home |
| Reproducir todo / aleatorio | Añadidos y funcionales | `ElyvaenHomeIntro` |
| Top tracks / últimas agregadas / historial | Conservados | `ElyvaenHomeTopActions` |
| Para ti (favoritos/top) | Rediseñado a carrusel de canciones | `ElyvaenSongSpotlight` |
| Álbumes sugeridos | Rejilla 2/3 columnas + álbum destacado | `ElyvaenAlbumGrid` |
| Artistas sugeridos | Carrusel horizontal | `ElyvaenArtistRow` |
| Redescubrir | Lista densa con duración y play | `ElyvaenForgottenTracks` |
| Biblioteca (canciones, álbumes, artistas, géneros, años, carpetas, playlists) | Sin cambios funcionales; tema nuevo | pantallas existentes |
| Cola | Sin cambios funcionales; tema nuevo | `QueueFragment` |
| Letras | Sin cambios funcionales; tema nuevo | `LyricsFragment` |
| Ecualizador / sonido | Sin cambios funcionales; tema nuevo | `EqualizerScreen`, `SoundSettings` |
| Ajustes | Sin cambios funcionales; tema nuevo | `SettingsFragment` |
| Panel DAP / UAC2 / ADB / arranque | Sin cambios; solo tema | `dap/*` |
| Nuevo estilo de player Elyvaen | Implementado y seleccionado por defecto | `ui/screen/player/styles/elyvaenstyle/` |
| Menú del player (favorito, cola, letras, EQ, sleep timer, tag editor, web, compartir, borrar, detalles) | Conservado | menú `menu_now_playing` |

## Cambios de esta fase

1. **Tema** `Elyvaen` en Compose (`AuroraTheme.kt`) y en el tema View
   `Theme.Booming.Black` (`colors.xml`, `styles.xml`): superficies carbón,
   primary teal, secondary rosa, tertiary lavanda.
2. **Tipografía y formas** globales con Google Sans Flex y esquinas más
   redondeadas (`Theme.kt`).
3. **Home** nuevo: cabecera Elyvaen, tarjeta de estado DAP, “Tu música” con
   Play all/Aleatorio, acciones rápidas en tarjetas, secciones APlayer con
   carrusel de canciones, rejilla de álbumes, artistas y redescubrir.
4. **Bottom nav** flotante de cristal con indicador píldora.
5. **Player style Elyvaen**:
   - carátula dentro de un “sleeve” con marco;
   - título/artista alineados a la izquierda, formato y acciones;
   - seek teal, transporte con botón morphic, acción de cola/sonido/letras;
   - línea “Sigue …” con la próxima pista;
   - menú completo de acciones.
6. **Selección por defecto**: migración `dap_elyvaen_player_applied` fija
   `now_playing_screen=Elyvaen` en instalaciones existentes; las nuevas ya
   nacen con ese estilo.

## Capturas

- `screens/home.png` — Home con selector Local/Deezer/Descargas y mini player.
- `screens/home-foryou.png` — sección “Para ti” con carrusel de canciones.
- `screens/home-sueltas.png` — sección “Sueltas” para singles y pistas sueltas.
- `screens/sueltas.png` — vista completa de Sueltas con reproducir y aleatorio.
- `screens/link-resolver.png` — enlace de Deezer resuelto con song.link y coincidencias locales.
- `screens/metadata-health.png` — salud de biblioteca con incidencias y filtros.
- `screens/discord-webhook.png` — ajustes del webhook de Discord.
- `screens/nas-settings.png` — formulario NAS y Tailscale con credenciales cifradas.
- `screens/nas-browser.png` — navegador NAS con descarga por carpeta y resumen.
- `screens/nas-sync.png` — sincronización incremental con conflictos.
- `screens/nas-backup.png` — copia `.bmgbak` subida al NAS.
- `screens/media-server.png` — H3 con DLNA, WebDAV, control web y QR.
- `screens/audio-engine.png` — H4 con EQ paramétrico, crossfeed y convolución.
- `screens/sala.png` — H5 con visualizador, envío a TV y vídeo local.
- `screens/dap-mode.png` — H6 con batería, térmica y modo dedicado.
- `screens/dap-buttons.png` — H6 con el remapeo del botón de asistente.
- `screens/hub.png` — Elyvaen Hub con importación USB, Wi-Fi y almacenamiento.
- `screens/hub-wifi.png` — subida por Wi-Fi con QR y enlace temporal.
- `screens/hub-usb-import.png` — importación SAF completada con progreso y resumen.
- `screens/hub-storage.png` — volúmenes, biblioteca, importaciones y caché.
- `screens/player.png` — player Elyvaen con favorito, portada y próxima pista.
- `screens/library-songs.png` — biblioteca de canciones con filas tipo tarjeta.
- `screens/library-albums.png` — biblioteca de álbumes.
- `screens/library-genres.png` — cabecera editorial y rejilla de géneros.
- `screens/deezer-catalog.png` — catálogo Deezer con éxitos, álbumes y playlists.
- `screens/deezer-login.png` — login oficial de Deezer en WebView.
- `screens/deezer-library.png` — biblioteca de la cuenta: artistas favoritos.
- `screens/deezer-artist.png` — ficha de artista con favorito y descarga.
- `screens/deezer-offline.png` — pantalla Deezer sin red.
- `screens/deezer-connect.png` — diálogo con login oficial y alternativa ARL.
- `screens/downloads.png` — Descargas con historial persistente y reproducción.
- `screens/downloads-empty.png` — estado vacío de Descargas.
- `screens/stats.png` — estadísticas de escucha.
- `screens/settings.png` — ajustes.
- `screens/queue.png` — cola.
- `screens/lyrics.png` — letras.
- `screens/player-menu.png` — menú completo del player.
- `screens/style-picker.png` — selector de estilo con Elyvaen.
- `screens/dap-dashboard.png` — panel DAP/UAC2.
- `screens/kiosk.png` — kiosco real con la app fijada.
- `screens/hidden-content.png` — panel Contenido oculto con restaurar.
- `screens/muted-albums.png` — álbumes silenciados, visibles con la marca.
- `screens/muted-panel.png` — sección Álbumes silenciados del panel.

## Pruebas realizadas

- Build `:app:assembleGithubDebug` correcto.
- Instalación sobre la app existente conservando datos.
- Home, navegación, favoritos, play all, mini player, player, cola, letras,
  menú de acciones, ajustes y panel DAP abiertos sin crash.
- Estilo Elyvaen visible en Ajustes → Reproduciendo ahora → Estilo.
- UAC2/ADB no se tocaron en esta fase.

## Cómo probar

```bash
# dentro del entorno de build
./gradlew :app:assembleGithubDebug -PelyvaenNoSuffix=true
adb install -r -d app/build/outputs/apk/github/debug/BoomingMusic-*-github-arm64-v8a.apk
```

El flag `-PelyvaenNoSuffix` permite instalar el build de depuración sobre la
app existente (misma firma de depuración) sin perder datos. Sin el flag, el
paquete es `com.elyvaen.booming.debug`.

## Iteración 2 — rendimiento y barra inferior (2026-11-19)

Feedback de uso: carátulas lentas al pasar el carrusel, caída de FPS con
imágenes y mini-player oculto por la barra inferior.

Cambios:

- **90 Hz por defecto** en `MainActivity.applyPreferredDisplayMode()`
  (antes elegía 120 Hz). Si no hay 90, usa 60.
- **Paleta saltada en AppTheme**: el estilo Elyvaen usa colores del tema, así
  que ya no se convierte la carátula a bitmap ni se ejecuta PaletteProcessor
  en cada swipe. Se añadió una caché LRU por canción para esquemas Vibrant/Blur.
- **Carrusel plano**: el estilo Elyvaen usa un `ShapeableImageView` con borde,
  sin `MaterialCardView` ni sombra/elevación, y sin transformaciones caras.
- **Mini-player corregido**: la altura de la barra inferior se toma de la vista
  medida (`navigationView.height`) en lugar del dimen fijo; el mini-player y el
  padding de las listas quedan por encima de la barra.
- **Header de biblioteca**: título alineado a la izquierda, 76 dp y degradado
  teal sutil, más cerca de APlayer/Symphony.
- **APK release no minificado** (`ElyvaenBooming-latest.apk`) firmado con la
  misma clave que la app instalada. Se generó con `-PelyvaenNoMinify` porque
  R8 agota la memoria del contenedor; no es debuggable, así que debe ir más
  fluido que el build de depuración.

Pendiente de verificar en el dispositivo (se desconectó ADB durante el día):

- posición final del mini-player y padding de listas;
- FPS reales del carrusel con el APK release;
- capturas nuevas del header de biblioteca.

## Iteración 3 — biblioteca con identidad propia (2026-11-21)

- **Título de biblioteca editorial**: mayúsculas, tracking amplio, 76 dp y
  alineación a la izquierda (`TextAppearance.Elyvaen.LibraryTitle`).
- **Filas tipo tarjeta**: `item_list` y `item_song` usan
  `bg_library_row.xml` con superficie elevada, borde sutil, esquinas de 18 dp
  y estado `activated` para la selección múltiple. Se conserva el ripple.
- **Carátula de fila a 52 dp** para dar más presencia sin perder densidad.
- Encabezado con degradado teal sutil (`bg_library_header.xml`).

Esto cambia canciones, carpetas, playlists, búsqueda y listas de detalle de
álbum/artista, no solo el Home y el player.

## Verificado en dispositivo (2026-11-21)

- Home con fuente `Local · Deezer · Descargas`.
- Biblioteca de canciones como tarjetas redondeadas.
- Cabecera de géneros en mayúsculas, alineada a la izquierda y con degradado.
- **Mini-player corregido**: sus bounds terminan en `y=2200`, justo donde empieza
  la barra inferior; ya no se solapan.
- **90 Hz activos**: `dumpsys display` reporta `modeId 3` (90 Hz).
- Pantalla Deezer y diálogo ARL abiertos sin crash.
- Pantalla **Descargas** con estado vacío y botón Abrir Deezer.
- Pantalla **Estadísticas** con tiempo escuchado, reproducciones, pistas
  únicas, top canciones, top artistas, top álbumes e historial.
- Corregido el arranque: si la última página es una categoría que no está en
  la barra inferior (p. ej. Géneros), la app arranca en Home y la barra no deja
  una selección engañosa.
- El catálogo Deezer no se pudo validar porque el DAP estaba en **modo avión**
  y la Wi-Fi no estaba conectada; la pantalla mostró correctamente el error de
  conexión y el botón Reintentar.

## Pendiente / siguiente iteración

- Instalar `ElyvaenBooming-latest.apk` y medir frames con `dumpsys gfxinfo`.
- Verificar en dispositivo el header y las filas tipo tarjeta (no se pudo
  instalar porque ADB sigue desconectado).
- Revisar en el dispositivo la rejilla de álbumes con bibliotecas grandes.
- Pulir el header de biblioteca con tabs si se quiere acercar aún más a APlayer.
- Revisar el panel DAP en formato apaisado.
- Opcional: icono/splash Elyvaen y textos “Booming” residuales del About.
