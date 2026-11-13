# Ajustes de la radio NWD (reversibles)

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González

Equipo: Allwinner T3, placa `k2001-nwd`, Android 7.1.1. La consola de Bugjaeger entra como **root**
(`id` → `uid=0(root)`, 31/10/2026) y los archivos de `/nwdappconfig/app/` tienen permisos de escritura
para todos (`rwxrwxrwx`). Estos cambios no tocan el firmware ni el microcontrolador: son listas de
texto que la radio lee al arrancar. Siempre se hace copia antes.

## Ya aplicado por el autor (31/10/2026)

- Animaciones a 0,5x (`settings put global … 0.5`).
- Deshabilitados con `pm disable-user --user 0`: `com.google.android.googlequicksearchbox`,
  `com.nwd.appmall`, `com.nwd.check.appver`, `com.nwd.guidebookskin`, `com.nwd.whole.machine`,
  `com.nwd.complete.test`, `com.nwd.android.video.ui`, `com.nwd.android.music.ui`.
  Para volver atrás: `pm enable <paquete>`. Ojo: `com.nwd.android.music.ui` es el reproductor de
  fábrica (USB); si hace falta para música por USB, se vuelve a habilitar.

## Listas de procesos (hallazgo HU-014)

| Archivo | Qué hace | Spotify / Onix (31/10) |
|---|---|---|
| `BackgroundManager.ini` | `white:<paquete>` = no se cierra cuando falta memoria | No estaban |
| `OneClearConfig.xml` | `ProcessItem PackName="powernotkill.<paquete>"` = no se cierra al apagar el contacto; `ForceSleepKill` = se cierra sí o sí | No estaban (el launcher de fábrica sí: `powernotkill.com.android.launcher`) |
| `NaviSound.ini` | Apps cuya voz se mezcla sobre la música | Onix no estaba |

Hipótesis: por eso Spotify (y el launcher) se cierran al apagar el auto y hay que abrir Spotify de
nuevo. Se confirma después del cambio: apagar y prender el contacto y ver si Spotify sigue abierto
(`pidof com.spotify.music` antes y después).

## Procedimiento (Bugjaeger, como root)

1. Copia de seguridad en `/sdcard/onix_backup/`.
2. Agregar `com.spotify.music` y `com.axel.onixlauncher` a las tres listas.
3. Reiniciar la radio (la radio lee las listas al arrancar).

Para volver atrás: copiar los archivos de `/sdcard/onix_backup/` a `/nwdappconfig/app/` y reiniciar.

## Aplicado por el autor (04 y 05/11/2026)

1. Copia: `/sdcard/onix_backup/` con `BackgroundManager.ini` (2638 B), `NaviSound.ini` (3438 B) y `OneClearConfig.xml` (12157 B), 04/11 15:08.
2. `BackgroundManager.ini`: `white:com.spotify.music` y `white:com.axel.onixlauncher` al final (líneas 98 y 99).
3. `OneClearConfig.xml`: `<ProcessItem PackName="powernotkill.com.spotify.music"/>` y `…powernotkill.com.axel.onixlauncher"/>` antes del launcher de fábrica (línea 159).
4. `NaviSound.ini` (formato `paquete   true   appset`): `com.axel.onixlauncher   true   appset` al final.
5. Dueño `system:system` y permisos `rwxrwxrwx` como estaban; reinicio de la radio.

Comandos usados (de a uno, en Bugjaeger):

```
printf '\nwhite:com.spotify.music\nwhite:com.axel.onixlauncher\n' >> /nwdappconfig/app/BackgroundManager.ini
sed -i 's#<ProcessItem PackName="powernotkill.com.android.launcher">#<ProcessItem PackName="powernotkill.com.spotify.music"/><ProcessItem PackName="powernotkill.com.axel.onixlauncher"/><ProcessItem PackName="powernotkill.com.android.launcher">#' /nwdappconfig/app/OneClearConfig.xml
f=/nwdappconfig/app/NaviSound.ini; [ -n "$(tail -c 1 $f)" ] && echo >> $f; printf 'com.axel.onixlauncher                       true    appset\n' >> $f
chown system:system /nwdappconfig/app/*.ini /nwdappconfig/app/OneClearConfig.xml && chmod 777 /nwdappconfig/app/*.ini /nwdappconfig/app/OneClearConfig.xml
```

**Spotify en `NaviSound.ini` (12/11):** al despertar, la radio cierra toda app que reproduce audio, salvo las de esta lista (regla de `android.os.NwdAudioService`; registro: `fmState = 1, kill play process in background`). Copia en `/sdcard/NaviSound_antes.ini`, línea agregada y reinicio:

```
f=/nwdappconfig/app/NaviSound.ini; cp $f /sdcard/NaviSound_antes.ini; printf 'com.spotify.music                           true    appset\n' >> $f
```

Resultado: `pidof com.spotify.music` = 9938 antes y después de apagar y prender el contacto; suena normal. Desde v0.81.16 la app lee esta lista y no pasa Spotify al frente si figura. Para volver atrás: `cp /sdcard/NaviSound_antes.ini /nwdappconfig/app/NaviSound.ini` y reiniciar.

**Resultado (05/11 13:20):** después de apagar y prender el contacto, `pidof com.spotify.music` = 12360 y `pidof com.axel.onixlauncher` = 5779: los dos siguieron abiertos (HU-014 resuelto). Falta ver con el uso si Spotify retoma más rápido.
