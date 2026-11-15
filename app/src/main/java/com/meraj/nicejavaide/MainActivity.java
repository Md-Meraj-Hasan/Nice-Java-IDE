package com.meraj.nicejavaide;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN = 101;
    private static final int REQUEST_SAVE_AS = 102;
    private static final String STARTER = "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello from Nice Java IDE!\");\n" +
            "    }\n" +
            "}\n";

    private EditText editor;
    private TextView status;
    private TextView fileLabel;
    private LinearLayout tabBar;
    private final List<File> openTabs = new ArrayList<>();
    private File workspace;
    private File currentFile;
    private Uri currentUri;
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private String lastEditorText = "";
    private boolean restoringEditor = false;
    private static final int HISTORY_LIMIT = 100;
    private File jdkHome;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        workspace = new File(getFilesDir(), "workspace");
        if (!workspace.exists() && !workspace.mkdirs()) {
            Toast.makeText(this, "Workspace could not be created", Toast.LENGTH_LONG).show();
        }
        currentFile = new File(workspace, "Main.java");
        buildUi();
        loadCurrentFile();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(0xFF111827);

        TextView heading = new TextView(this);
        heading.setText("Nice Java IDE");
        heading.setTextColor(0xFFF9FAFB);
        heading.setTextSize(21);
        heading.setPadding(dp(4), dp(8), dp(4), dp(10));
        root.addView(heading);

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        addTool(toolbar, "New", v -> newFile());
        addTool(toolbar, "Files", v -> showWorkspaceFiles());
        addTool(toolbar, "Open", v -> openDocument());
        addTool(toolbar, "Save", v -> saveFile());
        addTool(toolbar, "Save As", v -> saveAs());
        addTool(toolbar, "Find", v -> showFind());
        addTool(toolbar, "Replace", v -> showReplace());
        addTool(toolbar, "Undo", v -> undo());
        addTool(toolbar, "Redo", v -> redo());
        addTool(toolbar, "Device", v -> showDeviceInfo());
        addTool(toolbar, "Install JDK", v -> installBundledJdk());
        addTool(toolbar, "Run", v -> showEngineStatus());
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll);

        fileLabel = new TextView(this);
        fileLabel.setTextColor(0xFF93C5FD);
        fileLabel.setTextSize(12);
        fileLabel.setPadding(dp(5), dp(12), dp(5), dp(6));
        root.addView(fileLabel);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabsScroll.addView(tabBar);
        root.addView(tabsScroll);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(14);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);
        editor.setTextColor(0xFFE5E7EB);
        editor.setHintTextColor(0xFF6B7280);
        editor.setHint("Write Java code here...");
        editor.setBackgroundColor(0xFF0B1220);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setHorizontallyScrolling(true);
        editor.setMinLines(18);
        editor.setTextIsSelectable(true);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                String current = editable.toString();
                if (!restoringEditor && !current.equals(lastEditorText)) {
                    undoStack.push(lastEditorText);
                    while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
                    redoStack.clear();
                    lastEditorText = current;
                }
                JavaSyntaxHighlighter.apply(editable);
            }
        });
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        editorParams.topMargin = dp(8);
        root.addView(editor, editorParams);

        status = new TextView(this);
        status.setTextColor(0xFFFBBF24);
        status.setTextSize(11);
        status.setPadding(dp(5), dp(10), dp(5), dp(4));
        root.addView(status);
        setContentView(root);
        updateFileLabel();
        jdkHome = new File(getFilesDir(), "openjdk-21");
        status.setText(isJdkInstalled() ? "OpenJDK 21 files installed" : "Full OpenJDK 21 bundled • ready to install offline");
    }

    private void setEditorText(String value) {
        restoringEditor = true;
        editor.setText(value);
        lastEditorText = value;
        undoStack.clear();
        redoStack.clear();
        restoringEditor = false;
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            status.setText("Nothing to undo");
            return;
        }
        String current = editor.getText().toString();
        redoStack.push(current);
        String previous = undoStack.pop();
        restoringEditor = true;
        editor.setText(previous);
        editor.setSelection(editor.length());
        lastEditorText = previous;
        restoringEditor = false;
        status.setText("Undo");
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            status.setText("Nothing to redo");
            return;
        }
        String current = editor.getText().toString();
        undoStack.push(current);
        String next = redoStack.pop();
        restoringEditor = true;
        editor.setText(next);
        editor.setSelection(editor.length());
        lastEditorText = next;
        restoringEditor = false;
        status.setText("Redo");
    }

    private void addTool(LinearLayout bar, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(action);
        bar.addView(button);
    }

    private void newFile() {
        currentFile = new File(workspace, "Untitled.java");
        currentUri = null;
        addOpenTab(currentFile);
        setEditorText("public class Untitled {\n    public static void main(String[] args) {\n    }\n}\n");
        updateFileLabel();
        status.setText("New file • use Save or Save As");
    }

    private void showWorkspaceFiles() {
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        collectJavaFiles(workspace, files);
        java.util.Collections.sort(files, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        String[] names = new String[files.size() + 1];
        names[0] = "+  Create new Java file";
        for (int i = 0; i < files.size(); i++) {
            String relative = workspace.toURI().relativize(files.get(i).toURI()).getPath();
            names[i + 1] = relative;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Workspace • Java files")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) promptNewWorkspaceFile();
                    else openWorkspaceFile(files.get(which - 1));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void collectJavaFiles(File folder, java.util.List<File> output) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectJavaFiles(child, output);
            else if (child.getName().endsWith(".java")) output.add(child);
        }
    }

    private void openWorkspaceFile(File file) {
        try {
            saveCurrentBeforeSwitch();
            currentFile = file;
            currentUri = null;
            addOpenTab(file);
            setEditorText(readText(file));
            updateFileLabel();
            status.setText("Opened: " + workspace.toURI().relativize(file.toURI()).getPath());
        } catch (Exception e) {
            status.setText("Open error: " + e.getMessage());
        }
    }

    private void promptNewWorkspaceFile() {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Example: Main.java or app/Utils.java");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Create Java file")
                .setView(nameInput)
                .setPositiveButton("Create", (dialog, which) -> {
                    String relative = nameInput.getText().toString().trim();
                    if (relative.isEmpty()) return;
                    if (!relative.endsWith(".java")) relative += ".java";
                    File target = new File(workspace, relative).getAbsoluteFile();
                    try {
                        String rootPath = workspace.getCanonicalPath() + File.separator;
                        String targetPath = target.getCanonicalPath();
                        if (!targetPath.startsWith(rootPath)) throw new IllegalArgumentException("Invalid file path");
                        if (target.exists()) throw new IllegalArgumentException("File already exists");
                        writeText(target, "public class " + target.getName().replace(".java", "") +
                                " {\n    public static void main(String[] args) {\n    }\n}\n");
                        openWorkspaceFile(target);
                    } catch (Exception e) {
                        status.setText("Create error: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCurrentBeforeSwitch() throws Exception {
        if (currentUri != null) writeUri(currentUri, editor.getText().toString());
        else if (currentFile != null) writeText(currentFile, editor.getText().toString());
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/x-java", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_OPEN);
    }

    private void saveAs() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/x-java");
        intent.putExtra(Intent.EXTRA_TITLE, currentFile == null ? "Main.java" : currentFile.getName());
        startActivityForResult(intent, REQUEST_SAVE_AS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            if (requestCode == REQUEST_OPEN) {
                currentUri = uri;
                currentFile = new File(workspace, "External.java");
                setEditorText(readUri(uri));
                status.setText("Opened external Java file");
                updateFileLabel();
            } else if (requestCode == REQUEST_SAVE_AS) {
                currentUri = uri;
                writeUri(uri, editor.getText().toString());
                status.setText("Saved using Android file picker");
                updateFileLabel();
            }
        } catch (SecurityException ignored) {
            handlePickedUri(requestCode, uri);
        } catch (Exception e) {
            status.setText("File error: " + e.getMessage());
        }
    }

    private void handlePickedUri(int requestCode, Uri uri) {
        try {
            if (requestCode == REQUEST_OPEN) {
                currentUri = uri;
                currentFile = new File(workspace, "External.java");
                setEditorText(readUri(uri));
                status.setText("Opened external Java file");
            } else if (requestCode == REQUEST_SAVE_AS) {
                currentUri = uri;
                writeUri(uri, editor.getText().toString());
                status.setText("Saved using Android file picker");
            }
            updateFileLabel();
        } catch (Exception e) {
            status.setText("File error: " + e.getMessage());
        }
    }

    private void loadCurrentFile() {
        try {
            if (!currentFile.exists()) writeText(currentFile, STARTER);
            addOpenTab(currentFile);
            setEditorText(readText(currentFile));
            updateFileLabel();
            status.setText("Opened: " + currentFile.getName());
        } catch (Exception e) {
            status.setText("Open error: " + e.getMessage());
        }
    }

    private void saveFile() {
        try {
            if (currentUri != null) {
                writeUri(currentUri, editor.getText().toString());
                status.setText("Saved external file");
            } else {
                writeText(currentFile, editor.getText().toString());
                status.setText("Saved: " + currentFile.getName());
            }
            Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            status.setText("Save error: " + e.getMessage());
        }
    }

    private void showFind() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Find text");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Find in current file")
                .setView(query)
                .setPositiveButton("Find", (dialog, which) -> {
                    String needle = query.getText().toString();
                    if (needle.isEmpty()) return;
                    String source = editor.getText().toString();
                    int start = Math.max(0, editor.getSelectionStart());
                    int found = source.indexOf(needle, start);
                    if (found < 0) found = source.indexOf(needle);
                    if (found >= 0) {
                        editor.requestFocus();
                        editor.setSelection(found, found + needle.length());
                        status.setText("Found: " + needle);
                    } else {
                        status.setText("Not found: " + needle);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showReplace() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), 0);

        EditText findInput = new EditText(this);
        findInput.setSingleLine(true);
        findInput.setHint("Text to find");
        panel.addView(findInput);

        EditText replaceInput = new EditText(this);
        replaceInput.setSingleLine(true);
        replaceInput.setHint("Replace with");
        panel.addView(replaceInput);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Find and replace")
                .setView(panel)
                .setPositiveButton("Replace all", (dialog, which) -> {
                    String find = findInput.getText().toString();
                    if (find.isEmpty()) {
                        status.setText("Enter text to find");
                        return;
                    }
                    String source = editor.getText().toString();
                    int count = 0;
                    int from = 0;
                    while ((from = source.indexOf(find, from)) >= 0) {
                        count++;
                        from += find.length();
                    }
                    if (count == 0) {
                        status.setText("No matches found");
                        return;
                    }
                    String updated = source.replace(find, replaceInput.getText().toString());
                    int cursor = Math.max(0, editor.getSelectionStart());
                    editor.setText(updated);
                    editor.setSelection(Math.min(cursor, editor.length()));
                    status.setText("Replaced " + count + " occurrence(s)");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isJdkInstalled() {
        File java = new File(jdkHome, "bin/java");
        File javac = new File(jdkHome, "bin/javac");
        return java.isFile() && javac.isFile();
    }

    private void installBundledJdk() {
        if (isJdkInstalled()) {
            status.setText("OpenJDK 21 is already extracted");
            showJdkDetails();
            return;
        }
        if (!"arm64-v8a".equals(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "")) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("ARM64 device required")
                    .setMessage("This bundled OpenJDK build targets Android ARM64 (arm64-v8a).")
                    .setPositiveButton("OK", null).show();
            return;
        }
        long free = new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
        if (free < 1200L * 1024L * 1024L) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Storage space is low")
                    .setMessage("Please keep at least 1.2 GB free in internal storage before extracting the full JDK.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Install bundled OpenJDK 21?")
                .setMessage("The full ARM64 JDK archive is already inside this APK. Extraction needs substantial internal storage and may take several minutes. No internet download is required on the phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", (dialog, which) -> extractBundledJdk())
                .show();
    }

    private void extractBundledJdk() {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("Installing OpenJDK 21");
        progress.setMessage("Extracting full offline toolchain…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            File staging = new File(getFilesDir(), "openjdk-21-installing");
            boolean success = false;
            String error = null;
            try {
                deleteTree(staging);
                if (!staging.mkdirs()) throw new IllegalStateException("Cannot create JDK folder");
                try (InputStream raw = getAssets().open("jdk21-android.tar.gz");
                     GzipCompressorInputStream gzip = new GzipCompressorInputStream(raw);
                     TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
                    TarArchiveEntry entry;
                    byte[] buffer = new byte[64 * 1024];
                    while ((entry = tar.getNextTarEntry()) != null) {
                        File output = new File(staging, entry.getName()).getCanonicalFile();
                        String base = staging.getCanonicalPath() + File.separator;
                        if (!output.getPath().startsWith(base) && !output.equals(staging)) {
                            throw new SecurityException("Unsafe path in JDK archive");
                        }
                        if (entry.isDirectory()) {
                            if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create " + entry.getName());
                        } else if (entry.isSymbolicLink()) {
                            File parent = output.getParentFile();
                            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create link folder");
                            Path linkTarget = Paths.get(entry.getLinkName());
                            if (linkTarget.isAbsolute()) throw new SecurityException("Absolute symlink in JDK archive");
                            Files.createSymbolicLink(output.toPath(), linkTarget);
                        } else if (entry.isFile()) {
                            File parent = output.getParentFile();
                            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create " + parent);
                            try (FileOutputStream out = new FileOutputStream(output)) {
                                int count;
                                while ((count = tar.read(buffer)) != -1) out.write(buffer, 0, count);
                            }
                            if ((entry.getMode() & 0111) != 0) output.setExecutable(true, false);
                        }
                    }
                }
                File home = findJdkHome(staging, 0);
                if (home == null) throw new IllegalStateException("JDK bin/java and bin/javac were not found in the archive");
                File destination = jdkHome;
                deleteTree(destination);
                if (!home.equals(staging)) {
                    if (!home.renameTo(destination)) throw new IllegalStateException("Cannot move extracted JDK into place");
                    deleteTree(staging);
                } else if (!staging.renameTo(destination)) {
                    throw new IllegalStateException("Cannot finalize JDK installation");
                }
                jdkHome = destination;
                if (!isJdkInstalled()) throw new IllegalStateException("JDK compiler files are missing");
                success = true;
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                deleteTree(staging);
            }
            final boolean installed = success;
            final String failure = error;
            runOnUiThread(() -> {
                progress.dismiss();
                if (installed) {
                    status.setText("OpenJDK 21 extracted • native runtime test pending");
                    showJdkDetails();
                } else {
                    status.setText("JDK extraction failed");
                    new android.app.AlertDialog.Builder(this).setTitle("JDK installation failed")
                            .setMessage(failure == null ? "Unknown error" : failure)
                            .setPositiveButton("OK", null).show();
                }
            });
        }, "Jdk21-Extractor").start();
    }

    private File findJdkHome(File folder, int depth) {
        if (folder == null || depth > 4) return null;
        File java = new File(folder, "bin/java");
        File javac = new File(folder, "bin/javac");
        if (java.isFile() && javac.isFile()) return folder;
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File found = findJdkHome(child, depth + 1);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private void showJdkDetails() {
        String message = "JDK: OpenJDK 21.0.4 ARM64/Bionic" +
                "\\njava: " + new File(jdkHome, "bin/java").isFile() +
                "\\njavac: " + new File(jdkHome, "bin/javac").isFile() +
                "\\njar: " + new File(jdkHome, "bin/jar").isFile() +
                "\\njavadoc: " + new File(jdkHome, "bin/javadoc").isFile() +
                "\\njshell: " + new File(jdkHome, "bin/jshell").isFile() +
                "\\n\\nFiles extracted. Native process execution still needs device verification.";
        new android.app.AlertDialog.Builder(this).setTitle("OpenJDK 21")
                .setMessage(message).setPositiveButton("OK", null).show();
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private void showDeviceInfo() {
        String[] abis = Build.SUPPORTED_ABIS;
        StringBuilder abiText = new StringBuilder();
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) abiText.append(", ");
            abiText.append(abis[i]);
        }

        StatFs storage = new StatFs(getFilesDir().getAbsolutePath());
        long freeBytes = storage.getAvailableBytes();
        String freeSpace = android.text.format.Formatter.formatShortFileSize(this, freeBytes);
        boolean arm64 = false;
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi)) arm64 = true;
        }

        String report = "Android API: " + Build.VERSION.SDK_INT +
                "\\nSupported ABIs: " + abiText +
                "\\n64-bit ARM available: " + (arm64 ? "Yes" : "No") +
                "\\nApp internal free space: " + freeSpace +
                "\\n\\nOpenJDK 21 bundle is not installed yet. This check only reports device details; it does not prove that native JDK executables can run.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Device compatibility")
                .setMessage(report)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showEngineStatus() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("OpenJDK 21 integration in progress")
                .setMessage("The editor can open and save Java files. Offline javac/java execution will be enabled after the bundled ARM64 JDK passes Android runtime checks.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void addOpenTab(File file) {
        if (file == null) return;
        for (File existing : openTabs) {
            if (existing.equals(file)) {
                refreshTabs();
                return;
            }
        }
        openTabs.add(file);
        refreshTabs();
    }

    private void refreshTabs() {
        if (tabBar == null) return;
        tabBar.removeAllViews();
        for (File file : openTabs) {
            Button tab = new Button(this);
            tab.setText(file.getName());
            tab.setAllCaps(false);
            tab.setTextSize(11);
            tab.setMinHeight(dp(36));
            tab.setPadding(dp(8), 0, dp(8), 0);
            boolean active = currentUri == null && file.equals(currentFile);
            tab.setTextColor(active ? 0xFFFFFFFF : 0xFF9CA3AF);
            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    active ? 0xFF1D4ED8 : 0xFF1F2937));
            tab.setOnClickListener(v -> openWorkspaceFile(file));
            tabBar.addView(tab);
        }
    }

    private void updateFileLabel() {
        if (fileLabel == null) return;
        String name = currentUri != null ? "External Java file" :
                (currentFile == null ? "Untitled.java" : currentFile.getName());
        fileLabel.setText("WORKSPACE  /  " + name);
        refreshTabs();
    }

    private String readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Cannot open selected file");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private void writeUri(Uri uri, String value) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("Cannot write selected file");
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private void writeText(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create workspace folder");
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
