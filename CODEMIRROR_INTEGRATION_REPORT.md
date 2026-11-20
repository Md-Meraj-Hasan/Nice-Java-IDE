# CodeMirror integration report

## Integrated package
- CodeMirror 5.65.16 from the uploaded official npm package archive.
- Compiled core: `web-preview/vendor/codemirror/lib/codemirror.js`
- Core stylesheet: `web-preview/vendor/codemirror/lib/codemirror.css`
- Full distributed `mode/`, `addon/`, `theme/`, and `keymap/` directories copied locally.
- Selected dark theme: `material-darker`.

## Web Preview changes
- Replaced the Web Preview textarea interaction with CodeMirror 5.
- Enabled line numbers, active-line highlighting, bracket matching, auto-close brackets, four-space indentation, and built-in history/undo/redo.
- Added language mode switching for Java, Kotlin, Kotlin Script, HTML, CSS, JavaScript, JSON, XML, Markdown, and Python.
- Preserved Open, Save, Save As, Find, Replace All, Run/HTML Preview, and existing toolbar IDs.
- All CodeMirror scripts and styles load from local relative paths. No CDN is used.

## Safety / scope
- The native Android editor in `MainActivity.java` remains its existing `EditText` and syntax-highlighter implementation.
- The native APK editor screen was deliberately not replaced or restructured.
- The standalone `web-preview/` is not currently packaged as the APK editor screen. This change upgrades the browser Web Preview only.

## Validation performed
- Existing and added Node tests: 6 passed, 0 failed.
- Syntax check: 185 bundled JavaScript files passed.
- Inline Web Preview application script: passed Node syntax check.
- CSS scan: 77 stylesheets; no external HTTP(S) imports or font URLs detected.
- No Android Gradle build or on-device WebView visual/runtime test was performed in this environment.


## Native editor performance optimization (follow-up)

The original native `afterTextChanged()` full-document highlighter call was removed.
`CodeSyntaxHighlighter` now caches per-line text and lexer state, recolors only edited lines, and propagates further only until unchanged line text plus incoming multiline state converge. A short 85 ms debounce coalesces rapid input. The native watcher captures the pre-edit line start from Android `Layout` to avoid scanning the entire file just to locate the edit.

Undo history was also changed from one full-document snapshot per character to one snapshot per 650 ms typing burst. Combined undo/redo history is capped at 30 snapshots and approximately 8 Mi characters.

### Follow-up validation
- `CodeSyntaxHighlighter.java` compiled with `javac -Xlint:all` against lightweight Android text/color stubs.
- Incremental output matched a fresh full highlight across Java, CSS, HTML, and Kotlin edits, including multiline comment/string state changes, line insertion, and line deletion (7 scenarios). A 3,000-line synthetic Java file was used for a single-line edit: only one line's spans were queried and four token spans were recreated.
- Web Preview tests are re-run separately.
- No Android Gradle build, device benchmark, or ANR trace was available in this environment; those remain required before claiming device-level performance is fully verified.
