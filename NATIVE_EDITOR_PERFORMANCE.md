# Native editor performance patch

## What was expensive before

`afterTextChanged()` called a full-document highlighter after every edit. The old path also converted the complete `Editable` to a `String` and retained a full-document undo snapshot for each keypress.

## What changed

- `CodeSyntaxHighlighter` now caches each line's source text and incoming/outgoing lexer state.
- A normal edit re-tokenizes only the changed line. It stops at the first following line whose source text and incoming multiline state match the cached version.
- If a multiline comment or Java/Kotlin text block opens/closes, recoloring propagates only until the lexical state converges.
- Existing spans outside the changed lines are preserved.
- The cache is updated in place for normal same-line edits; it does not allocate a replacement cache for every keypress.
- The changed line and its start offset are captured from the pre-edit Android `Layout`.
- Highlighting is coalesced with an 85 ms debounce.
- Undo snapshots are grouped over a 650 ms typing burst and bounded to 30 entries / approximately 8 Mi characters combined.

## Validation performed

- `CodeSyntaxHighlighter.java` compiled with `javac -Xlint:all` against lightweight Android text/color stubs.
- Incremental span output matched a fresh full highlight in seven Java, CSS, HTML, and Kotlin scenarios, including multiline state changes and line insertions/deletions.
- In a synthetic 3,000-line Java document, a one-line edit performed one span-range query and created four replacement token spans.
- Existing Web Preview Node tests: 6 passed, 0 failed.

## Still required

The environment did not contain an Android SDK or Gradle executable/wrapper, so no APK build or physical-device benchmark was run. Install the APK on the target phone and test long Java/CSS/JS files, rapid typing, paste, multiline comments, undo/redo, and file switching. Use Android Studio Profiler / Logcat to check frame time, memory, and ANR traces.
