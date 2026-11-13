# Reglas de R8. Compose, Media3 y AndroidX traen sus propias reglas.
# Servicios declarados en el manifiesto (R8 ya los conserva, esto es por las dudas).
-keep class com.axel.onixlauncher.RadioService { *; }
-keep class com.axel.onixlauncher.MediaListenerService { *; }
# Conscrypt (v0.43.0): el código nativo llama a sus clases por nombre; se conservan enteras.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
# MapLibre (v0.47.0): trae sus reglas; esto evita avisos de clases opcionales que no se usan.
-dontwarn com.mapbox.**
-keep class com.mapbox.mapboxsdk.** { *; }
-keep class com.mapbox.geojson.** { *; }

# v0.56.0 (ADR-0012): motor de CarLink (Android Auto y CarPlay). Igual que CarLink: sin renombrar
# (los diagnósticos de los motores filtran por nombre de paquete) y sin fusionar sus clases. Las
# reglas de cada motor vienen en sus propios archivos (consumer rules).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep,allowshrinking,allowobfuscation,allowaccessmodification class com.carlink.**
-keep,allowshrinking,allowobfuscation,allowaccessmodification class com.shilapi.**
-keep,allowshrinking,allowobfuscation,allowaccessmodification class com.andrerinas.**
-keep,allowshrinking,allowobfuscation,allowaccessmodification class com.byd.spi.**
