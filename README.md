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


## Runtime reliability patch (2026-11)

The source patch improves three failure points:
- Java package/class detection now uses correctly escaped Java regular expressions, so packaged classes are launched with their fully-qualified class name.
- Native JDK command output is redirected to a temporary log file while the process runs. This prevents a full stdout pipe from blocking the reader before the timeout can be applied.
- Offline JAR installation copies to a temporary file, validates each JAR as a ZIP archive, then atomically activates it. Damaged cached JARs are replaced on the next install.
- The JDK is not marked installed unless `java`, `javac`, `lib/modules`, and executable permissions are present.
- `verifyOfflineBundles` makes the APK build fail early with a clear error if the OpenJDK archive, Kotlin compiler archive, or Java project JAR assets are missing.

### Required device validation
After installing the APK, use **Tools → Install OpenJDK 21**, then **View → Engine Status**. The runtime test must display `NICE_JAVA_RUNTIME_OK`. Next use **Tools → Install Java Project Libraries** and test a small Java file importing Gson. A successful Gradle build alone does not prove that native JDK binaries can execute on the target Android device.


## CodeMirror 5.65.16 — Web Preview editor

The browser-based `web-preview/` IDE now bundles the official CodeMirror 5.65.16 npm distribution locally:
- Compiled CodeMirror core and base CSS.
- Complete official language-mode, addon, theme, and keymap directories.
- The Web Preview loads Java/Kotlin, HTML, CSS, JavaScript, JSON, XML, Markdown, and Python modes from local files.
- Editor features enabled: line numbers, active-line highlight, bracket matching, auto-close brackets, indentation, built-in undo/redo, find/replace, and keyboard shortcuts.
- No CDN or runtime network request is required for CodeMirror.

The CodeMirror files are distributed under their included MIT license; see `web-preview/vendor/codemirror/LICENSE`.

**Scope note:** The Android native editing surface in `MainActivity.java` remains the existing native `EditText` plus syntax highlighters. It was deliberately not replaced with a WebView editor, so this CodeMirror addition applies to the standalone browser Web Preview and does not change the native APK editor UI or its compiler/runtime. The standalone `web-preview/` folder is not currently packaged as the APK's editor screen.


## Native editor performance patch — incremental highlighting and bounded undo

The native Android editor no longer runs a full-file syntax pass on every keystroke.

- Syntax coloring is debounced by 85 ms while typing.
- The highlighter tokenizes only the changed line range and stops as soon as the new line text and incoming multiline-comment/string state match the cached old line.
- Existing spans on unaffected lines are retained; spans are removed and recreated only for lines that need recoloring.
- Multiline block comments, HTML comments, and Kotlin/Java text-block strings carry lexer state across lines. If editing changes that state, highlighting propagates only until the state converges again.
- Changed-line offsets are captured from the editor's pre-edit Layout, avoiding a full-document line search on each key.
- Undo snapshots are grouped across a 650 ms typing burst instead of copying and storing the entire document for every character.
- Undo/redo history is capped at 30 snapshots and approximately 8 Mi characters of combined history (with at least the most recent snapshot retained).

The native editor remains an Android `EditText`; the Web Preview CodeMirror editor is unchanged by this native performance patch. A 3,000-line synthetic Java document was also checked: a one-line edit queried spans once and created four token spans, rather than rebuilding spans for the whole document.

### Validation
- Compiled `CodeSyntaxHighlighter.java` against lightweight Android text/color stubs with `javac -Xlint:all`.
- Ran incremental-vs-full highlighting comparisons for ordinary edits, multiline-comment state changes, line insertion, and line deletion.
- Re-ran the Web Preview Node tests.
- Android Gradle build and physical-device typing/ANR profiling still need to be run in Android Studio / on the target phone.
