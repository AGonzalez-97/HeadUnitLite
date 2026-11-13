package com.axel.onixlauncher.logic

import com.axel.onixlauncher.CarHealth
import com.axel.onixlauncher.CarSystem
import com.axel.onixlauncher.EngineAlert
import com.axel.onixlauncher.HealthLevel
import com.axel.onixlauncher.ObdCodes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Salud del auto por sistema (v0.64.0; idea de LiteDash, código propio según SAE J2012). */
class CarHealthTest {
    @Test
    fun cadaCodigoVaASuSistema() {
        assertEquals(CarSystem.AIR_FUEL, CarHealth.systemOf("P0171"))     // mezcla pobre
        assertEquals(CarSystem.ENGINE, CarHealth.systemOf("P0301"))       // falla de encendido
        assertEquals(CarSystem.EMISSIONS, CarHealth.systemOf("P0420"))    // catalizador
        assertEquals(CarSystem.COOLING, CarHealth.systemOf("P0128"))      // termostato
        assertEquals(CarSystem.BATTERY, CarHealth.systemOf("P0562"))      // voltaje bajo
        assertEquals(CarSystem.GEARBOX, CarHealth.systemOf("P0700"))
        assertEquals(CarSystem.BRAKES, CarHealth.systemOf("C0035"))
        assertEquals(CarSystem.BODY, CarHealth.systemOf("U0100"))
    }

    @Test
    fun estadoDeCadaSistema() {
        val codes = ObdCodes(mil = true, stored = listOf("P0301"), pending = listOf("P0420"), readAt = 0L)
        val h = CarHealth.of(codes, EngineAlert.HOT).toMap()
        assertEquals(HealthLevel.FAULT, h[CarSystem.ENGINE])       // guardado
        assertEquals(HealthLevel.WARN, h[CarSystem.EMISSIONS])     // pendiente
        assertEquals(HealthLevel.FAULT, h[CarSystem.COOLING])      // recalentado
        assertEquals(HealthLevel.GOOD, h[CarSystem.GEARBOX])
        assertEquals(HealthLevel.UNKNOWN, CarHealth.of(null, null).toMap()[CarSystem.ENGINE])
    }

    /** AUD-065 (v0.67.1): la Prueba completa resume la salud por sistema en una línea. */
    @Test
    fun resumenParaElInforme() {
        val codes = ObdCodes(mil = true, stored = listOf("P0301"), pending = listOf("P0420"), readAt = 0L)
        assertEquals(
            "Motor y encendido: falla · Escape y emisiones: atención · el resto bien",
            CarHealth.summary(CarHealth.of(codes, null))
        )
        assertEquals("todo bien", CarHealth.summary(CarHealth.of(ObdCodes(mil = false, stored = emptyList(), pending = emptyList(), readAt = 0L), null)))
        assertEquals("sin dato (no se leyeron los códigos)", CarHealth.summary(CarHealth.of(null, null)))
    }

    @Test
    fun luzPrendidaSinCodigos() {
        val h = CarHealth.of(ObdCodes(mil = true, stored = emptyList(), pending = emptyList(), readAt = 0L), null).toMap()
        assertEquals(HealthLevel.WARN, h[CarSystem.ENGINE])
        assertEquals(HealthLevel.WARN, CarHealth.of(null, EngineAlert.NOT_CHARGING).toMap()[CarSystem.BATTERY])
    }
}
