# Prompt para Claude Code — App Onix Joy 2018

---

## Objective

Construir una app Android nativa llamada **OnixGarage** para un Chevrolet Onix Joy 2018, que se conecta en tiempo real a un lector OBD2 ELM327 vía Bluetooth, lee datos del auto en vivo, y los combina con historial persistente de mantenimiento, nafta, neumáticos y km diarios. La app debe correr en tres targets: teléfono Android, radio unit genérica Android, y Android Auto.

---

## Context (carry forward — leer antes de cada sesión)

### El auto
- Modelo: Chevrolet Onix Joy 2018
- Motor: 1.4L Ecotec (tiene sensor MAF — PID 0x10 disponible)
- Tanque: 50 litros
- Lector OBD2: ELM327 Bluetooth (ya disponible físicamente)
- Protocolo: CAN bus / OBD2 estándar — soportado completo en este modelo
- Ubicación del dueño: Tucumán, Argentina (precios en pesos argentinos)

### Decisiones de arquitectura ya tomadas
- Lenguaje: **Kotlin**
- IDE: **Android Studio**
- UI: **Jetpack Compose** (no XML)
- Base de datos local: **Room + SQLite**
- Patrón: **MVVM** con un módulo central compartido entre los tres targets
- Conexión OBD2: **BluetoothSocket SPP** (perfil serie) para teléfono y radio unit
- Para radio unit genérica: puede usarse ELM327 WiFi si el Bluetooth de la unidad está ocupado con audio/llamadas
- Android Auto: **Car App Library (androidx.car.app)** — no UI libre, solo templates de Google
- Arquitectura modular: un módulo `core` con toda la lógica, tres módulos de UI (`app-phone`, `app-headunit`, `app-auto`)

### Targets y sus restricciones
| Target | UI | Instalación | Restricciones |
|---|---|---|---|
| Teléfono Android | Jetpack Compose libre | APK normal / Play Store | Ninguna |
| Radio unit genérica | Jetpack Compose adaptada (texto grande, pantalla horizontal) | Sideload APK por USB | RAM puede ser 1-2GB, Android 8-10 |
| Android Auto | Car App Library templates fijos | Sideload personal (sin Google review) | Sin gráficos custom, solo listas/grids/alertas |

---

## Starting State

Proyecto nuevo en Android Studio. Sin código previo. Estructura de módulos a crear desde cero.

---

## Target State

App funcional con las siguientes pantallas y capacidades operativas:

### Módulo core (compartido, sin UI)
- Servicio Bluetooth que se conecta al ELM327 y mantiene el socket abierto mientras el auto está encendido
- Lector de PIDs OBD2 que envía comandos AT y parsea respuestas hex cada 2 segundos
- Room Database con entidades: `FuelLoad`, `ServiceRecord`, `TirePressureRecord`, `DailyTrip`
- ViewModels que exponen StateFlow para cada pantalla
- Calculadora de consumo (MAF-based para tiempo real, fill-to-fill para historial preciso)
- Estimaciones: autonomía restante, días hasta quedarse sin nafta, costo del día, costo por km

### PIDs OBD2 a implementar (en orden de prioridad)
```
0x0C — RPM del motor
0x0D — Velocidad (km/h)
0x05 — Temperatura del refrigerante
0x2F — Nivel de combustible (%) — referencia gruesa, tanque 50L
0x10 — MAF (g/s) — para consumo en tiempo real preciso
0x11 — Posición del acelerador
0x04 — Carga del motor (%)
0x31 — Km recorridos desde último reset de fallas
0x03 — Códigos de falla DTC (leer y borrar)
```

**Fórmula consumo MAF:**
```kotlin
val litrosPorHora = (mafGs * 3600.0) / (14.7 * 730.0)
val litrosPor100km = if (velocidadKmh > 0) (litrosPorHora / velocidadKmh) * 100 else 0.0
```

### Pantallas — target teléfono y radio unit

**1. Dashboard en tiempo real**
- Nivel de nafta visual (tanque animado) + litros restantes estimados
- Autonomía restante en km
- RPM + temperatura motor
- Velocidad actual
- Consumo instantáneo (L/100km) y del viaje actual
- Gasto en pesos del día y del viaje actual
- Estado de conexión OBD2 (conectado / buscando / error)

**2. Nafta**
- Nivel del depósito en tiempo real (PID 0x2F + cálculo MAF)
- Autonomía restante calculada desde consumo histórico real
- Estimación de días hasta quedarse sin nafta (basada en km/día promedio histórico)
- Costo del día, del mes proyectado, por km recorrido
- Formulario para registrar carga: tipo (Súper / Premium / Infinia), litros, precio por litro (calcula total automático), tanque lleno sí/no
- Historial de cargas: fecha, litros, tipo, precio/L, consumo calculado fill-to-fill

**3. Service y mantenimiento**
- Lista de servicios con barra de progreso por km real del auto
- Alertas: al 85% del intervalo → amarillo, al 100% → rojo vencido
- Tipos predefinidos: cambio de correa de distribución, aceite, filtro de aceite, filtro de aire, filtro de combustible, bujías, pastillas de freno, líquido de frenos, refrigerante, gomería (balanceo/alineación)
- Formulario: tipo, fecha realizado, km al realizar, próximo service a km X, costo, notas
- El km del auto se actualiza automáticamente desde OBD2 (PID 0x0D integrado)

**4. Neumáticos**
- 4 ruedas individuales + rueda de auxilio
- Presión recomendada: 32 PSI
- Estado visual: OK (30-34 PSI), Baja/Alta (±5 PSI), Muy baja/alta (más de ±5 PSI)
- Formulario: registrar presión de una rueda o las cuatro juntas
- Historial de controles con fecha

**5. Historial de km y viajes**
- Registro automático de cada sesión (ignición ON → OFF): km recorridos, duración, velocidad promedio y máxima, temperatura al arrancar y al llegar, consumo de ese viaje, costo en pesos
- Vista de km por día / semana / mes
- Km acumulados totales desde que se instaló la app
- Gráfico semanal de km y consumo

**6. Fallas (DTC)**
- Leer códigos de error activos del auto
- Descripción en español de cada código
- Botón para borrar fallas (con confirmación obligatoria antes de ejecutar)

### Pantallas — Android Auto (Car App Library)
Solo lo permitido por Google con templates fijos:
- Lista de estado: nivel nafta, autonomía, temperatura motor, km del día
- Alertas activas: servicios vencidos o próximos, fallas DTC
- Sin formularios, sin gráficos custom, solo lectura

---

## Allowed Actions

- Crear y modificar archivos dentro del proyecto Android Studio
- Instalar dependencias declarándolas primero y esperando aprobación antes de agregarlas al `build.gradle`
- Crear la estructura de módulos desde cero
- Escribir código Kotlin, XML de recursos, y archivos de configuración del proyecto
- Proponer decisiones de arquitectura cuando haya dos caminos válidos — siempre pedir aprobación antes de implementar

---

## Forbidden Actions

- NUNCA modificar archivos fuera del directorio del proyecto
- NUNCA agregar dependencias sin declararlas primero con su versión exacta y esperar aprobación
- NUNCA implementar la integración con Android Auto hasta que el módulo `core` y la app de teléfono estén funcionando
- NUNCA hacer llamadas a APIs externas o de red — toda la data es local o viene del OBD2 por Bluetooth
- NUNCA eliminar archivos sin mostrar qué se va a borrar y esperar confirmación
- NUNCA tomar decisiones de arquitectura que afecten los tres targets sin aprobación explícita

---

## Stop Conditions

Pausar y pedir revisión humana cuando:
- Una dependencia nueva cambia la arquitectura de algún módulo
- Hay dos formas de implementar la conexión Bluetooth y la elección afecta la radio unit
- Un PID del OBD2 no responde como se espera en el Onix Joy 2018
- El manejo de errores de reconexión Bluetooth requiere una decisión de UX
- Cualquier cambio afecte los tres targets simultáneamente

---

## Checkpoints

Después de cada paso major, outputear: ✅ [qué se completó]

**Orden de desarrollo obligatorio:**
1. ✅ Estructura de módulos (`core`, `app-phone`, `app-headunit`, `app-auto`)
2. ✅ Conexión Bluetooth al ELM327 + lectura de un solo PID (temperatura motor)
3. ✅ Lectura de todos los PIDs de la lista con parser hex
4. ✅ Room Database con todas las entidades
5. ✅ ViewModels + StateFlow para cada pantalla
6. ✅ Dashboard en tiempo real (teléfono)
7. ✅ Pantalla de nafta completa con estimaciones
8. ✅ Pantalla de service con alertas por km
9. ✅ Pantalla de neumáticos
10. ✅ Historial de viajes y km diarios
11. ✅ Pantalla de fallas DTC
12. ✅ Adaptar UI para radio unit (texto grande, layout horizontal)
13. ✅ Módulo Android Auto con Car App Library

Al finalizar cada checkpoint, listar todos los archivos creados o modificados.

---

## Constraints técnicos

- `minSdk`: 26 (Android 8.0) — para compatibilidad con radio units genéricas
- `targetSdk`: 34
- Kotlin Coroutines para todo lo asíncrono — sin callbacks, sin RxJava
- Room para persistencia — sin SharedPreferences para datos estructurados
- No usar librerías OBD2 de terceros — implementar el protocolo ELM327 directamente para tener control total
- Bluetooth Classic (SPP, UUID `00001101-0000-1000-8000-00805F9B34FB`) — no BLE
- Todos los precios en pesos argentinos (ARS), sin símbolo de moneda externo, formato `$1.450`
- Consumo en L/100km (no mpg)
- Distancias en km (no millas)
- Jetpack Compose para toda UI del teléfono y radio unit — cero XML layouts
- Car App Library versión estable más reciente para el módulo Android Auto

---

## Primera tarea a ejecutar

Crear la estructura de módulos del proyecto:

```
OnixGarage/
├── core/                    ← lógica pura, sin Android UI
│   ├── obd/                 ← BluetoothService, PidReader, PidParser
│   ├── data/                ← Room DB, DAOs, entidades
│   ├── domain/              ← ViewModels, cálculos, estimaciones
├── app-phone/               ← UI Compose para teléfono
├── app-headunit/            ← UI Compose adaptada para radio unit
└── app-auto/                ← Car App Library para Android Auto
```

Crear el `build.gradle` raíz con las dependencias base y los tres módulos declarados. Listar cada dependencia con su versión antes de agregar. Esperar aprobación antes de continuar al paso 2.
