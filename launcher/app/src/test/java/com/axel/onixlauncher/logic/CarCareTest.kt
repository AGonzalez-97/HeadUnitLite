package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Builtin
import com.axel.onixlauncher.DockSlot
import com.axel.onixlauncher.DockStore
import com.axel.onixlauncher.Maintenance
import com.axel.onixlauncher.MaintItem
import com.axel.onixlauncher.Monthly
import com.axel.onixlauncher.Reminders
import com.axel.onixlauncher.TripTracker
import com.axel.onixlauncher.Urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Mi auto (v0.52.0): viajes, recordatorios y el acceso del dock. */
class CarCareTest {

    @Test
    fun viajeDeDiezKmConParadaFinal() {
        val t = TripTracker()
        var trip: com.axel.onixlauncher.TripSummary? = null
        for (s in 0..600) assertNull(t.tick(s * 1000L, 60, 4f, 85 + s / 100))        // 10 min a 60 km/h
        for (s in 601..900) { trip = t.tick(s * 1000L, 0, 4f, 90) ?: trip }           // 5 min parado
        assertNotNull(trip)
        trip!!
        assertEquals(10f, trip.km, 0.01f)
        assertEquals(600, trip.seconds)
        assertEquals(60, trip.maxKmh)
        assertEquals(60, trip.avgKmh)
        assertEquals(1.0f, trip.liters!!, 0.01f)                                       // 4 l/h durante 15 min
        assertEquals(10f, trip.kmPerLiter!!, 0.1f)
        assertEquals(91, trip.maxCoolant)
    }

    @Test
    fun elRuidoDelGpsEstacionadoNoArmaViajes() {
        val t = TripTracker()
        for (s in 0..600) assertNull(t.tick(s * 1000L, if (s % 2 == 0) 7 else 0, null, null))   // 5–8 km/h de ruido
        assertEquals(0f, t.km, 0.0001f)
        assertEquals(false, t.active)
    }

    @Test
    fun maniobrasCortasNoSonViajesYLosHuecosNoSumanKm() {
        val t = TripTracker()
        for (s in 0..10) t.tick(s * 1000L, 30, null, null)
        var trip: com.axel.onixlauncher.TripSummary? = null
        for (s in 11..400) trip = t.tick(s * 1000L, null, null, null) ?: trip
        assertNull(trip)                                                               // 80 m: no cuenta
        val g = TripTracker()
        g.tick(0, 100, null, null)
        g.tick(60_000, 100, null, null)                                                // la app no corrió 1 min
        assertEquals(100f * 5 / 3600, g.km, 0.001f)                                    // solo 5 s
    }

    private val today = 20_000L

    @Test
    fun cuantoFaltaParaCadaService() {
        val oil = MaintItem(1, "Aceite y filtro de aceite", everyKm = 10_000, everyMonths = 12, lastKm = 80_000, lastDay = today - 30)
        assertEquals(Urgency.OK, Maintenance.urgency(oil, carKm = 85_000, today = today))
        assertEquals("Faltan 5.000 km", Maintenance.describe(oil, 85_000, today))
        assertEquals(Urgency.SOON, Maintenance.urgency(oil, 89_400, today))             // menos de 1.000 km
        assertEquals(Urgency.DUE, Maintenance.urgency(oil, 90_300, today))
        assertEquals("Pasado por 300 km", Maintenance.describe(oil, 90_300, today))
        // Por fecha, lo primero que llegue
        assertEquals(Urgency.DUE, Maintenance.urgency(oil.copy(lastDay = today - 400), 81_000, today))
        // Sin el km del auto: cuándo toca
        assertEquals("A los 90.000 km", Maintenance.describe(oil, null, today))
    }

    @Test
    fun itemsSinCompletar() {
        val belt = MaintItem(2, "Correa de distribución", everyKm = null, everyMonths = null, lastKm = null, lastDay = null)
        assertEquals(Urgency.UNSET, Maintenance.urgency(belt, 85_000, today))
        assertEquals("Completá cada cuánto se hace", Maintenance.describe(belt, 85_000, today))
        val tires = MaintItem(3, "Neumáticos", 60_000, null, null, null)
        assertEquals("Completá a qué km lo hiciste", Maintenance.describe(tires, 85_000, today))
        val vtv = MaintItem(4, "VTV", null, 12, null, today - 300)
        assertEquals("Faltan 66 días", Maintenance.describe(vtv, null, today))
    }

    @Test
    fun hechoGuardaElKmYLaFecha() {
        val oil = MaintItem(1, "Aceite", 10_000, 12, 80_000, today - 300)
        val done = Maintenance.done(oil, km = 90_100, today = today)
        assertEquals(90_100, done.lastKm)
        assertEquals(today, done.lastDay)
        assertEquals("Faltan 10.000 km", Maintenance.describe(done, 90_100, today))
        // La lista de arranque: lo que pidió el autor
        val names = Maintenance.defaults().map { it.name }
        assertEquals(true, "Neumáticos" in names && "Batería" in names && "VTV" in names)
        assertEquals(60_000, Maintenance.defaults().first { it.name == "Neumáticos" }.everyKm)
        assertEquals(10_000, Maintenance.defaults().first { it.name.startsWith("Alineación") }.everyKm)
        assertEquals(null, Maintenance.defaults().first { it.name == "Correa de distribución" }.everyKm)
        assertEquals(false, names.any { it == "Seguro" || it == "Patente" })
    }

    @Test
    fun litrosPorElNivelDelTanqueSinConsumoDelLector() {
        val t = TripTracker()
        var trip: com.axel.onixlauncher.TripSummary? = null
        for (s in 0..600) t.tick(s * 1000L, 60, null, 85, fuelPct = if (s < 300) 50 else 46)
        trip = t.finish()
        assertNotNull(trip)
        assertNull(trip!!.liters)                                                    // el lector no da consumo
        assertEquals(54f * 4 / 100, trip.levelLiters!!, 0.01f)                        // 4 % de 54 L
        assertEquals(54f * 4 / 100, trip.shownLiters!!, 0.01f)
        assertEquals(true, trip.fromLevel)
    }

    @Test
    fun resumenPorMes() {
        val t1 = com.axel.onixlauncher.TripSummary(1_761_998_400_000L, 600, 10f, 60, 60, null, 1f)   // 01/11/2025
        val t2 = com.axel.onixlauncher.TripSummary(1_762_084_800_000L, 600, 20f, 60, 60, null, 1f)
        var m = Monthly.add(emptyMap(), t1, TimeZone.getTimeZone("GMT-3"))
        m = Monthly.add(m, t2, TimeZone.getTimeZone("GMT-3"))
        val nov = m.getValue("2025-11")
        assertEquals(30f, nov.km, 0.01f)
        assertEquals(2f, nov.liters, 0.01f)
        assertEquals("Noviembre 2025: 30 km · 2,0 l · 15,0 km/l", Monthly.label("2025-11", nov))
    }

    @Test
    fun mesesYDiaDeHoy() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.JANUARY, 31) }
        val jan31 = c.timeInMillis / 86_400_000L
        val next = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = Reminders.addMonths(jan31, 1) * 86_400_000L }
        assertEquals(Calendar.FEBRUARY, next.get(Calendar.MONTH))
        assertEquals(28, next.get(Calendar.DAY_OF_MONTH))
        // 23 h UTC del día 20000 en Argentina (UTC−3) todavía es el día 20000
        assertEquals(20_000L, Reminders.today(20_000L * 86_400_000L + 23 * 3_600_000L, TimeZone.getTimeZone("GMT-3")))
        assertEquals("85.400", Reminders.km(85_400))
    }

    @Test
    fun miAutoReemplazaADiagnosticoEnElDock() {
        val old = listOf(Builtin.NAV, Builtin.MUSIC, Builtin.RADIO, Builtin.PHONE, Builtin.APPS, Builtin.DIAG, Builtin.SETTINGS).map { DockSlot.Own(it) }
        val migrated = DockStore.withCar(old)
        assertEquals(DockSlot.Own(Builtin.CAR), migrated[5])
        assertEquals(migrated, DockStore.withCar(migrated))                            // una sola vez
        assertEquals(DockSlot.Own(Builtin.CAR), DockStore.defaults[5])
    }

    @Test
    fun velocidadDelGpsSinSenalSeOlvida() {
        assertEquals(false, com.axel.onixlauncher.GpsSpeed.stale(lastFixAt = 10_000, now = 14_000))
        assertEquals(true, com.axel.onixlauncher.GpsSpeed.stale(lastFixAt = 10_000, now = 15_001))
        assertEquals(true, com.axel.onixlauncher.GpsSpeed.stale(lastFixAt = 0, now = 1_000))
    }

    /**
     * AUD-058 (v0.64.1): la velocidad "en vivo" no depende de que la pantalla se redibuje
     * (con Android Auto o CarPlay encima, Onix no se redibuja). OBD2 primero; GPS si es reciente.
     */
    @Test
    fun velocidadEnVivo() {
        assertEquals(30, com.axel.onixlauncher.GpsSpeed.pick(obdKmh = 30, gpsKmh = 50, gpsAt = 1_000, now = 2_000))
        assertEquals(50, com.axel.onixlauncher.GpsSpeed.pick(obdKmh = null, gpsKmh = 50, gpsAt = 1_000, now = 2_000))
        assertEquals(null, com.axel.onixlauncher.GpsSpeed.pick(obdKmh = null, gpsKmh = 50, gpsAt = 1_000, now = 6_001))
        assertEquals(null, com.axel.onixlauncher.GpsSpeed.pick(obdKmh = null, gpsKmh = null, gpsAt = 0, now = 1_000))
    }
}

