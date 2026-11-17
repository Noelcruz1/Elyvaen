# Investigación UI/UX — reproductores Android (2026-11-17)

Objetivo: mantener **todas** las funciones de Booming y cambiar la UI/UX con
el mínimo esfuerzo. Booming está construido sobre **Retro Music Player**;
por eso Retro no aporta una UI nueva.

## Candidatos

| Proyecto | UI | Funciones | Licencia | Esfuerzo |
|---|---|---|---|---|
| **APlayer** (rRemix) | Material3/Compose, tabs configurables, lista/grid, player, letras | Muy alto: canciones/álbumes/artistas/géneros/folders/playlists/WebDAV, letras locales/online palabra a palabra, tag editor, EQ, velocidad, sleep timer, widgets, lockscreen | GPL-3.0 | **Bajo-medio** |
| **Symphony** (zyrouge) | Oscuro, elegante, Compose; Now Playing, Songs, For You, Queue | Medio: offline, folders, playlists, cola; sin EQ/tag editor destacados | AGPL-3.0 | Alto (faltan funciones; licencia stronger) |
| **OuterTune** | Material3 oscuro, home rico, player grande, cola, letras | Alto, pero YouTube-first; local + YT, colas múltiples, lyrics, efectos | GPL-3.0 | Medio-alto (quitar YT, portar DAP) |
| **Auxio** | Material limpio, racional, lista/player | Medio: Media3, ReplayGain, folders, playlists, Auto, widgets; EQ externo, sin letras integradas | GPL-3.0 | Medio |
| **Gramophone** | Material3 claro, minimal, lista/player | Medio-bajo: local, folders, playlists, letras; sin EQ avanzado | GPL-3.0 | Medio |
| **Retro Music** | Material You, rica | Muy alto (base de Booming) | GPL-3.0 | Ninguno (misma UI) |
| **Zen** | Material You oscuro, limpio | Bajo-medio; proyecto pequeño | MIT | Medio |
| **Namida** | Flutter, muy personalizable | Muy alto | — | Alto (no Compose) |

## Conclusión

- **APlayer** es el mejor equilibrio función/UI/esfuerzo: es el único con
  paridad cercana a Booming (folders, playlists, letras, tag editor, EQ,
  widgets) y ya está en Compose. Su UI es convencional, pero tiene temas
  claro/oscuro/AMOLED y colores personalizables; se puede reskinar a
  teal/pink sin tocar el motor.
- **Symphony** tiene la UI más bonita, pero es AGPL y más ligera de funciones.
  Sirve como **referencia visual**, no como base de código.
- **OuterTune** es rico y Material3, pero está pensado para YouTube Music;
  adaptarlo a un DAP local + UAC2 es más trabajo.
- **Auxio/Gramophone** son demasiado minimalistas para mantener Booming.
- **Retro** es la base de Booming: no cambia nada.

## Estrategia recomendada (mínimo esfuerzo)

1. **No forkear** otro reproductor. Mantener Booming: motor, datos,
   Media3, UAC2, ADB, boot receiver, ajustes y funciones.
2. Rediseñar la **capa UI** tomando de APlayer la estructura funcional
   (tabs configurables, lista/grid, mini-player, player con letras, EQ,
   ajustes) y de Symphony la **estética oscura/elegante**.
3. Aplicar identidad Elyvaen/Miku: teal `#39C5BB`, pink `#FF4F9A`,
   glass, tipografía grande, detalles cassette **sin quitar densidad**.
4. Ahora Playing puede tener el deck/rueda como pieza visual, pero el resto
   de pantallas conserva toda la información y funciones.
5. Prototipo v2 con 4–5 pantallas **llenas**: Home, Biblioteca, Player,
   Cola/Letras y DAC/Ajustes.

## Referencias

- https://github.com/rRemix/APlayer
- https://github.com/zyrouge/symphony
- https://github.com/OuterTune/OuterTune
- https://github.com/OxygenCobalt/Auxio
- https://github.com/FoedusProgramme/Gramophone
- https://github.com/RetroMusicPlayer/RetroMusicPlayer
- https://github.com/pakka-papad/Zen

Capturas comparativas (fuera del repo):
`/workspace/Android/Mockups-Elyvaen/sheet1.png`, `sheet2.png`,
`aplayer-dark.png`.
