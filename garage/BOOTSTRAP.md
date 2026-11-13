# OnixGarage — Bootstrap Instructions

## Gradle Wrapper (required before first sync)

The files `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are NOT committed
because `gradle-wrapper.jar` is a binary and must be generated, not hand-crafted.

Run ONE of the following from the project root after installing JDK 17:

### Option A — System Gradle on PATH (preferred)

```
gradle wrapper --gradle-version 8.9
```

On macOS/Linux, also run:

```
chmod +x gradlew
```

### Option B — Android Studio

Open the project root in Android Studio. The IDE will detect the missing wrapper and offer
to download it automatically during the first Gradle sync.

## JDK Requirement

Gradle 8.9 requires JDK 17 or higher to run (JDK 21 is also supported).
The project compiles against JavaVersion.VERSION_17 bytecode.

Download JDK 17: https://adoptium.net/temurin/releases/?version=17
