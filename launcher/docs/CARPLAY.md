# CarPlay: la identidad

**v0.74.0:** la versión y la huella están en `tools/carlink.properties` y el workflow `carlink.yml` las actualiza solo todos los días (verifica el APK nuevo con su `.sha256` y que traiga `assets/offline-mfi/`), así que un APK retirado no vuelve a dejar los builds sin CarPlay.

**Actualización 2026-11-04, v0.70.1 (AUD-068):** el APK de CarLink 1.0.7 ya no se puede bajar (HTTP 404) y desde el build 86 los APK salieron sin CarPlay. Ahora la CI baja **CarLink 1.0.9**
(`https://github.com/F-e-n-y-x/CarLink/releases/download/v1.0.9/CarLink-1.0.9.apk`) y verifica la huella que publica su release:
`d4737a02405e7e2fd5152a6e7692f8069270e3bf94471f4bb540f28e319dac46`. Si CarLink borra también esa versión, usa su última
release con la huella de su archivo `.sha256` y deja un aviso para fijar la versión nueva en `build.yml` (`CARLINK_VERSION`,
`CARLINK_APK_SHA256`). El log dice "CarPlay: identidad desde el APK de CarLink 1.0.9 (huella verificada)". Los secretos de abajo
siguen siendo la opción si algún día CarLink deja de publicar el APK.

**Antes (v0.58.0):** al compilar, la CI baja el APK público de CarLink 1.0.7
(`https://github.com/F-e-n-y-x/CarLink/releases/download/v1.0.7/CarLink-1.0.7.apk`), verifica que
su huella SHA-256 sea `16e8b98436a5c5f355aa4b49086e28d41bee9f9f874174658cafb682fd503e77` (la que
publica CarLink) y saca de ahí los dos archivos. El log de la CI dice "CarPlay: identidad desde el
APK de CarLink 1.0.7 (huella verificada)". Lo de abajo (secretos) es opcional, por si algún día se
quiere usar otra identidad.

## Opcional: cargarla a mano

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González · Licencia AGPL-3.0

CarPlay necesita dos archivos de "identidad de accesorio": `identity.pk8` y `certificate.p7b`.
No son de Apple: salen del firmware de un adaptador Carlinkit y los publican DiPlay y CarLink
dentro de su APK. **Nunca se suben al repositorio** (están bloqueados en `.gitignore`): se cargan
como **GitHub Secrets** y la CI los mete en el APK al compilar (ADR-0012).

Riesgo aceptado (ADR-0012): no es una identidad oficial de Apple; `identity.pk8` es la clave privada
de un adaptador Carlinkit, sacada de su firmware por terceros. Una actualización de iOS puede dejar
de aceptarla. **Cada APK la lleva** (desde v0.58.0 la CI la agrega siempre): ningún APK ni release
se comparte ni se publica (AUD-067).

## 1. Sacar los dos archivos (en la PC)

1. Conseguí el APK de CarLink 1.0.7 que ya tenías (descargas del celular o de la PC). Si solo está
   instalado en la radio, con Bugjaeger: `pm path <paquete de CarLink>` dice dónde está y se puede
   copiar a `/sdcard/` (`cp <ruta>/base.apk /sdcard/CarLink.apk`) para pasarlo a la PC.
2. Copialo y cambiale la extensión de `.apk` a `.zip`.
3. Abrí el `.zip` y entrá a `assets/offline-mfi/`. Copiá `identity.pk8` y `certificate.p7b` a
   una carpeta, por ejemplo `C:\carplay`.

## 2. Pasarlos a texto (base64)

En Windows, abrí **PowerShell** en esa carpeta y corré (cada línea copia el texto al portapapeles):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\carplay\identity.pk8")) | Set-Clipboard
```

Pegalo en el secreto `CARPLAY_IDENTITY_PK8` (paso 3). Después:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\carplay\certificate.p7b")) | Set-Clipboard
```

y pegalo en `CARPLAY_CERTIFICATE_P7B`.

## 3. Cargarlos en GitHub

En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**.

| Nombre | Valor |
|---|---|
| `CARPLAY_IDENTITY_PK8` | El texto de `identity.pk8` |
| `CARPLAY_CERTIFICATE_P7B` | El texto de `certificate.p7b` |

Después borrá la carpeta `C:\carplay`.

## 4. Comprobar

Pedile a Claude una compilación nueva. En el log de la CI tiene que aparecer
"CarPlay: identidad cargada desde GitHub Secrets". Instalá ese APK y enchufá el iPhone por USB.

Para quitarla: borrá los dos secretos y compilá de nuevo.
