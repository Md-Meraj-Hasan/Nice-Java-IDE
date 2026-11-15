# Nice Java IDE

A standalone, offline-first Android Java IDE project.

## Project goals
- Separate APK and Android application identity from NiceCoding.
- Local workspace with Java source files and package folders.
- Editor shell with Java syntax highlighting, search/replace, tabs, and file actions.
- Full offline OpenJDK 21 ARM64 toolchain, including compiler and runtime tools.
- Diagnostics and console output presented inside the app.
- No network dependency at runtime. INTERNET permission is not required for normal IDE operation.

## Confirmed JDK target
The selected toolchain is OpenJDK 21.0.4 for Android ARM64 (AArch64/Bionic), from the MobileStudio-AndroidIDE release:
https://github.com/MobileStudio-AndroidIDE/MobileStudio_AndroidIDE/releases/tag/jdk21-android-21.0.4

The upstream archive is approximately 312 MB compressed. It includes tools such as `java`, `javac`, `jar`, `javadoc`, `jshell`, `keytool`, `jlink`, and `jmod`. The upstream build is headless and licensed under GPLv2 with Classpath Exception; its license and attribution files must accompany redistribution.

**Packaging update:** The Android Gradle build now downloads this exact release archive, verifies its SHA-256, and packages the compressed full JDK archive into the APK assets. The app has an offline extraction flow for the bundled archive, including executable permission and link restoration. This makes the APK very large (the upstream compressed archive alone is about 312 MB), and extraction needs substantial additional internal storage.\n\n**Important:** Bundling and extracting the JDK does not yet prove its native executables can run on every Android device. Android executable-loading restrictions, app-private execution, process launching, and device API compatibility must be validated before the IDE can claim compile/run support. Java SE desktop APIs are not automatically available on Android.

## Offline Java project libraries
The APK build also bundles a curated Java SE library pack as JAR assets. The app copies these JARs into its private storage when the user taps **Libraries** (or when a project is run), and adds them to both the javac compile classpath and Java runtime classpath. No library download is needed on the phone.

Included libraries:
- Gson and Jackson Databind (JSON)
- OkHttp (HTTP networking; includes its transitive dependencies)
- Apache Commons Lang, IO, CSV, and Codec
- Bouncy Castle provider (cryptography)
- JUnit 4 (unit testing; includes Hamcrest)
- H2 Database (embedded Java database/JDBC)
- SLF4J API (logging facade)

These are Java SE project libraries, not Android app SDK APIs. Spring Boot, JavaFX, LibGDX, and Android framework libraries are not part of this starter pack; they require separate framework-specific setup and compatibility testing.

## Current status
The repository contains the editor/workspace foundation, a CI-time full JDK archive bundle, offline JDK extraction, and a curated offline Java JAR pack connected to the project's compiler/runtime classpaths. Native java/javac execution, dependency compatibility on the target phone, interactive console input, and full library smoke tests still require on-device validation.

## Compatibility note
The selected JDK archive targets ARM64 Android/Bionic. The initial app build supports Android API 24+, but the JDK's minimum supported Android API level has not yet been independently verified. Desktop-only Java APIs and arbitrary native libraries may not run on Android.

## Build
Open this repository in Android Studio and build the app. The project uses a standard Android Gradle build.

## Planned milestones
1. Android shell, workspace, editor tabs, file open/save.
2. Java syntax highlighting, navigation, find/replace, undo/redo.
3. Bundle and extract OpenJDK 21; validate safe native execution on supported Android versions.
4. Multi-file/package compilation, diagnostics with line numbers, console and interactive stdin.
5. Device tests, sample projects, and release APK workflow.

## License
The application license and all bundled dependency licenses/attributions must be documented before release.
