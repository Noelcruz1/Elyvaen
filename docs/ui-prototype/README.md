# Elyvaen DAP Deck — prototipo visual

Prototipo HTML/CSS/JS autocontenido del concepto **A1: DAP Deck + Miku**.
No es la app final: sirve para validar estética, jerarquía e interacción antes
de implementarlo en Compose.

## Abrir

Abre `index.html` en un navegador. Para verlo a tamaño real:

```
index.html?full=1
```

## Interacciones

- **Swipe arriba** (o la pista BIBLIOTECA): biblioteca.
- **Swipe abajo** (o la pista DAC USB): pantalla DAC.
- **Rueda derecha**: arrastra o usa la rueda del ratón para el volumen.
- **Play/pause**: anima o detiene los carretes.
- **Dock inferior**: Cola, Inspector y Ajustes.
- **Seek**: clic en la cinta.
- La biblioteca y el DAC se cierran con el botón de volver.

## Parámetros útiles

- `?screen=library` / `?screen=dac`
- `?panel=queue` / `?panel=inspector` / `?panel=settings`
- `?still=1` — desactiva animaciones (capturas)
- `?full=1` — 1080×2460 sin escalar

## Diseño

- Miku teal `#39C5BB`, mint `#7FF7E8`, neon pink `#FF4F9A`.
- Fondo teal-negro, cristal, cassette, carretes, rueda de volumen.
- Sin bottom nav ni feed de tarjetas de Booming.
- Objetivo de rendimiento en la app real: 90 Hz, solo transform/opacity,
  carretes simplificados y sin blur dinámico.
