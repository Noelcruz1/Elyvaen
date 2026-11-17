# Elyvaen UI — rediseño DAP-first

## Estado actual

La app sigue siendo la UI de **Booming** (Material3): home con tarjetas,
bottom sheet "Panel DAP", pantallas de biblioteca/reproductor heredadas.
Solo la capa UAC2 es propia.

Objetivo: construir una **capa UI nueva** ("Elyvaen OS") sobre el motor
actual. Se conservan `playback`, `data`, `core`, Media3, Room, Koin y todo
el backend UAC2. No es un tema ni un skin: cambia la navegación, la
jerarquía y las pantallas principales.

## Referencias

- **Sony WM1ZM2**: negro OLED, carátula protagonista, widget de reproducción
  en el home, volumen con estética analógica, tipografía grande, pocos
  elementos por pantalla.
- **HiBy RS8 / HiBy OS**: bottom nav, tarjetas, MSEB, VU meters, acentos
  vivos, densidad media.
- **FiiO M15/M17**: home en cuadrícula, VU meters, mandos grandes, azul y
  naranja, información técnica visible.
- **JadeAudio**: versión simplificada de FiiO, más limpia y directa.
- **Astell&Kern SP3000**: lujo minimalista, carátula grande, tipografía fina,
  transiciones cuidadas, semi-Android.
- **Shanling MTouch**: controles circulares, gestos, foco en el álbum.
- **iBasso Mango OS**: listas simples y rápidas, biblioteca como centro.

No se copia ninguna: se toman las ideas de jerarquía, foco en carátula,
controles grandes y lectura rápida.

## Principios

1. **La música primero**: carátula y título dominan; cero ruido visual.
2. **Una mano**: acciones principales abajo; objetivos táctiles ≥48 dp.
3. **Negro de verdad**: OLED, alto contraste, sin grises lavados.
4. **Un acento**: color extraído de la carátula con fallback teal Elyvaen.
5. **Jerarquía tipográfica**: display 34–40 sp, título 24–28 sp, metadata
   13–15 sp; numerales tabulares para tiempos y tasas.
6. **Sin bottom sheets anidados** para el modo DAC: es una pantalla de
   primer nivel.
7. **Feedback físico**: háptica, animaciones cortas (180–240 ms),
   shared-element de carátula.
8. **Información técnica sin ensuciar**: tasa/formato en badges discretos;
   el detalle vive en DAC USB.

## Navegación

Bottom nav de 4 destinos + reproductor a pantalla completa:

1. **Inicio**: hero "Ahora suena", modos local/DAC, recientes, accesos de
   biblioteca, estado del sistema discreto.
2. **Biblioteca**: canciones, álbumes, artistas, listas, carpetas; búsqueda,
   orden, scroll rápido, lista/grid.
3. **DAC USB**: pantalla dedicada con estado, tasa, volumen del host,
   calidad, ADB, auto-arranque y diagnóstico.
4. **Ajustes**: audio (Pure Audio, EQ), reproducción, pantalla, USB DAC,
   sistema.

**Now Playing** se abre desde el hero/mini-player, a pantalla completa.
La cola es un sheet, no una pantalla.

## Pantallas

### Now Playing

- Fondo: carátula desenfocada + gradiente; overlay negro 60–70 %.
- Carátula grande (~70 % del ancho), sombra suave, esquinas 24 dp.
- Título 28–34 sp, artista 16 sp, álbum 13 sp.
- Badges: `24/192`, `PCM`, `Nativo`, `USB DAC`/`Local`.
- Seek con tiempos; VU/spectrum opcional (desactivable).
- Controles: shuffle, anterior, play/pause 72 dp, siguiente, repeat.
- Fila inferior: volumen, salida, cola, favorito.
- Animaciones: shared element de carátula, morph de play/pause, barras EQ.

### Inicio

- Hero "Ahora suena" con carátula, título, play/pause y badges.
- Fila de modos `DAP local` / `DAC USB` con estado real.
- "Recientes" horizontal de álbumes.
- Accesos grandes: Canciones, Álbumes, Artistas, Listas.
- Estado del sistema en una fila discreta: batería, almacenamiento, Wi-Fi,
  USB. Sin tarjetas gigantes de batería/almacenamiento.

### DAC USB

- Cabecera grande: `DAC USB` + estado (`PC reproduciendo`,
  `Esperando audio`, `Apagado`, `Error`).
- Tasa enorme: `192.0 kHz`, `24-bit`, `Nativo`/`48k`.
- Selector `DAP local` / `DAC USB`.
- Volumen del host: dB + mute; nota "lo controla el PC".
- Calidad: segmented `Nativo` / `48 kHz estable`.
- Auto-arranque; ADB: `Desactivado automáticamente` o aviso.
- Diagnóstico expandible: Windows no lo detecta → ADB, USB 2.0, 24/192,
  reinstalar dispositivo.
- Botón "Copiar diagnóstico" (opcional).

### Biblioteca

- Header con título y búsqueda; chips de pestañas.
- Canciones: número, carátula 48 dp, título, artista, duración, badge de
  formato; filas 64–72 dp.
- Álbumes: grid de 2 columnas con carátula grande.
- Artistas: lista con imagen circular.
- Fast scroll, selector de orden, modo lista/grid.
- Sin cards Material3 genéricas.

## Sistema visual

- **Colores**: fondo `#07080A`, superficie `#101216`, elevada `#171A20`,
  texto `#F5F7FA`, secundario `#9AA3AD`, acento teal `#4DE0C8`, error
  `#FF6B6B`.
- **Acento adaptativo**: Palette de la carátula; fallback teal.
- **Tipografía**: Manrope o Inter (o system sans), numerales tabulares.
- **Formas**: cards 24 dp, chips 999 dp, thumbs 16 dp.
- **Iconos**: line icons 24 dp, grosor 1.8; play/pause filled.
- **Elevación**: sombras suaves, sin bordes duros; glass solo en overlays.
- **Animación**: emphasized decelerate, 180–240 ms; háptica en play/seek.

## Fases

0. Moodboard y 4 mockups (Now Playing, Inicio, Biblioteca, DAC USB).
1. Shell + tema + bottom nav + Now Playing.
2. Inicio + mini-player + cola.
3. Biblioteca (canciones/álbumes/artistas/listas).
4. DAC USB + Ajustes de audio.
5. Pulido, háptica, transiciones y QA.

## Técnico

- Nuevo paquete `ui/elyvaen`: `ElyvaenTheme`, `ElyvaenNavHost`,
  `NowPlayingScreen`, `HomeScreen`, `LibraryScreen`, `DacScreen`,
  `SettingsScreen`.
- Reutilizar viewmodels/repositorios; no tocar `playback`, `data` ni UAC2.
- Feature flag para alternar la UI vieja durante la migración.
- Empezar por Now Playing y el shell; migrar el resto pantalla a pantalla.

## Dirección elegida: HiBy M500 x Hatsune Miku (v2)

Referencia: HiBy Digital M500 x Hatsune Miku. Se toma el lenguaje visual,
no la dotación multimedia del M500 (4G, cámara, FM, altavoces, micrófonos,
launcher Android). La app será solo reproductor + DAC USB.

### Paleta

- **Miku teal** `#39C5BB` (acento principal, sliders, iconos).
- **Miku mint** `#7FF7E8` (detalles, textos secundarios).
- **Neon pink** `#FF4F9A` (estado activo, play, power, badges).
- **Deep ink** `#061012` (fondo OLED).
- **Glass teal** `#0C1F21` / `#123033` (superficies).
- **Blanco** `#F4FFFD`, secundario `#8FB8B5`.

### Motivos

- Teal + magenta como la carcasa Miku y el botón power rosa.
- Cristal/acrylic, esquinas redondeadas, pills y sliders con pulgar teal.
- Aire retro-cassette: carrete/tape en el seek, botones táctiles, VU meter.
- Avatar de cantante virtual en Inicio (placeholder abstracto; se puede
  sustituir por arte de Miku proporcionado por el usuario).
- Fondo con ondas/geometría "sea breeze" muy sutiles.
- Animaciones: carretes girando al reproducir, pulso del avatar, háptica.
- Solo funciones de reproductor: Inicio, Biblioteca, DAC USB, Ajustes.

### Mockups

`docs/ui-mockups/v2-miku/`:
- `01-now-playing.png`
- `02-home.png`
- `03-library.png`
- `04-usb-dac.png`

Los SVG editables están en `v2-miku/svg/`.
