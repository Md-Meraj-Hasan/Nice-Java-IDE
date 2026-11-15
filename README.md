# Nice Java IDE

A standalone, offline-first Android Java IDE project.

## Project goals
- Separate APK and Android application identity from NiceCoding.
- Local workspace with Java source files and package folders.
- Editor shell with Java syntax highlighting, search/replace, tabs, and file actions.
- Build/run pipeline designed for a bundled offline Java compiler targeting Android runtime.
- Diagnostics and console output presented inside the app.
- No network dependency at runtime. INTERNET permission is not required for normal IDE operation.

## Current status
This repository starts with the Android application scaffold and editor/workspace foundation. The Java compiler/runtime integration is intentionally a separate milestone and is **not yet implemented**. The app must not claim to compile or execute Java until that engine has been integrated and tested on-device.

## Compatibility note
Android is not a desktop JDK. Java programs that depend on desktop-only APIs or arbitrary native libraries may not run. The intended execution target is Android's runtime, with supported Java language/library features documented as the engine is completed.

## Build
Open the `android/` folder in Android Studio and build the app. The initial scaffold uses a standard Android Gradle project.

## Planned milestones
1. Android shell, workspace, editor tabs, file open/save.
2. Java syntax highlighting, navigation, find/replace, undo/redo.
3. Offline compiler and Android-compatible execution engine.
4. Multi-file/package builds, diagnostics with line numbers, console and interactive stdin.
5. Device tests, sample projects, and release APK workflow.

## License
To be selected before bundling compiler/editor dependencies. Check each dependency's license and attribution requirements.
