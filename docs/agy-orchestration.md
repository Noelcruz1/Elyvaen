# AGY: Gemini/Claude como herramientas de OpenCode

`agy` es el CLI de Antigravity, ejecutándose en el contenedor `elyvaen-agy`.
OpenCode lo invoca en modo headless (`agy --print`) para explorar UI,
criticar diseño y generar código/sistemas de diseño. No sustituye al agente
local: yo dirijo los prompts, reviso, integro y pruebo.

## Modelos

| ID | Uso |
|---|---|
| `gemini-3.8-flash-high` | Conceptos visuales, variantes, HTML/SVG, multimodal |
| `gemini-3.8-flash-medium` | Iteraciones rápidas |
| `gemini-3.8-flash-low` | Pruebas y prompts cortos |
| `claude-opus-4-6-thinking` | Crítica UX profunda, arquitectura de interacción |
| `claude-sonnet-4-6` | Sistema de diseño, Compose, código |
| `gpt-oss-120b-medium` | Segunda opinión barata |

## Flujo de trabajo

1. Yo escribo el brief y los criterios de aceptación.
2. Gemini 3.8 Flash High genera 6–10 conceptos divergentes.
3. Claude Opus 4.6 critica, elige y afina el modelo de interacción.
4. Claude Sonnet 4.6 convierte el elegido en sistema de diseño + esqueleto
   Compose.
5. Yo integro en el repo, compilo, capturo en el dispositivo e itero.

## Comandos

```sh
agy status
agy models
agy ask --model gemini-3.8-flash-high "prompt"
agy ask --model claude-opus-4-6-thinking --out /workspace/Android/agy-work/critica.md "prompt"
agy ask --model claude-sonnet-4-6 --tools "escribe el diseño en agy-work"
agy login
```

- El repo se monta en `/workspace/Elyvaen` **solo lectura**.
- Las escrituras van a `/workspace/Android/agy-work` (`/workspace/agy-work`
  dentro del contenedor) y a `/workspace/Android/Mockups-Elyvaen`.
- `--tools` usa `--dangerously-skip-permissions --mode accept-edits`; úsalo
  solo con el workdir dedicado.
- Cada llamada consume cuota de Antigravity/AI PRO. Para volumen, Flash;
  Opus solo en decisiones clave.

## Autenticación

El token está ligado al contenedor. Tras recrear `elyvaen-agy` hay que hacer
login una vez por ttyd (`http://192.168.100.25:7681`, usuario `agy`,
password en `/workspace/Android/tools/antigravity/.env`). Después persiste en
`tools/antigravity/home`.

## Instalación

- Proyecto: `/workspace/Android/tools/antigravity`
- Contenedor: `elyvaen-agy`
- Imagen: `elyvaen-agy:latest`
- Respaldo: instalación antigua en
  `/srv/dev-disk-by-uuid-a2e4b2e5-95cf-40fb-9af4-0090fb851dbf/antigravity`,
  contenedor `antigravity` parado.
