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

**Important:** Selecting this JDK does not yet mean it is integrated or proven to execute from this APK. Android executable-loading restrictions, app-private extraction, process launching, and device API compatibility must be validated before the IDE can claim offline compile/run support. Java SE desktop APIs are not automatically available on Android.

## Current status
This repository contains the Android application scaffold and editor/workspace foundation. Full JDK packaging, compiler/runtime process integration, multi-file compilation, diagnostics, and interactive console input remain implementation and device-testing milestones. The app must not claim to compile or execute Java until those parts have been integrated and tested on-device.

## Compatibility note
The selected JDK archive targets ARM64 Android/Bionic. The initial app build supports Android API 24+, but the JDK's minimum supported Android API level has not yet been independently verified. Desktop-only Java APIs and arbitrary native libraries may not run on Android.

## Build
Open this repository in Android Studio and build the app. The project uses a standard Android Gradle build.

## Planned milestones
1. Android shell, workspace, editor tabs, file open/save.
2. Java syntax highlighting, navigation, find/replace, undo/redo.
3. Bundle OpenJDK 21 and validate safe execution on supported Android versions.
4. Multi-file/package compilation, diagnostics with line numbers, console and interactive stdin.
5. Device tests, sample projects, and release APK workflow.

## License
The application license and all bundled dependency licenses/attributions must be documented before release.
