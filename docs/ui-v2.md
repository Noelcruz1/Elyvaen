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

- `screens/home.png` — Home con “Para ti”, estado DAP y mini player.
- `screens/home-sections.png` — rejilla de álbumes, artistas y redescubrir.
- `screens/player.png` — player Elyvaen con favorito y próxima pista.
- `screens/library-songs.png` — biblioteca de canciones.
- `screens/library-albums.png` — biblioteca de álbumes.
- `screens/settings.png` — ajustes.
- `screens/queue.png` — cola.
- `screens/lyrics.png` — letras.
- `screens/player-menu.png` — menú completo del player.
- `screens/dap-dashboard.png` — panel DAP/UAC2.

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

## Pendiente / siguiente iteración

- Revisar en el dispositivo la rejilla de álbumes con bibliotecas grandes.
- Pulir el header de biblioteca (título alineado a la izquierda + tabs) si se
  quiere acercar aún más a APlayer.
- Revisar el panel DAP en formato apaisado.
- Opcional: icono/splash Elyvaen y textos “Booming” residuales del About.
- Medir frames reales a 90 Hz con `dumpsys gfxinfo`.
