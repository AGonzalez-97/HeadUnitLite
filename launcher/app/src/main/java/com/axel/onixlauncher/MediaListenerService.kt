package com.axel.onixlauncher

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío: solo existe para que el usuario pueda darle "acceso a notificaciones"
 * a la app, que es el permiso que Android exige para leer las sesiones de música activas.
 */
class MediaListenerService : NotificationListenerService()
