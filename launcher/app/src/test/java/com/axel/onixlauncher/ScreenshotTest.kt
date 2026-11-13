package com.axel.onixlauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Renderiza las pantallas reales (el mismo código de la app) y guarda las capturas
 * en app/build/screenshots. Clima y OBD2 con valores de ejemplo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1024dp-h600dp-land-mdpi")
class ScreenshotTest {

    @Before
    fun sinAnimaciones() {
        RenderFlags.animate = false
        Festive.preview.value = null
        RadioState.current.value = null
        RadioState.playing.value = false
        RadioState.station.value = null
    }

    private fun at(h: Int, m: Int): Calendar =
        Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 25, h, m, 0) }

    private val sampleObd = ObdData(coolantC = 89, fuelPct = 64, volts = 14.2f, rpm = 1850, speedKmh = 62, kmPerLiter = 16.4f)

    private val noCodes = ObdCodes(mil = false, stored = emptyList(), pending = emptyList(), readAt = 0L)

    private val forecast = listOf(
        DayForecast("LUN", 61, 19, 12),
        DayForecast("MAR", 0, 25, 13),
        DayForecast("MIÉ", 2, 27, 15),
        DayForecast("JUE", 95, 22, 16),
        DayForecast("VIE", 80, 20, 14)
    )

    private fun weather(temp: Int, code: Int, day: Boolean) = Weather(
        tempC = temp, code = code, feelsC = temp - 1, humidity = 58, windKmh = 12,
        maxC = temp + 3, minC = temp - 8, rainProb = if (code in 51..99) 80 else 10,
        isDay = day, days = forecast
    )

    private fun shot(name: String, hour: Int, ambient: Ambient, w: Weather) {
        captureRoboImage("build/screenshots/$name.png") {
            HomeScreen(
                now = at(hour, 15),
                ambient = ambient,
                lightsOn = ambient != Ambient.DIA,
                media = MediaResult(true, null),
                speed = 62,
                weather = w,
                obd = sampleObd,
                place = "San Miguel de Tucumán",
                obdCodes = noCodes,
                onDiag = {}
            )
        }
    }

    @Test
    fun dia() = shot("1_dia", 12, Ambient.DIA, weather(24, 0, true))

    @Test
    fun atardecer() = shot("2_atardecer", 18, Ambient.ATARDECER, weather(21, 2, true))

    @Test
    fun noche() = shot("3_noche", 22, Ambient.NOCHE, weather(16, 0, false))

    @Test
    fun lluvia() = shot("4_lluvia", 16, Ambient.LLUVIA, weather(14, 63, true))

    @Test
    fun lluviaNoche() = shot("11_lluvia_noche", 22, Ambient.LLUVIA_NOCHE, weather(13, 63, false))

    @Test
    fun lluviaAtardecer() = shot("12_lluvia_atardecer", 18, Ambient.LLUVIA_ATARDECER, weather(15, 61, true))

    @Test
    fun presentacion() {
        captureRoboImage("build/screenshots/13_presentacion.png") { IntroOverlay(onDone = {}, still = true) }
    }

    @Test
    fun navegacion() {
        val sampleMedia = MediaResult(
            true,
            MediaInfo(
                pkg = "com.spotify.music",
                title = "De Música Ligera",
                artist = "Soda Stereo",
                art = null,
                playing = true,
                positionMs = 12_000L,
                durationMs = 212_000L,
                controller = null
            )
        )
        Lyrics.seed(
            "De Música Ligera",
            "Soda Stereo",
            listOf(
                LyricLine(0L, "De aquel amor de música ligera"),
                LyricLine(10_000L, "Nada nos libra, nada más queda")
            )
        )
        captureRoboImage("build/screenshots/5_navegacion.png") {
            NavMusicScreen(
                now = at(22, 15),
                media = sampleMedia,
                weather = weather(16, 0, false),
                onDiag = {}
            )
        }
    }

    @Test
    fun radios() {
        captureRoboImage("build/screenshots/6_radios.png") {
            FmRadioPanel(
                stations = TucumanFm.stations.filter { it.name == "La 100" } + listOf(
                    Station("FM Sensaciones 95.1", "", "Tucumán · AAC 64 kbps"),
                    Station("Radio Bicentenario Tucumán", "", "Tucumán · AAC 128 kbps"),
                    Station("FMI Tucumán", "", "Tucumán · MP3 128 kbps")
                ),
                loading = false,
                current = "La 100",
                favorites = setOf("La 100", "Radio Bicentenario Tucumán"),
                playing = true,
                buffering = false,
                onPlay = {}, onStop = {}, onClose = {}
            )
        }
    }

    @Test
    fun editar() {
        captureRoboImage("build/screenshots/7_editar.png") {
            HomeScreen(
                now = at(12, 15),
                ambient = Ambient.DIA,
                lightsOn = false,
                media = MediaResult(true, null),
                speed = 62,
                weather = weather(24, 0, true),
                obd = sampleObd,
                editing = true,
                onDiag = {}
            )
        }
    }

    @Test
    fun acercaDe() {
        captureRoboImage("build/screenshots/8_acerca_de.png") {
            Box(Modifier.size(1024.dp, 600.dp)) { AboutOverlay(onClose = {}) }
        }
    }

    @Test
    fun diagnostico() {
        captureRoboImage("build/screenshots/9_diagnostico.png") {
            Box(Modifier.size(1024.dp, 600.dp)) {
                SettingsNav.category.value = SettingsCategory.DIAG
                SettingsOverlay(
                    lightsMode = LightsMode.RADIO, onLightsMode = {}, onAbout = {}, onClose = {},
                    diagnostics = {
                        DiagnosticsPanel(
                            now = at(22, 15), ambient = Ambient.NOCHE, lightsOn = true,
                            media = MediaResult(true, null), speed = 62, weather = weather(16, 0, false)
                        )
                    }
                )
            }
        }
        SettingsNav.category.value = SettingsCategory.SCREEN
    }

    @Test
    fun inicioConRadio() {
        val la100 = TucumanFm.stations.first { it.name == "La 100" }
        RadioState.station.value = la100
        RadioState.current.value = la100.name
        RadioState.playing.value = true
        captureRoboImage("build/screenshots/10_inicio_radio.png") {
            HomeScreen(
                now = at(18, 15), ambient = Ambient.ATARDECER, lightsOn = true,
                media = MediaResult(true, null), speed = 62, weather = weather(21, 2, true),
                obd = sampleObd, onDiag = {}
            )
        }
        RadioState.current.value = null
        RadioState.playing.value = false
    }

    @Test
    fun codigosDeFalla() {
        val codes = ObdCodes(mil = true, stored = listOf("P0420", "P0133"), pending = listOf("P0171"), readAt = 0L)
        captureRoboImage("build/screenshots/14_codigos.png") {
            Box(Modifier.size(1024.dp, 600.dp)) {
                HomeScreen(
                    now = at(12, 15), ambient = Ambient.DIA, lightsOn = false,
                    media = MediaResult(true, null), speed = 0, weather = weather(24, 0, true),
                    obd = sampleObd, obdCodes = codes, onDiag = {}
                )
                CodesDialog(codes, speed = 0, onClose = {})
            }
        }
    }

    @Test
    fun avisoLuces() {
        captureRoboImage("build/screenshots/15_aviso_luces.png") {
            Box(Modifier.size(1024.dp, 600.dp)) {
                HomeScreen(
                    now = at(22, 15), ambient = Ambient.NOCHE, lightsOn = false,
                    media = MediaResult(true, null), speed = 62, weather = weather(16, 0, false),
                    obd = sampleObd, obdCodes = noCodes, onDiag = {}
                )
                SafetyBanner(androidx.compose.ui.graphics.Color(0xF2C77700), "Luces apagadas", "Encendelas para un viaje más seguro", onClose = {})
            }
        }
    }

    @Test
    fun miAuto() {
        CarCare.load(org.robolectric.RuntimeEnvironment.getApplication())
        val today = Reminders.today()
        CarCare.offsetKm.value = 85_400f
        CarCare.trips.value = listOf(
            TripSummary(System.currentTimeMillis() - 3_600_000L, 1_560, 18.4f, 96, 52, 92, 1.3f),
            TripSummary(System.currentTimeMillis() - 86_400_000L, 720, 6.2f, 58, 31, 89, 0.6f)
        )
        CarCare.maintenance.value = Maintenance.defaults().map {
            when (it.name) {
                "Aceite y filtro de aceite" -> it.copy(lastKm = 76_000, lastDay = today - 200)
                "Alineación y balanceo (con rotación)" -> it.copy(lastKm = 84_800)
                "Neumáticos" -> it.copy(lastKm = 30_000)
                "VTV" -> it.copy(lastDay = today - 350)
                else -> it
            }
        }
        captureRoboImage("build/screenshots/16_mi_auto.png") { CarCareOverlay(onClose = {}) }
    }

    /** v0.65.0: temas festivos (decoración quieta, como en las capturas). */
    @Test
    fun navidad() {
        Festive.preview.value = Holiday.NAVIDAD
        shot("17_navidad", 22, Ambient.NOCHE, weather(24, 0, false))
    }

    @Test
    fun fechaPatria() {
        Festive.preview.value = Holiday.INDEPENDENCIA
        shot("18_fecha_patria", 12, Ambient.DIA, weather(12, 0, true))
    }

    @Test
    fun anioNuevo() {
        Festive.preview.value = Holiday.ANIO_NUEVO
        shot("19_anio_nuevo", 0, Ambient.NOCHE, weather(22, 0, false))
    }

    @Test
    fun halloween() {
        Festive.preview.value = Holiday.HALLOWEEN
        shot("20_halloween", 21, Ambient.NOCHE, weather(18, 0, false))
    }

    @Test
    fun ajustes() {
        captureRoboImage("build/screenshots/21_ajustes.png") {
            Box(Modifier.size(1024.dp, 600.dp)) {
                SettingsOverlay(
                    lightsMode = LightsMode.RADIO,
                    onLightsMode = {},
                    onAbout = {},
                    onClose = {}
                )
            }
        }
    }

    /** v0.81.11: las otras categorías de Ajustes, para revisar que cada una entre en el panel. */
    @Test
    fun ajustesPorCategoria() {
        for ((name, category) in listOf(
            "22_ajustes_sonido" to SettingsCategory.SOUND,
            "23_ajustes_auto" to SettingsCategory.CAR,
            "24_ajustes_sistema" to SettingsCategory.SYSTEM,
        )) {
            SettingsNav.category.value = category
            captureRoboImage("build/screenshots/$name.png") {
                Box(Modifier.size(1024.dp, 600.dp)) {
                    SettingsOverlay(lightsMode = LightsMode.RADIO, onLightsMode = {}, onAbout = {}, onClose = {})
                }
            }
        }
        SettingsNav.category.value = SettingsCategory.SCREEN
    }
}
