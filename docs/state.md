# Estado del proyecto (checkpoint)

Fecha: 2026-11-08. Regla vigente: **no flashear nada hasta que el backend esté terminado y aprobado**.

## Repositorio

- Local: `/workspace/Android/Elyvaen`
- GitHub: `Noelcruz1/Elyvaen` (público), `main` en `854bff4` antes de este checkpoint.
- Deploy key: `build-kernel/elyvaen_deploy_key` (ignorada por git).
- Commits recientes con mensajes individuales:
  - `4c7bb42` chore: ignore local build and test artifacts
  - `f48a7db` feat(scripts): add bootloader-compatible sparse image converter
  - `0057e58` test(kernel): add isolated vendor and module super builder
  - `22e6346` fix(build): use 256 MiB sparse chunks for super images
  - `099e658` feat(system): make Elyvaen DAP the only home screen
  - `ce7b964` docs(audio): record validated 24-bit 192 kHz configuration
  - `f07f169` feat(kernel): add UAC2 defconfig patch
  - `854bff4` docs(kernel): record UAC2 build and next backend steps

## Dispositivo ahora

- Recuperado al DAP validado tras el incidente UAC2.
- Kernel `5.4.259-perf+-cypfq-test-g368d7ad093dd` (build sin UAC2, el validado).
- Slot A; `boot_b` = TWRP; no se ha tocado.
- Home: `com.mardous.booming/.MainActivity` desde `/product/priv-app/BoomingMusic/BoomingMusic.apk`.
- Táctil `CSOT_7807S`, audio HAL OK; config de audio completa Pure/LOHIFI/24-bit/192k.
- Sin Magisk en el boot actual; los módulos en `/data` no están activos.
- Datos conservados; la app puede estar en onboarding porque se quitó su actualización de `/data`.

## Artefactos clave

- Kernel validado: `build-kernel/` (`Image`, 58 módulos, DTB/DTBO, `boot_test.img`).
- Kernel UAC2 compilado: `build-kernel-uac2/` (`Image`, 58 módulos; UAC2 built-in; vermagic `...-g17dbfeb76641`).
- Patch UAC2: `patches/kernel/0001-config-enable-UAC2.patch`.
- Super DAP con audio completo validado: `build-audio-tuning/super.sparse.img`.
- Super DAP base stock audio: `build-dap-test/super.sparse.img`.
- Super de prueba UAC2 (NO flasheado): `build-uac2-test/super.sparse.img`.
- `boot_uac2_magisk.img` se perdió por el `rm -rf` del script y debe regenerarse antes de cualquier prueba UAC2.
- Backups/rollback: `/workspace/Android/cypfq_backup` (`super_ab_safe.img`, `current_boot_a.img`, `current_vendor_boot_a.img`, `current_dtbo_a.img`, TWRP `boot_b`).

## Lecciones

- El audio viejo no era problema del kernel: la config segura completa (effects/policy/mixer/platform) funciona con el kernel 5.4.259.
- El formato sparse de `img2simg` no lo acepta el bootloader; usar `scripts/raw-to-sparse.py` (chunks 256 MiB).
- El servicio init horneado no se ejecuta; el auto-home se resolvió eliminando `Launcher3QuickStep`, dejando la app DAP como único home.
- No flashear un `super` de prueba sin su `boot` correspondiente; causó el incidente de no enumeración USB.
- El teléfono se recupera con fastboot + `super_ab_safe`/boot validados.

## Cómo continuar

1. Trabajar solo offline/build/commits.
2. Completar backend: kernel release, UAC2 userspace, latencia/performance, pruebas estáticas.
3. Solo al final: un único flash de la ROM terminada, con rollback listo.
