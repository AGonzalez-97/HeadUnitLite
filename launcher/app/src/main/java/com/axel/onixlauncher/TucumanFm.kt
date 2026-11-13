package com.axel.onixlauncher

/**
 * Emisoras FM de San Miguel de Tucumán con su frecuencia real (fuente: World Radio Map).
 * El enlace vacío significa "buscarlo en Radio Browser al sintonizar" (prefiriendo la señal de Tucumán).
 * Varios enlaces se separan con "|"; "page:" es la página del reproductor oficial y "nac:" la señal nacional.
 */
object TucumanFm {
    /*
     * La 100 Tucumán (95.5): el reproductor de Cienradios para Tucumán ya no existe (HTTP 404 en
     * el celular del autor, 2026-10-26), StreamTheWorld no tiene ninguna señal de Tucumán (12
     * nombres probables, todos 404) y ningún directorio publica una. Por internet solo está la
     * señal de La 100 de Buenos Aires (FM999_56), que se muestra como "SEÑAL NACIONAL".
     *
     * Cadenas sin señal de Tucumán por internet (Mitre, Radio 10, Vale, Cadena 3, Radio María,
     * Rivadavia como respaldo): se usa la señal nacional ("nac:"), como en La 100. Radio 10 y
     * Vale siguen el formato HLS de stweb.tv que usa Mega 98.3 (radioarg.net); no confirmados.
     *
     * Enlaces de audio de radios de Tucumán: radioarg.net (2026-10-26). La revisión de radios
     * de la app confirma en el equipo cuáles responden.
     */

    private fun fm(f: String, name: String, url: String = "") =
        Station(name, url, "FM $f · San Miguel de Tucumán", logo = logos[f], homepage = homepages[f])

    /**
     * Logos por frecuencia: las imágenes que World Radio Map publica para cada emisora del dial
     * de Tucumán. No están en el repositorio; la app las descarga y guarda en la caché del equipo.
     * Las que World Radio Map tiene en SVG (Android no lo lee) o sin logo se buscan por otras vías.
     */
    private val logos: Map<String, String> = mapOf(
        "87.5" to "https://worldradiomap.com/ar/images/elpalomar.gif",
        "87.7" to "https://worldradiomap.com/ar/images/tec877.gif",
        "87.9" to "https://worldradiomap.com/ar/images/ciudad-smt.gif",
        "88.1" to "https://worldradiomap.com/ar/images/rt881.gif",
        "88.5" to "https://worldradiomap.com/ar/images/antares.gif",
        "88.9" to "https://worldradiomap.com/us/images/bbn.gif",
        "89.1" to "https://worldradiomap.com/ar/images/cadena-eco.gif",
        "89.5" to "https://worldradiomap.com/ar/images/lastalitas.gif",
        "89.9" to "https://worldradiomap.com/ar/images/mega899.gif",
        "90.1" to "https://worldradiomap.com/ar/images/prensa901.gif",
        "90.5" to "https://worldradiomap.com/ar/images/9dejulio.gif",
        "90.7" to "https://radiomap.eu/it/images/maria.gif",
        "90.9" to "https://worldradiomap.com/ar/images/jerusalen909.gif",
        "91.3" to "https://worldradiomap.com/ar/images/activa913.gif",
        "91.7" to "https://worldradiomap.com/ar/images/delparque917.gif",
        "91.9" to "https://worldradiomap.com/ar/images/elfortin.gif",
        "92.1" to "https://worldradiomap.com/ar/images/aspen.gif",
        "92.7" to "https://worldradiomap.com/ar/images/mi927.gif",
        "92.9" to "https://worldradiomap.com/ar/images/romantica929.gif",
        "93.1" to "https://worldradiomap.com/ar/images/wonder931.gif",
        "93.3" to "https://worldradiomap.com/ar/images/play959.gif",
        "93.5" to "https://worldradiomap.com/ar/images/metropolitana935.gif",
        "93.9" to "https://worldradiomap.com/ar/images/delplata.gif",
        "94.3" to "https://worldradiomap.com/ar/images/gamba.gif",
        "94.7" to "https://worldradiomap.com/ar/images/universidad-tucuman.gif",
        "95.1" to "https://worldradiomap.com/ar/images/sensaciones-tucuman.gif",
        "95.5" to "https://worldradiomap.com/ar/images/la100.gif",
        "95.7" to "https://worldradiomap.com/ar/images/tiempo957.gif",
        "95.9" to "https://worldradiomap.com/ar/images/feel.gif",
        "96.3" to "https://worldradiomap.com/ar/images/dokime.gif",
        "96.7" to "https://worldradiomap.com/ar/images/besame967.gif",
        "96.9" to "https://worldradiomap.com/ar/images/reskate.gif",
        "97.1" to "https://worldradiomap.com/ar/images/tucuman971.gif",
        "97.3" to "https://worldradiomap.com/ar/images/uno-lafolk.gif",
        "97.5" to "https://worldradiomap.com/ar/images/deljardin.gif",
        "97.7" to "https://worldradiomap.com/ar/images/rivadavia.gif",
        "98.1" to "https://worldradiomap.com/ar/images/antena8.gif",
        "98.3" to "https://worldradiomap.com/ar/images/sport983.gif",
        "99.1" to "https://worldradiomap.com/ar/images/fmi.gif",
        "99.3" to "https://worldradiomap.com/ar/images/latinatropical993.gif",
        "99.5" to "https://worldradiomap.com/ar/images/mitre.gif",
        "100.5" to "https://worldradiomap.com/ar/images/metropolitana1005.gif",
        "100.9" to "https://worldradiomap.com/ar/images/miroisem.gif",
        "101.1" to "https://worldradiomap.com/ar/images/mia-tucuman.gif",
        "101.3" to "https://worldradiomap.com/ar/images/lavoz-tucuman.gif",
        "101.5" to "https://worldradiomap.com/ar/images/radio10.gif",
        "101.7" to "https://worldradiomap.com/ar/images/losralos.gif",
        "102.3" to "https://worldradiomap.com/ar/images/vale975.gif",
        "102.7" to "https://worldradiomap.com/ar/images/lv7.gif",
        "102.9" to "https://worldradiomap.com/ar/images/bandadelriosali.gif",
        "103.1" to "https://worldradiomap.com/ar/images/cadena-fuego.gif",
        "103.3" to "https://worldradiomap.com/ar/images/bicentenario.gif",
        "103.5" to "https://worldradiomap.com/ar/images/laser1035.gif",
        "103.7" to "https://worldradiomap.com/ar/images/libertad.gif",
        "103.9" to "https://worldradiomap.com/ar/images/sanalfonso.gif",
        "104.1" to "https://worldradiomap.com/ar/images/la1041.gif",
        "104.3" to "https://worldradiomap.com/ar/images/dinamica1043.gif",
        "104.5" to "https://worldradiomap.com/ar/images/contacto1045.gif",
        "104.7" to "https://worldradiomap.com/ar/images/dale1047.gif",
        "104.9" to "https://worldradiomap.com/ar/images/conciencia.gif",
        "105.1" to "https://worldradiomap.com/ar/images/lv12.gif",
        "105.3" to "https://worldradiomap.com/ar/images/joven1053.gif",
        "105.5" to "https://worldradiomap.com/ar/images/a-tucuman.gif",
        "105.9" to "https://worldradiomap.com/ar/images/top1059.gif",
        "106.1" to "https://worldradiomap.com/ar/images/delfingallo.gif",
        "106.5" to "https://worldradiomap.com/ar/images/fm2000.gif",
        "106.9" to "https://worldradiomap.com/ar/images/rockandpop_tucuman.gif",
        "107.1" to "https://worldradiomap.com/ar/images/lacompanera.gif",
        "107.3" to "https://worldradiomap.com/ar/images/salvacion1073.gif",
        "107.5" to "https://worldradiomap.com/ar/images/cadena3.gif",
        "107.9" to "https://worldradiomap.com/ar/images/lider-echeverria.gif"
    )

    /** Páginas web propias de las emisoras (de ahí sale su ícono si el logo no carga). */
    private val homepages: Map<String, String> = mapOf(
        "91.3" to "http://fmradioactiva.com/",
        "94.7" to "http://fm947universidad.com.ar/",
        "97.5" to "http://www.radiodeljardin.com.ar/",
        "102.9" to "http://radiomunicipal.bandadelriosali.gob.ar/"
    )

    val stations: List<Station> = listOf(
        fm("87.5", "Radio El Palomar", "https://az03.streaminghd.net.ar:8232/stream"),
        fm("87.7", "Radio Tec"),
        fm("87.9", "Radio Ciudad"),
        fm("88.1", "RT 88.1"),
        fm("88.3", "Radio Belén"),
        fm("88.5", "Radio Antares"),
        fm("88.9", "BBN Radio"),
        fm("89.1", "Radio Cadena Eco"),
        fm("89.5", "Radio Las Talitas"),
        fm("89.7", "Radio Nuevo Tiempo"),
        fm("89.9", "Radio Mega"),
        fm("90.1", "Radio Prensa FM"),
        fm("90.5", "Radio 9 de Julio"),
        fm("90.7", "Radio María"),
        fm("90.9", "Radio Jerusalén"),
        fm("91.3", "Radio Activa", "https://streamall.alsolnet.com:443/radioactivahd"),
        fm("91.7", "Radio del Parque"),
        fm("91.9", "Radio El Fortín"),
        fm("92.1", "Aspen Tucumán", "https://frontend.radiohdvivo.com/931tucuman/live"),
        fm("92.7", "Mi Radio", "https://streaming01.shockmedia.com.ar:10352/stream|https://streaming01.shockmedia.com.ar:10352/;"),
        fm("92.9", "FM Romántica"),
        fm("93.1", "Wonder 93.1"),
        fm("93.3", "Play FM"),
        fm("93.5", "Metropolitana 93.5", "https://srv1.streamingradio.ar/8296/stream"),
        fm("93.9", "Radio Del Plata", "https://streaming.tucumanradio.com.ar:7066/;"),
        fm("94.3", "Gamba FM"),
        fm("94.7", "Radio Universidad"),
        fm("95.1", "FM Sensaciones", "https://chino.republicahosting.com:2318/live"),
        fm("95.5", "La 100", "nac:https://playerservices.streamtheworld.com/api/livestream-redirect/FM999_56.mp3"),
        fm("95.7", "FM Tiempo"),
        fm("95.9", "Radio Feel"),
        fm("96.1", "Los 40 Tucumán", "https://streaming01.shockmedia.com.ar:10307/stream"),
        fm("96.3", "FM DoKime"),
        fm("96.7", "Bésame FM"),
        fm("96.9", "FM Reskate"),
        fm("97.1", "La 97.1 Radio"),
        fm("97.3", "Radio Uno La Folk"),
        fm("97.5", "Radio Del Jardín"),
        fm("97.7", "Radio Rivadavia", "https://streaming.tucumanradio.com.ar:7068/stream|nac:https://playerservices.streamtheworld.com/api/livestream-redirect/RIVADAVIAAAC.aac"),
        fm("98.1", "Antena Ocho"),
        fm("98.3", "Radio Sport FM"),
        fm("98.7", "Radio Nacional Tucumán", "https://sa.mp3.icecast.magma.edge-access.net/sc_rad15"),
        fm("99.1", "FMI Radio", "https://ssl.radiosnethosting.com/index.php?port=9808/;stream.mp3"),
        fm("99.3", "Radio Latina Tropical"),
        fm("99.5", "Radio Mitre Tucumán", "nac:https://playerservices.streamtheworld.com/api/livestream-redirect/AM790_56AAC_SC"),
        fm("100.1", "Continental Tucumán", "https://ssl.radiosnethosting.com/index.php?port=9698|https://ssl.radiosnethosting.com/index.php?port=9698/;"),
        fm("100.5", "Metropolitana FM", "http://stream1.tucbbs.com.ar:8000/Metropolitana"),
        fm("100.9", "FM Mi Roisem"),
        fm("101.1", "Mía 101.1", "https://streaming1.locucionar.com/proxy/miatucuman?mp=/stream"),
        fm("101.3", "La Voz de Tucumán"),
        fm("101.5", "Radio 10", "nac:https://radio10.stweb.tv/radio10/live/playlist.m3u8"),
        fm("101.7", "FM Los Ralos", "https://serverssl.deradios.stream:7013/;"),
        fm("101.9", "Radio Urquiza"),
        fm("102.3", "Vale Radio", "nac:https://vale.stweb.tv/vale/live/playlist.m3u8"),
        fm("102.7", "LV7 Radio Tucumán", "https://stream.zeno.fm/hjb5if7bemvvv|http://01.solumedia.com.ar:8758/stream.m3u"),
        fm("102.9", "Radio Municipal"),
        fm("103.1", "Cadena Fuego"),
        fm("103.3", "Radio Bicentenario"),
        fm("103.5", "FM Láser"),
        fm("103.7", "Radio Libertad"),
        fm("103.9", "Radio San Alfonso"),
        fm("104.1", "La 104.1"),
        fm("104.3", "FM Dinámica"),
        fm("104.5", "Radio Contacto", "https://streaming.tucumanradio.com.ar:7208/;"),
        fm("104.7", "FM Dale"),
        fm("104.9", "Radio Conciencia"),
        fm("105.1", "LV12", "https://ssl.radiosnethosting.com/?port=8032|http://s1.radiosnethosting.com:8032/stream/;stream.mp3"),
        fm("105.3", "FM Joven"),
        fm("105.5", "Radio A"),
        fm("105.9", "Radio Top"),
        fm("106.1", "FM Delfín Gallo"),
        fm("106.5", "FM 2000"),
        fm("106.9", "Rock & Pop Tucumán", "https://streaming.tucumanradio.com.ar:7066/stream"),
        fm("107.1", "La Compañera", "https://tolkien.republicahosting.net:2361/live"),
        fm("107.3", "Radio Salvación"),
        fm("107.5", "Cadena 3"),
        fm("107.9", "FM Líder Echeverría")
    )
}
