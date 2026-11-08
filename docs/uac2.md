# UAC2 backend

## Estado del kernel

- Rama local: `elyvaen-uac2` del source `Mxiier/kernel_motorola_cypfq`.
- Commit: `17dbfeb76 config: enable UAC2 USB audio gadget`.
- Defconfig: `CONFIG_USB_CONFIGFS_F_UAC2=y`, `CONFIG_USB_F_UAC2=y`, `CONFIG_USB_U_AUDIO=y`.
- El driver quedó **built-in** en `Image`; no se generó `usb_f_uac2.ko`.
- Build: `KERNEL_BUILD=build-kernel-uac2 JOBS=3 ./scripts/build-mxiier-kernel.sh`.
- Vermagic: `5.4.259-perf+-cypfq-test-g17dbfeb76641 SMP preempt mod_unload aarch64`.
- No se ha flasheado; el dispositivo sigue en el kernel/DAP validado anterior.

Parche reproducible: `patches/kernel/0001-config-enable-UAC2.patch`.

## Siguiente fase backend

1. Exponer la función `uac2` en el gadget USB (`/config/usb_gadget/g1`) y enlazarla al UDC.
2. Implementar el daemon que capture el PCM UAC2 que llega de la PC y lo reproduzca en el DAC interno.
3. Probar enumeración en Linux/Windows como tarjeta de sonido USB.
4. Validar 24-bit/192 kHz y latencia.
5. Añadir un modo conmutable: DAP normal / DAC USB, sin perder ADB de forma permanente.

El kernel ya tiene la parte de audio USB; el trabajo restante es de configuración y userspace.
