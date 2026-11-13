# ADR-0015 - Client ID de Spotify desde un secreto de GitHub

**Estado:** Aceptada · **Fecha:** 2026-11-06 · **Versión:** v0.79.6 · **Autor:** Axel Hernán González

## Contexto

- La conexión con Spotify usa PKCE (ADR-0009): no hay "client secret". El Client ID va a la vista en el enlace de permiso (`accounts.spotify.com/authorize?...&client_id=...`), así que no es un secreto de verdad.
- Hasta v0.79.5 el autor tenía que pegar el Client ID a mano en la pantalla de Spotify de la radio, y volver a pegarlo si la app se reinstalaba o se borraban sus datos.
- La clave de TomTom y el canal de ntfy ya entran al compilar desde GitHub Secrets (ADR-0014, v0.76.0), y las releases son privadas (AUD-067).

## Decisión

1. El Client ID entra a `BuildConfig.SPOTIFY_CLIENT_ID` al compilar, desde el GitHub Secret `SPOTIFY_CLIENT_ID`, con el mismo mecanismo que `TOMTOM_API_KEY` y `NTFY_TOPIC`. Al compilar se limpia a letras y números, para que ningún valor pueda romper el código generado. La CI nunca imprime el valor.
2. Es opcional. Sin el secreto el campo queda vacío y la pantalla pide pegar el Client ID, como antes.
3. Si hay uno en la app y otro pegado a mano, vale el de la app (`SpotifyAuth.pickClientId`): un valor viejo escrito a mano no tapa al nuevo.
4. Con el de la app presente, la pantalla de Spotify no muestra el campo: solo "Conectar con Spotify".
5. El valor no se escribe en el repositorio ni en la documentación.

## Consecuencias

- Una cosa menos para hacer a mano en la radio. El autor lo carga una sola vez en GitHub (`gh secret set SPOTIFY_CLIENT_ID`).
- El APK lleva el Client ID. Es público por diseño y el APK ya es privado; cambia la superficie de AUD-067 en nada.
- Si el secreto se carga después de una compilación, hay que volver a compilar para que entre.
- La app de Spotify del autor (developer.spotify.com) tiene que tener su cuenta en la lista de usuarios y el Redirect URI `onixlauncher://spotify`.
- Reemplaza la parte de ADR-0009 que decía que el autor pega el Client ID en el equipo.
