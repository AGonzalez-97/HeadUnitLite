package com.axel.onixlauncher

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Pantalla completa: las barras del sistema (en el head unit, la de arriba con atrás / inicio /
 * apps) se esconden al abrir, cada vez que la app vuelve al frente y, si aparecen, a los 3 s.
 * Para verlas, deslizar desde el borde.
 */
class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val hideAgain = Runnable { hideSystemBars() }

    override fun onCreate(savedInstanceState: Bundle?) {
        Tls.install()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { flags ->
            // Si las barras volvieron a aparecer, se esconden de nuevo a los 3 s
            if (flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 || flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
                handler.removeCallbacks(hideAgain)
                handler.postDelayed(hideAgain, 3000)
            }
        }
        NwdSignals.register(this)
        AutoResume.register(this)
        Intro.register(this)
        NetWatch.register(this)
        RadioIll.register(this)
        setContent { LauncherApp() }
        handleSpotifyRedirect(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSpotifyRedirect(intent)
    }

    /** Vuelta del permiso de Spotify (onixlauncher://spotify?code=…): se cambia por tokens en segundo plano. */
    private fun handleSpotifyRedirect(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "onixlauncher" || uri.host != "spotify") return
        val app = applicationContext
        Thread { SpotifyAuth.handleRedirect(app, uri) }.start()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    /**
     * Anota cada tecla que llega (botones del volante, de la radio, del control) para el informe,
     * sin cambiar lo que hace (v0.53.0). Con eso se sabe qué código manda cada botón del volante.
     * v0.64.1 (lint RestrictedApi): con onKeyDown en vez de dispatchKeyEvent, que en ComponentActivity
     * de AndroidX es interno; anota las teclas que ninguna pantalla usó (las de la radio y de música).
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            NwdSignals.add(NwdSignals.keyLine(keyCode, android.view.KeyEvent.keyCodeToString(keyCode), event.scanCode, event.source))
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideAgain)
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT < 30) {
            // Android 7.1 del head unit: banderas clásicas de pantalla completa "pegajosa"
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }
}
