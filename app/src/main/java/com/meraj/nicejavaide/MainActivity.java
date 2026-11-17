package com.meraj.nicejavaide;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.os.Build;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.webkit.WebView;
import android.webkit.WebSettings;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN = 101;
    private static final int REQUEST_SAVE_AS = 102;
    private static final int REQUEST_WORKSPACE_TREE = 103;
    private static final String STORAGE_PREFS = "nicejava_storage";
    private static final String STARTER = "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello from Nice Java IDE!\");\n" +
            "    }\n" +
            "}\n";

    private EditText editor;
    private WebView ideWebView;
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
        if (getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("tree_uri", null) == null) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Setup Downloads / NiceJava")
                    .setMessage("Choose your Downloads folder once. NiceJava will create Java/Projects, Kotlin/Projects, HTML_CSS and Libraries folders and remember access.")
                    .setPositiveButton("Setup now", (dialog, which) -> setupJavaStorage())
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void buildUi() {
        // Keep the native EditText as the bridge to the existing Java/Kotlin
        // compiler and file-management code. The visible editor is CodeMirror.
        editor = new EditText(this);
        editor.setText(STARTER);
        editor.setVisibility(View.GONE);
        status = new TextView(this);
        status.setVisibility(View.GONE);

        ideWebView = new WebView(this);
        WebSettings settings = ideWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        ideWebView.setBackgroundColor(0xFFF2F5FA);
        ideWebView.setWebChromeClient(new android.webkit.WebChromeClient());
        ideWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String code = editor == null ? STARTER : editor.getText().toString();
                view.evaluateJavascript("window.setEditorCode && window.setEditorCode(" +
                        org.json.JSONObject.quote(code) + ")", null);
                String name = currentFile == null ? "Main.java" : currentFile.getName();
                view.evaluateJavascript("window.setFileName && window.setFileName(" +
                        org.json.JSONObject.quote(name) + ")", null);
            }
        });
        ideWebView.addJavascriptInterface(new NiceIdeBridge(), "NativeIDE");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F5FA);
        root.addView(ideWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(editor, new LinearLayout.LayoutParams(1, 1));
        root.addView(status, new LinearLayout.LayoutParams(1, 1));
        setContentView(root);

        jdkHome = new File(getFilesDir(), "openjdk-21");
        ideWebView.loadUrl("file:///android_asset/ide/index.html");
    }

    public final class NiceIdeBridge {
        @android.webkit.JavascriptInterface
        public String initialCode() {
            return editor == null ? STARTER : editor.getText().toString();
        }

        @android.webkit.JavascriptInterface
        public String fileName() {
            return currentFile == null ? "Main.java" : currentFile.getName();
        }

        @android.webkit.JavascriptInterface
        public void changed(String code) {
            runOnUiThread(() -> {
                if (editor == null) return;
                restoringEditor = true;
                editor.setText(code == null ? "" : code);
                lastEditorText = editor.getText().toString();
                restoringEditor = false;
            });
        }

        @android.webkit.JavascriptInterface
        public void run() {
            runOnUiThread(() -> {
                try {
                    saveCurrentBeforeSwitch();
                    runCurrentProject();
                } catch (Exception e) {
                    showConsoleResult(false, "Unable to prepare code for running: " + e.getMessage());
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void save() {
            runOnUiThread(() -> saveFile());
        }

        @android.webkit.JavascriptInterface
        public void open() {
            runOnUiThread(() -> openDocument());
        }

        @android.webkit.JavascriptInterface
        public void saveAs() {
            runOnUiThread(() -> MainActivity.this.saveAs());
        }

        @android.webkit.JavascriptInterface
        public void newFile() {
            runOnUiThread(() -> MainActivity.this.newFile());
        }

        @android.webkit.JavascriptInterface
        public void files() {
            runOnUiThread(() -> showWorkspaceFiles());
        }

        @android.webkit.JavascriptInterface
        public void setupFolders() {
            runOnUiThread(() -> setupJavaStorage());
        }

        @android.webkit.JavascriptInterface
        public void installJdk() {
            runOnUiThread(() -> installBundledJdk());
        }

        @android.webkit.JavascriptInterface
        public void installLibraries() {
            runOnUiThread(() -> installJavaProjectLibraries());
        }

        @android.webkit.JavascriptInterface
        public void tools() {
            runOnUiThread(() -> showToolsDialog());
        }

        @android.webkit.JavascriptInterface
        public void newKotlin() {
            runOnUiThread(() -> promptNewKotlinFile());
        }
    }

    private void setEditorText(String value) {
        restoringEditor = true;
        editor.setText(value);
        lastEditorText = value;
        undoStack.clear();
        redoStack.clear();
        restoringEditor = false;
        if (ideWebView != null) {
            String safe = org.json.JSONObject.quote(value == null ? "" : value);
            ideWebView.evaluateJavascript("window.setEditorCode && window.setEditorCode(" + safe + ")", null);
        }
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

    private void addMenuButton(LinearLayout bar, String title, int menuId) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(0xFFF9FAFB);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1F2937));
        button.setMinWidth(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(view -> showIdeMenu(view, menuId));
        bar.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
    }

    private void showIdeMenu(View anchor, int menuId) {
        PopupMenu popup = new PopupMenu(this, anchor);
        switch (menuId) {
            case 1: // File
                popup.getMenu().add("New File").setOnMenuItemClickListener(item -> { newFile(); return true; });
                popup.getMenu().add("New Kotlin File").setOnMenuItemClickListener(item -> { promptNewKotlinFile(); return true; });
                popup.getMenu().add("Open File…").setOnMenuItemClickListener(item -> { openDocument(); return true; });
                popup.getMenu().add("Workspace Files").setOnMenuItemClickListener(item -> { showWorkspaceFiles(); return true; });
                popup.getMenu().add("Save").setOnMenuItemClickListener(item -> { saveFile(); return true; });
                popup.getMenu().add("Save As…").setOnMenuItemClickListener(item -> { saveAs(); return true; });
                popup.getMenu().add("Setup Folders…").setOnMenuItemClickListener(item -> { setupJavaStorage(); return true; });
                break;
            case 2: // Edit
                popup.getMenu().add("Undo").setOnMenuItemClickListener(item -> { undo(); return true; });
                popup.getMenu().add("Redo").setOnMenuItemClickListener(item -> { redo(); return true; });
                popup.getMenu().add("Find…").setOnMenuItemClickListener(item -> { showFind(); return true; });
                popup.getMenu().add("Replace…").setOnMenuItemClickListener(item -> { showReplace(); return true; });
                popup.getMenu().add("Select All").setOnMenuItemClickListener(item -> { editor.selectAll(); return true; });
                break;
            case 3: // Search
                popup.getMenu().add("Find in Current File…").setOnMenuItemClickListener(item -> { showFind(); return true; });
                popup.getMenu().add("Find and Replace…").setOnMenuItemClickListener(item -> { showReplace(); return true; });
                popup.getMenu().add("Workspace Files").setOnMenuItemClickListener(item -> { showWorkspaceFiles(); return true; });
                break;
            case 4: // View
                popup.getMenu().add("Workspace Explorer").setOnMenuItemClickListener(item -> { showWorkspaceFiles(); return true; });
                popup.getMenu().add("Device / Runtime Info").setOnMenuItemClickListener(item -> { showDeviceInfo(); return true; });
                popup.getMenu().add("Engine Status").setOnMenuItemClickListener(item -> { showEngineStatus(); return true; });
                popup.getMenu().add("JDK Details").setOnMenuItemClickListener(item -> { showJdkDetails(); return true; });
                break;
            case 5: // Tools
                popup.getMenu().add("Install OpenJDK 21").setOnMenuItemClickListener(item -> { installBundledJdk(); return true; });
                popup.getMenu().add("Install Java Project Libraries").setOnMenuItemClickListener(item -> { installJavaProjectLibraries(); return true; });
                popup.getMenu().add("Java: java").setOnMenuItemClickListener(item -> { runJdkTool("java"); return true; });
                popup.getMenu().add("Java Compiler: javac").setOnMenuItemClickListener(item -> { runJdkTool("javac"); return true; });
                popup.getMenu().add("JAR Tool: jar").setOnMenuItemClickListener(item -> { runJdkTool("jar"); return true; });
                popup.getMenu().add("Javadoc").setOnMenuItemClickListener(item -> { runJdkTool("javadoc"); return true; });
                popup.getMenu().add("JShell").setOnMenuItemClickListener(item -> { runJdkTool("jshell"); return true; });
                popup.getMenu().add("Keytool").setOnMenuItemClickListener(item -> { runJdkTool("keytool"); return true; });
                popup.getMenu().add("Kotlin Compiler").setOnMenuItemClickListener(item -> { runJdkTool("kotlinc"); return true; });
                popup.getMenu().add("HTML / CSS / JS Preview").setOnMenuItemClickListener(item -> { previewCurrentHtml(); return true; });
                break;
            case 6: // Run
                popup.getMenu().add("Run Current Project").setOnMenuItemClickListener(item -> { runCurrentProject(); return true; });
                popup.getMenu().add("Preview HTML").setOnMenuItemClickListener(item -> { previewCurrentHtml(); return true; });
                popup.getMenu().add("Stop / Status").setOnMenuItemClickListener(item -> { showEngineStatus(); return true; });
                break;
            case 7: // Help
                popup.getMenu().add("Supported Languages").setOnMenuItemClickListener(item -> {
                    showConsoleResult(true, "Supported editor languages: Java, Kotlin (.kt/.kts), HTML, CSS, JavaScript, JSON, XML, Markdown, Python and plain text.\n\nJava/Kotlin execution requires the bundled OpenJDK 21 runtime and Kotlin compiler. HTML/CSS/JS files can be previewed in the built-in WebView.");
                    return true;
                });
                popup.getMenu().add("Supported JDK Tools").setOnMenuItemClickListener(item -> {
                    showConsoleResult(true, "OpenJDK 21 tools: java, javac, jar, javadoc, jshell, keytool.\nJava project libraries are managed separately for offline projects.");
                    return true;
                });
                popup.getMenu().add("About Nice Java IDE").setOnMenuItemClickListener(item -> {
                    new android.app.AlertDialog.Builder(this).setTitle("Nice Java IDE")
                            .setMessage("Offline Java and Kotlin IDE with HTML/CSS/JavaScript preview, OpenJDK 21 tools, project libraries and syntax highlighting.")
                            .setPositiveButton("OK", null).show();
                    return true;
                });
                break;
        }
        popup.show();
    }

    private void showToolsDialog() {
        final String[] items = {
                "Install OpenJDK 21",
                "Install Java Project Libraries",
                "Java: java",
                "Java Compiler: javac",
                "JAR Tool: jar",
                "Javadoc",
                "JShell",
                "Keytool",
                "Kotlin Compiler",
                "HTML / CSS / JS Preview"
        };
        android.app.AlertDialog toolsDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("🧰  Nice Java Tools")
                // Populate the dialog's ListView before customizing it below.
                // Without setItems(), getListView() is null and Tools crashes.
                .setItems(items, null)
                .setNegativeButton("Close", null)
                .create();
        toolsDialog.setOnShowListener(dialog -> {
            android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF172D50, 0xFF263D68, 0xFF173D49});
            popupBg.setCornerRadius(dp(18));
            popupBg.setStroke(dp(1), 0xFF4D8FEA);
            toolsDialog.getWindow().setBackgroundDrawable(popupBg);
            toolsDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF79AEFF);
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView title = toolsDialog.findViewById(titleId);
            if (title != null) title.setTextColor(0xFFFFFFFF);

            android.widget.ListView list = toolsDialog.getListView();
            list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            list.setDivider(new android.graphics.drawable.ColorDrawable(0xFF46658F));
            list.setDividerHeight(dp(1));
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                    MainActivity.this, android.R.layout.simple_list_item_1, items) {
                @Override
                public android.view.View getView(int position, android.view.View convertView,
                                                 android.view.ViewGroup parent) {
                    TextView item = (TextView) super.getView(position, convertView, parent);
                    item.setTextColor(0xFFFFFFFF);
                    item.setTextSize(16);
                    item.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    item.setPadding(dp(22), dp(13), dp(18), dp(13));
                    item.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    return item;
                }
            };
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, which, id) -> {
                toolsDialog.dismiss();
                switch (which) {
                    case 0: installBundledJdk(); break;
                    case 1: installJavaProjectLibraries(); break;
                    case 2: runJdkTool("java"); break;
                    case 3: runJdkTool("javac"); break;
                    case 4: runJdkTool("jar"); break;
                    case 5: runJdkTool("javadoc"); break;
                    case 6: runJdkTool("jshell"); break;
                    case 7: runJdkTool("keytool"); break;
                    case 8: runJdkTool("kotlinc"); break;
                    case 9: previewCurrentHtml(); break;
                }
            });
        });
        toolsDialog.show();
    }

    private void runJdkTool(String toolName) {
        if (!isJdkInstalled()) {
            new android.app.AlertDialog.Builder(this).setTitle("OpenJDK 21 required")
                    .setMessage("Install the bundled OpenJDK 21 before using " + toolName + ".")
                    .setPositiveButton("Install JDK", (dialog, which) -> installBundledJdk())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        EditText argsInput = new EditText(this);
        argsInput.setSingleLine(false);
        argsInput.setMinLines(2);
        argsInput.setHint("Optional command arguments");
        new android.app.AlertDialog.Builder(this).setTitle("Run " + toolName)
                .setMessage("Enter arguments (optional):")
                .setView(argsInput)
                .setPositiveButton("Run", (dialog, which) -> {
                    try {
                        File executable = new File(new File(jdkHome, "bin"), toolName);
                        if (!executable.exists()) executable = new File(new File(jdkHome, "bin"), toolName + ".exe");
                        if ("java".equals(toolName) || "javac".equals(toolName)) {
                            File packaged = new File(getApplicationInfo().nativeLibraryDir,
                                    "java".equals(toolName) ? "libnicejava.so" : "libnicejavac.so");
                            if (packaged.isFile() && packaged.canExecute()) executable = packaged;
                        }
                        if (!executable.exists()) throw new java.io.IOException(toolName + " is not available in this JDK build.");
                        List<String> command = new ArrayList<>();
                        command.add(executable.getAbsolutePath());
                        String raw = argsInput.getText().toString().trim();
                        if (!raw.isEmpty()) {
                            // Deliberately simple whitespace tokenization; quoted shell expressions are not evaluated.
                            for (String part : raw.split("\\s+")) command.add(part);
                        }
                        ProcessBuilder toolBuilder = new ProcessBuilder(command)
                                .directory(workspace)
                                .redirectErrorStream(true);
                        // Match the environment used by the working Run pipeline.
                        // The Android-packaged launcher needs libjli.so from JDK lib/.
                        java.util.Map<String, String> toolEnv = toolBuilder.environment();
                        toolEnv.put("JAVA_HOME", jdkHome.getAbsolutePath());
                        toolEnv.put("PATH", new File(jdkHome, "bin").getAbsolutePath() + ":" + toolEnv.get("PATH"));
                        String jdkLib = new File(jdkHome, "lib").getAbsolutePath();
                        String jdkServer = new File(jdkHome, "lib/server").getAbsolutePath();
                        String nativeLib = getApplicationInfo().nativeLibraryDir;
                        toolEnv.put("LD_LIBRARY_PATH", nativeLib + ":" + jdkServer + ":" + jdkLib);
                        Process process = toolBuilder.start();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try (InputStream input = process.getInputStream()) {
                            byte[] buffer = new byte[4096]; int count;
                            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        }
                        if (!process.waitFor(30, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                            throw new java.util.concurrent.TimeoutException(toolName + " timed out.");
                        }
                        showConsoleResult(process.exitValue() == 0,
                                "$ " + toolName + (raw.isEmpty() ? "" : " " + raw) + "\n" + output.toString("UTF-8"));
                    } catch (Exception error) {
                        showConsoleResult(false, toolName + " error: " + error.getMessage());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void addTool(LinearLayout bar, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(action);
        bar.addView(button);
    }

    private void setupJavaStorage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        startActivityForResult(intent, REQUEST_WORKSPACE_TREE);
    }

    private Uri findOrCreateFolder(Uri tree, Uri parent, String name) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(1))
                        && DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                }
            }
        }
        Uri created = DocumentsContract.createDocument(getContentResolver(), parent,
                DocumentsContract.Document.MIME_TYPE_DIR, name);
        if (created == null) throw new java.io.IOException("Cannot create folder: " + name);
        return created;
    }

    private void configureJavaFolders(Uri tree) throws Exception {
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri appRoot = findOrCreateFolder(tree, root, "NiceJava");
        Uri javaFolder = findOrCreateFolder(tree, appRoot, "Java");
        Uri webFolder = findOrCreateFolder(tree, appRoot, "HTML_CSS");
        Uri kotlinFolder = findOrCreateFolder(tree, appRoot, "Kotlin");
        Uri kotlinProjectsFolder = findOrCreateFolder(tree, kotlinFolder, "Projects");
        Uri librariesFolder = findOrCreateFolder(tree, appRoot, "Libraries");
        Uri projectsFolder = findOrCreateFolder(tree, javaFolder, "Projects");
        getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).edit()
                .putString("tree_uri", tree.toString())
                .putString("java_folder", projectsFolder.toString())
                .putString("web_folder", webFolder.toString())
                .putString("kotlin_folder", kotlinProjectsFolder.toString())
                .putString("libraries_folder", librariesFolder.toString())
                .putString("projects_folder", projectsFolder.toString()).apply();
        copySafFolderToLocal(tree, projectsFolder, workspace);
        copySafFolderToLocal(tree, kotlinProjectsFolder, workspace);
        copySafFolderToLocal(tree, webFolder, workspace);
        status.setText("Folders ready: Java, Kotlin, HTML_CSS and Libraries");
        Toast.makeText(this, "NiceJava folders are ready", Toast.LENGTH_LONG).show();
    }

    private void copySafFolderToLocal(Uri tree, Uri folder, File target) throws Exception {
        if (!target.exists() && !target.mkdirs()) throw new java.io.IOException("Cannot create local workspace");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name.contains("/") || name.equals(".") || name.equals("..")) continue;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                File local = new File(target, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    copySafFolderToLocal(tree, child, local);
                } else {
                    try (InputStream in = getContentResolver().openInputStream(child);
                         OutputStream out = new FileOutputStream(local)) {
                        if (in == null) continue;
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private void syncFileToSaf(File file, Uri tree, Uri javaFolder, Uri webFolder) throws Exception {
        String ext = file.getName().toLowerCase(java.util.Locale.ROOT);
        boolean webFile = ext.endsWith(".html") || ext.endsWith(".htm")
                || ext.endsWith(".css") || ext.endsWith(".js") || ext.endsWith(".mjs");
        boolean kotlinFile = ext.endsWith(".kt") || ext.endsWith(".kts");
        Uri parent = webFile ? webFolder : (kotlinFile
                ? Uri.parse(getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("kotlin_folder", ""))
                : javaFolder);
        if (parent == null || parent.toString().isEmpty()) parent = javaFolder;
        String relative = workspace.toURI().relativize(file.toURI()).getPath();
        String[] parts = relative.split("/");
        for (int i = 0; i < parts.length - 1; i++) parent = findOrCreateFolder(tree, parent, parts[i]);
        String filename = parts[parts.length - 1];
        Uri target = null;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext()) if (filename.equals(cursor.getString(1))) {
                target = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)); break;
            }
        }
        if (target == null) target = DocumentsContract.createDocument(getContentResolver(), parent,
                "application/octet-stream", filename);
        if (target == null) throw new java.io.IOException("Cannot save " + filename);
        try (InputStream in = new FileInputStream(file);
             OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) throw new java.io.IOException("Cannot write " + filename);
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }

    private void copyLocalFolderToSaf(File source, Uri tree, Uri folder) throws Exception {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (file.getName().startsWith(".")) continue;
            if (file.isDirectory()) {
                Uri sub = findOrCreateFolder(tree, folder, file.getName());
                copyLocalFolderToSaf(file, tree, sub);
            } else {
                Uri target = null;
                Uri childList = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder));
                String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
                try (Cursor cursor = getContentResolver().query(childList, columns, null, null, null)) {
                    if (cursor != null) while (cursor.moveToNext()) if (file.getName().equals(cursor.getString(1))) {
                        target = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)); break;
                    }
                }
                if (target == null) target = DocumentsContract.createDocument(getContentResolver(), folder,
                        "application/octet-stream", file.getName());
                if (target == null) throw new java.io.IOException("Cannot save " + file.getName());
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) throw new java.io.IOException("Cannot write " + file.getName());
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
            }
        }
    }

    private void newFile() {
        promptNewWorkspaceFile();
    }

    private void showWorkspaceFiles() {
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        collectWorkspaceFiles(workspace, files);
        java.util.Collections.sort(files, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        String[] names = new String[files.size() + 1];
        names[0] = "+  Create new code file";
        for (int i = 0; i < files.size(); i++) {
            String relative = workspace.toURI().relativize(files.get(i).toURI()).getPath();
            names[i + 1] = relative;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Workspace • Code files")
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

    private void collectWorkspaceFiles(File folder, java.util.List<File> output) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectWorkspaceFiles(child, output);
            else if (isSupportedCodeFile(child)) output.add(child);
        }
    }

    private boolean isSupportedCodeFile(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".java") || name.endsWith(".html") || name.endsWith(".htm")
                || name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".json")
                || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".txt")
                || name.endsWith(".py") || name.endsWith(".kt") || name.endsWith(".kts");
    }

    private String getCurrentExtension() {
        String name = currentFile == null ? "" : currentFile.getName().toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
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

    private void promptNewKotlinFile() {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Main.kt or script.kts");
        new android.app.AlertDialog.Builder(this)
                .setTitle("New Kotlin File")
                .setMessage("Kotlin source files are stored in Downloads/NiceJava/Kotlin/Projects.")
                .setView(nameInput)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) name = "Main.kt";
                    if (!name.matches("(?i).+\\.(kt|kts)$")) name += ".kt";
                    if (name.contains("/") || name.contains("\\")) {
                        status.setText("Use a file name without folder separators");
                        return;
                    }
                    File target = new File(workspace, name);
                    try {
                        if (target.exists()) throw new IllegalArgumentException("File already exists");
                        String starter = name.toLowerCase(java.util.Locale.ROOT).endsWith(".kts")
                                ? "println(\"Hello from Kotlin Script!\")\n"
                                : "fun main() {\n    println(\"Hello from Nice Kotlin IDE!\")\n}\n";
                        writeText(target, starter);
                        openWorkspaceFile(target);
                        status.setText("Kotlin file created • compiler integration is being prepared");
                    } catch (Exception e) {
                        status.setText("Kotlin create error: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptNewWorkspaceFile() {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Main.java, Main.kt, script.kts, index.html");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Create code file")
                .setView(nameInput)
                .setPositiveButton("Create", (dialog, which) -> {
                    String relative = nameInput.getText().toString().trim();
                    if (relative.isEmpty()) return;
                    if (!relative.matches("(?i).+\\.(java|kt|kts|html|htm|css|js|json|xml|md|txt|py)$")) relative += ".java";
                    File target = new File(workspace, relative).getAbsoluteFile();
                    try {
                        String rootPath = workspace.getCanonicalPath() + File.separator;
                        String targetPath = target.getCanonicalPath();
                        if (!targetPath.startsWith(rootPath)) throw new IllegalArgumentException("Invalid file path");
                        if (target.exists()) throw new IllegalArgumentException("File already exists");
                        String lower = target.getName().toLowerCase(java.util.Locale.ROOT);
                        String starter = "";
                        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                            starter = "<!doctype html>\n<html><head><meta charset=\"UTF-8\"><title>Nice Web Project</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><h1>Hello Web!</h1><script src=\"app.js\"></script></body></html>\n";
                        } else if (lower.endsWith(".css")) {
                            starter = "body {\n  font-family: sans-serif;\n}\n";
                        } else if (lower.endsWith(".js")) {
                            starter = "console.log('Hello JavaScript!');\n";
                        } else if (lower.endsWith(".kt")) {
                            starter = "fun main() {\n    println(\"Hello from Nice Kotlin IDE!\")\n}\n";
                        } else if (lower.endsWith(".kts")) {
                            starter = "println(\"Hello from Kotlin Script!\")\n";
                        } else if (lower.endsWith(".java")) {
                            starter = "public class " + target.getName().substring(0, target.getName().length() - 5) + " {\n    public static void main(String[] args) {\n        System.out.println(\"Hello Java!\");\n    }\n}\n";
                        }
                        writeText(target, starter);
                        openWorkspaceFile(target);
                    } catch (Exception e) {
                        status.setText("Create error: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCurrentBeforeSwitch() throws Exception {
        if (currentUri != null) {
            writeUri(currentUri, editor.getText().toString());
        } else if (currentFile != null) {
            writeText(currentFile, editor.getText().toString());
            String treeText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("tree_uri", null);
            String javaText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("java_folder", null);
            String webText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("web_folder", null);
            if (treeText != null && javaText != null && webText != null) {
                syncFileToSaf(currentFile, Uri.parse(treeText), Uri.parse(javaText), Uri.parse(webText));
            }
        }
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/html", "text/css", "application/javascript", "text/javascript", "text/x-java", "text/plain", "application/json", "application/xml", "application/octet-stream"});
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
        if (requestCode == REQUEST_WORKSPACE_TREE) {
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
                configureJavaFolders(uri);
            } catch (Exception e) {
                status.setText("Folder setup error: " + e.getMessage());
            }
            return;
        }
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
                String treeText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("tree_uri", null);
                String javaText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("java_folder", null);
                String webText = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE).getString("web_folder", null);
                if (treeText != null && javaText != null && webText != null) {
                    syncFileToSaf(currentFile, Uri.parse(treeText), Uri.parse(javaText), Uri.parse(webText));
                }
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
        File runtime = new File(jdkHome, "lib/modules");
        return java.isFile() && javac.isFile() && runtime.isFile();
    }

    private File getJavaLibrariesDirectory() {
        return new File(getFilesDir(), "java-libs");
    }

    private String getJavaLibrariesClasspath() throws Exception {
        File[] jars = getJavaLibrariesDirectory().listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("Offline Java/Kotlin libraries are not installed.");
        }
        StringBuilder classpath = new StringBuilder();
        for (File jar : jars) {
            if (classpath.length() > 0) classpath.append(File.pathSeparator);
            classpath.append(jar.getAbsolutePath());
        }
        return classpath.toString();
    }

    private void installJavaProjectLibraries() {
        status.setText("Installing offline Java libraries…");
        new Thread(() -> {
            String message;
            boolean ok = false;
            try {
                int count = ensureJavaLibrariesInstalled();
                message = count + " offline Java library JAR files are ready.\n\n" +
                        "Use imports such as com.google.gson.Gson, " +
                        "org.apache.commons.lang3.StringUtils, okhttp3.OkHttpClient, " +
                        "org.h2.Driver, and org.slf4j.Logger.";
                ok = true;
            } catch (Exception e) {
                message = "Library installation failed: " + e.getMessage();
            }
            final boolean success = ok;
            final String result = message;
            runOnUiThread(() -> {
                status.setText(success ? "Offline Java libraries installed" : "Java library installation failed");
                new android.app.AlertDialog.Builder(this)
                        .setTitle(success ? "Java Libraries Ready" : "Library Error")
                        .setMessage(result)
                        .setPositiveButton("OK", null).show();
            });
        }, "Java-Library-Installer").start();
    }

    private int ensureJavaLibrariesInstalled() throws Exception {
        String[] assets = getAssets().list("java-libs");
        if (assets == null || assets.length == 0) {
            throw new IllegalStateException("No bundled JAR files found in APK assets. Rebuild the APK after bundleJavaProjectLibraries.");
        }
        File destination = getJavaLibrariesDirectory();
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IllegalStateException("Cannot create Java library folder: " + destination);
        }
        int ready = 0;
        byte[] buffer = new byte[32 * 1024];
        for (String name : assets) {
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) continue;
            File target = new File(destination, name);
            if (target.isFile() && isValidJar(target)) {
                ready++;
                continue;
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Cannot replace damaged library: " + name);
            }
            File temp = new File(destination, name + ".part");
            if (temp.exists()) temp.delete();
            try (InputStream input = getAssets().open("java-libs/" + name);
                 FileOutputStream output = new FileOutputStream(temp)) {
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.getFD().sync();
            } catch (Exception copyError) {
                temp.delete();
                throw copyError;
            }
            if (!isValidJar(temp)) {
                temp.delete();
                throw new IllegalStateException("Bundled library is not a valid JAR: " + name);
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                throw new IllegalStateException("Cannot activate library: " + name);
            }
            ready++;
        }
        if (ready == 0) {
            throw new IllegalStateException("No .jar files found in APK assets/java-libs. Check Gradle bundleJavaProjectLibraries task.");
        }
        return ready;
    }

    private boolean isValidJar(File file) {
        if (file == null || !file.isFile() || file.length() < 22) return false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            return zip.entries().hasMoreElements();
        } catch (Exception invalid) {
            return false;
        }
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
                try (InputStream raw = openBundledJdkArchive();
                     java.io.PushbackInputStream probe = new java.io.PushbackInputStream(raw, 2)) {
                    int first = probe.read();
                    int second = probe.read();
                    if (second >= 0) probe.unread(second);
                    if (first >= 0) probe.unread(first);
                    // Depending on Android asset packaging, .tar.gz may be
                    // delivered as a decompressed .tar entry. Detect the bytes,
                    // not just the filename, before choosing the input stream.
                    InputStream payload = (first == 0x1f && second == 0x8b)
                            ? new GzipCompressorInputStream(probe) : probe;
                    try (TarArchiveInputStream tar = new TarArchiveInputStream(payload)) {
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
                            File resolvedTarget = new File(parent, entry.getLinkName()).getCanonicalFile();
                            if (!resolvedTarget.getPath().startsWith(staging.getCanonicalPath() + File.separator)) throw new SecurityException("Unsafe symlink in JDK archive");
                            android.system.Os.symlink(entry.getLinkName(), output.getAbsolutePath());
                        } else if (entry.isLink()) {
                            File target = new File(staging, entry.getLinkName()).getCanonicalFile();
                            if (!target.getPath().startsWith(staging.getCanonicalPath() + File.separator) || !target.exists()) throw new SecurityException("Unsafe or missing hardlink target");
                            File parent = output.getParentFile();
                            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create hardlink folder");
                            android.system.Os.link(target.getAbsolutePath(), output.getAbsolutePath());
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
                // Preserve executable permissions even if the extraction filesystem
                // did not retain the TAR mode bits.
                File binDirectory = new File(jdkHome, "bin");
                File[] executables = binDirectory.listFiles();
                if (executables != null) {
                    for (File executable : executables) {
                        if (executable.isFile()) executable.setExecutable(true, false);
                    }
                }
                File javaBinary = new File(jdkHome, "bin/java");
                File javacBinary = new File(jdkHome, "bin/javac");
                File modulesFile = new File(jdkHome, "lib/modules");
                if (javaBinary.isFile()) javaBinary.setExecutable(true, false);
                if (javacBinary.isFile()) javacBinary.setExecutable(true, false);
                if (!javaBinary.isFile() || !javacBinary.isFile() || !modulesFile.isFile()) {
                    throw new IllegalStateException("Extracted JDK is incomplete. java=" +
                            javaBinary.isFile() + " (" + javaBinary.getAbsolutePath() + ")" +
                            ", javac=" + javacBinary.isFile() + " (" + javacBinary.getAbsolutePath() + ")" +
                            ", lib/modules=" + modulesFile.isFile() +
                            ". Extracted folder: " + jdkHome.getAbsolutePath());
                }
                if (!javaBinary.canExecute() || !javacBinary.canExecute()) {
                    throw new IllegalStateException("JDK files exist but Android could not mark them executable. java executable=" +
                            javaBinary.canExecute() + ", javac executable=" + javacBinary.canExecute());
                }
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

    private InputStream openBundledJdkArchive() throws Exception {
        // Android Gradle may normalize the asset entry name from .tar.gz to .tar.
        // The bytes remain the original gzip-compressed TAR, so extraction still
        // uses GzipCompressorInputStream below for either asset name.
        final String[] candidates = {"jdk21-android.tar.gz", "jdk21-android.tar"};
        java.io.FileNotFoundException lastMissing = null;
        for (String candidate : candidates) {
            try {
                return getAssets().open(candidate);
            } catch (java.io.FileNotFoundException missing) {
                lastMissing = missing;
            }
        }

        String[] rootAssets = getAssets().list("");
        StringBuilder found = new StringBuilder();
        if (rootAssets != null) {
            for (String asset : rootAssets) {
                if (found.length() > 0) found.append(", ");
                found.append(asset);
            }
        }
        java.io.FileNotFoundException error = new java.io.FileNotFoundException(
                "Bundled JDK archive not found. Tried assets/jdk21-android.tar.gz and " +
                "assets/jdk21-android.tar. APK root assets: [" + found + "]");
        if (lastMissing != null) error.initCause(lastMissing);
        throw error;
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

    private void previewCurrentHtml() {
        try {
            saveCurrentBeforeSwitch();
            if (currentUri != null || currentFile == null) {
                status.setText("Save the web project inside Workspace before previewing it.");
                return;
            }

            File htmlFile = currentFile;
            String ext = getCurrentExtension();
            if ("css".equals(ext) || "js".equals(ext)) {
                File folder = currentFile.getParentFile();
                File index = new File(folder, "index.html");
                File main = new File(folder, "main.html");
                if (index.isFile()) htmlFile = index;
                else if (main.isFile()) htmlFile = main;
                else {
                    File[] candidates = folder.listFiles((dir, name) ->
                            name.toLowerCase(java.util.Locale.ROOT).endsWith(".html")
                                    || name.toLowerCase(java.util.Locale.ROOT).endsWith(".htm"));
                    if (candidates != null && candidates.length > 0) htmlFile = candidates[0];
                }
            }
            String htmlExt = htmlFile.getName().toLowerCase(java.util.Locale.ROOT);
            if (!htmlExt.endsWith(".html") && !htmlExt.endsWith(".htm")) {
                status.setText("Create index.html in this folder, then Run HTML/CSS/JS.");
                return;
            }

            WebView web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setLoadsImagesAutomatically(true);
            web.setWebViewClient(new android.webkit.WebViewClient());
            web.setWebChromeClient(new android.webkit.WebChromeClient());

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("Web Preview • " + htmlFile.getName())
                    .setView(web)
                    .setPositiveButton("Close", null)
                    .create();
            dialog.setOnDismissListener(d -> web.destroy());
            dialog.show();
            dialog.getWindow().setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);

            // Load the saved HTML file directly so relative CSS, JS, images and other
            // workspace assets resolve from the same project directory.
            web.loadUrl(android.net.Uri.fromFile(htmlFile).toString());
            status.setText("Running web project • HTML + linked CSS + JavaScript");
        } catch (Exception e) {
            status.setText("Web preview error: " + e.getMessage());
        }
    }


    private File getKotlinCompilerHome() {
        return new File(getFilesDir(), "kotlin-compiler/kotlinc");
    }

    private boolean isKotlinCompilerInstalled() {
        File home = getKotlinCompilerHome();
        return new File(home, "lib/kotlin-compiler.jar").isFile()
                && new File(home, "lib/kotlin-stdlib.jar").isFile();
    }

    private File ensureKotlinCompilerInstalled() throws Exception {
        if (isKotlinCompilerInstalled()) return getKotlinCompilerHome();
        File destination = new File(getFilesDir(), "kotlin-compiler");
        File staging = new File(getFilesDir(), "kotlin-compiler-installing");
        long free = new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
        if (free < 450L * 1024L * 1024L) {
            throw new IllegalStateException("At least 450 MB free internal storage is required to extract the offline Kotlin compiler.");
        }
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IllegalStateException("Cannot create Kotlin compiler folder.");
        try (InputStream raw = getAssets().open("kotlin-compiler-2.4.21.zip");
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            String base = staging.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                File output = new File(staging, entry.getName()).getCanonicalFile();
                if (!output.getPath().startsWith(base)) {
                    throw new SecurityException("Unsafe path in Kotlin compiler archive.");
                }
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw new IllegalStateException("Cannot create compiler folder: " + entry.getName());
                    }
                } else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IllegalStateException("Cannot create compiler folder.");
                    }
                    try (FileOutputStream fileOut = new FileOutputStream(output)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) fileOut.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            deleteTree(staging);
            throw e;
        }
        File stagedHome = new File(staging, "kotlinc");
        if (!new File(stagedHome, "lib/kotlin-compiler.jar").isFile()
                || !new File(stagedHome, "lib/kotlin-stdlib.jar").isFile()) {
            deleteTree(staging);
            throw new IllegalStateException("The bundled Kotlin compiler archive is incomplete.");
        }
        deleteTree(destination);
        if (!staging.renameTo(destination)) {
            deleteTree(staging);
            throw new IllegalStateException("Cannot activate the extracted Kotlin compiler.");
        }
        return getKotlinCompilerHome();
    }

    private void runKotlinProject() {
        if (!isJdkInstalled()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("OpenJDK 21 is not installed")
                    .setMessage("Install the bundled OpenJDK 21 first. Kotlin/JVM uses this offline runtime to compile and run programs.")
                    .setPositiveButton("Install JDK", (dialog, which) -> installBundledJdk())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        status.setText("Preparing offline Kotlin compiler…");
        new Thread(() -> {
            boolean success = false;
            String report;
            try {
                saveCurrentBeforeSwitch();
                if (currentUri != null || currentFile == null) {
                    throw new IllegalStateException("Save the Kotlin file inside Workspace before running.");
                }
                ensureJavaLibrariesInstalled();
                String projectLibraryClasspath = getJavaLibrariesClasspath();
                File compilerHome = ensureKotlinCompilerInstalled();
                File compilerLib = new File(compilerHome, "lib");
                String compilerClassPath = new File(compilerLib, "*").getAbsolutePath();
                String extension = getCurrentExtension();
                if ("kts".equals(extension)) {
                    String output = runJdkCommand(new File(jdkHome, "bin/java"), 120,
                            "-cp", compilerClassPath,
                            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                            "-kotlin-home", compilerHome.getAbsolutePath(),
                            "-classpath", projectLibraryClasspath,
                            "-script", currentFile.getAbsolutePath());
                    success = true;
                    report = "KOTLIN SCRIPT SUCCESSFUL\n\n" +
                            (output.isEmpty() ? "(Script finished with no output)" : output);
                } else {
                    List<File> sources = new ArrayList<>();
                    collectKotlinFiles(workspace, sources);
                    if (sources.isEmpty()) throw new IllegalStateException("No .kt source files found in Workspace.");
                    File buildDirectory = new File(getFilesDir(), "kotlin-build");
                    if (!buildDirectory.exists() && !buildDirectory.mkdirs()) {
                        throw new IllegalStateException("Cannot create Kotlin build folder.");
                    }
                    File outputJar = new File(buildDirectory, "nice-kotlin-program.jar");
                    if (outputJar.exists() && !outputJar.delete()) {
                        throw new IllegalStateException("Cannot replace previous Kotlin build output.");
                    }
                    List<String> compilerArgs = new ArrayList<>();
                    compilerArgs.add("-kotlin-home");
                    compilerArgs.add(compilerHome.getAbsolutePath());
                    compilerArgs.add("-include-runtime");
                    compilerArgs.add("-classpath");
                    compilerArgs.add(projectLibraryClasspath);
                    compilerArgs.add("-d");
                    compilerArgs.add(outputJar.getAbsolutePath());
                    for (File source : sources) compilerArgs.add(source.getAbsolutePath());
                    List<String> command = new ArrayList<>();
                    command.add("-cp");
                    command.add(compilerClassPath);
                    command.add("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler");
                    command.addAll(compilerArgs);
                    runJdkCommand(new File(jdkHome, "bin/java"), 180, command.toArray(new String[0]));

                    String sourceText = readText(currentFile);
                    java.util.regex.Matcher packageMatcher = java.util.regex.Pattern
                            .compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)")
                            .matcher(sourceText);
                    String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
                    String fileName = currentFile.getName();
                    String className = fileName.substring(0, fileName.length() - 3) + "Kt";
                    String mainClass = packageName.isEmpty() ? className : packageName + "." + className;
                    String output = runJdkCommand(new File(jdkHome, "bin/java"), 60,
                            "-Dfile.encoding=UTF-8", "-cp",
                            outputJar.getAbsolutePath() + File.pathSeparator + projectLibraryClasspath, mainClass);
                    success = true;
                    report = cleanProgramOutput(output);
                }
            } catch (Exception e) {
                report = "KOTLIN BUILD / RUN FAILED\n\n" + e.getMessage();
            }
            final boolean ok = success;
            final String result = report;
            runOnUiThread(() -> {
                status.setText(ok ? "Kotlin program finished successfully" : "Kotlin compile/run failed");
                showConsoleResult(ok, result);
            });
        }, "NiceKotlin-CompileRun").start();
    }

    private void collectKotlinFiles(File folder, List<File> output) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectKotlinFiles(child, output);
            else if (child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".kt")) output.add(child);
        }
    }

    private void runCurrentProject() {
        if (!"java".equals(getCurrentExtension())) {
            String ext = getCurrentExtension();
            if ("kt".equals(ext) || "kts".equals(ext)) {
                runKotlinProject();
                return;
            }
            if ("html".equals(ext) || "htm".equals(ext) || "css".equals(ext) || "js".equals(ext)) {
                previewCurrentHtml();
            } else {
                status.setText("Run supports Java and HTML/CSS/JavaScript projects.");
            }
            return;
        }
        if (!isJdkInstalled()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("OpenJDK 21 is not installed")
                    .setMessage("Tap Install JDK first. The full toolchain is bundled offline in this APK.")
                    .setPositiveButton("Install JDK", (dialog, which) -> installBundledJdk())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        status.setText("Saving and compiling Java project…");
        new Thread(() -> {
            String report;
            boolean success = false;
            File classes = new File(getFilesDir(), "java-build/classes");
            try {
                saveCurrentBeforeSwitch();
                if (currentUri != null) throw new IllegalStateException("Run a Java file saved inside Workspace. Use Save As into Workspace first.");
                ensureJavaLibrariesInstalled();
                File libraryDirectory = getJavaLibrariesDirectory();
                String libraryClasspath = new File(libraryDirectory, "*").getAbsolutePath();
                if (currentFile == null || !currentFile.isFile() ||
                        !currentFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
                    throw new IllegalStateException("Open a Java source file inside Workspace before running.");
                }

                String sourceText = readText(currentFile);
                java.util.regex.Matcher packageMatcher = java.util.regex.Pattern
                        .compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;")
                        .matcher(sourceText);
                String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
                java.util.regex.Matcher classMatcher = java.util.regex.Pattern
                        .compile("(?m)\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed)\\s+)*(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)")
                        .matcher(sourceText);
                String className = classMatcher.find() ? classMatcher.group(1) :
                        currentFile.getName().substring(0, currentFile.getName().length() - 5);

                // Java requires a public top-level type to match its .java filename.
                // Compile the active editor file from a temporary correctly named copy,
                // leaving the user's original file and workspace untouched.
                File compileSource = currentFile;
                String expectedSourceName = className + ".java";
                if (!currentFile.getName().equals(expectedSourceName)) {
                    File staging = new File(getFilesDir(), "java-build/staged-source");
                    deleteTree(staging);
                    if (!staging.mkdirs()) throw new IllegalStateException("Cannot create temporary Java source folder.");
                    compileSource = new File(staging, expectedSourceName);
                    writeText(compileSource, sourceText);
                }

                // Keep previous class files and compile only the active source.
                // javac resolves project dependencies through -sourcepath and
                // recompiles source dependencies when their class files are stale.
                if (!classes.exists() && !classes.mkdirs()) {
                    throw new IllegalStateException("Cannot create build output folder.");
                }
                String compileClasspath = classes.getAbsolutePath() + File.pathSeparator + libraryClasspath;
                List<String> compileArgs = new ArrayList<>();
                compileArgs.add("-encoding");
                compileArgs.add("UTF-8");
                compileArgs.add("-classpath");
                compileArgs.add(compileClasspath);
                compileArgs.add("-sourcepath");
                compileArgs.add(workspace.getAbsolutePath());
                compileArgs.add("-d");
                compileArgs.add(classes.getAbsolutePath());
                compileArgs.add(compileSource.getAbsolutePath());
                String compileOutput = runJdkCommand(new File(jdkHome, "bin/javac"), compileArgs.toArray(new String[0]));
                String mainClass = packageName.isEmpty() ? className : packageName + "." + className;
                String runOutput = runJdkCommand(new File(jdkHome, "bin/java"), "-Dfile.encoding=UTF-8",
                        "-cp", classes.getAbsolutePath() + File.pathSeparator + libraryClasspath, mainClass);
                success = true;
                report = cleanProgramOutput(runOutput);
            } catch (Exception e) {
                report = "BUILD / RUN FAILED\n\n" + e.getMessage();
            }
            final boolean ok = success;
            final String result = report;
            runOnUiThread(() -> {
                status.setText(ok ? "Program finished successfully" : "Java compile/run failed");
                showConsoleResult(ok, result);
            });
        }, "NiceJava-CompileRun").start();
    }

    private String cleanProgramOutput(String output) {
        if (output == null || output.trim().isEmpty()) return "Program finished with no output.";
        StringBuilder cleaned = new StringBuilder();
        for (String line : output.split("\\r?\\n")) {
            // Hide Android linker DT_RPATH warnings; these are not program output.
            if (line.startsWith("WARNING: linker: Warning:")) continue;
            if (cleaned.length() > 0) cleaned.append('\n');
            cleaned.append(line);
        }
        return cleaned.toString().trim().isEmpty()
                ? "Program finished with no output." : cleaned.toString().trim();
    }

    private void showConsoleResult(boolean success, String output) {
        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF2F5FA);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(12), dp(12));
        header.setBackgroundColor(0xFFE7EDF6);
        TextView heading = new TextView(this);
        heading.setText(success ? "OUTPUT" : "ERROR");
        heading.setTextColor(success ? 0xFF65D6A3 : 0xFFFF8585);
        heading.setTextSize(18);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button close = new Button(this);
        close.setText("CLOSE");
        close.setAllCaps(false);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close);
        page.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        TextView console = new TextView(this);
        console.setText(output);
        console.setTextIsSelectable(true);
        console.setTextColor(0xFF182235);
        console.setTextSize(15);
        console.setTypeface(android.graphics.Typeface.MONOSPACE);
        console.setPadding(dp(18), dp(18), dp(18), dp(18));
        console.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        // Preserve long output lines and allow independent horizontal scrolling.
        console.setHorizontallyScrolling(true);
        console.setSingleLine(false);

        android.widget.HorizontalScrollView horizontalScroll =
                new android.widget.HorizontalScrollView(this);
        horizontalScroll.setFillViewport(false);
        horizontalScroll.setHorizontalScrollBarEnabled(true);
        horizontalScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        horizontalScroll.addView(console, new android.widget.HorizontalScrollView.LayoutParams(
                android.widget.HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                android.widget.HorizontalScrollView.LayoutParams.WRAP_CONTENT));

        // Outer ScrollView handles vertical movement; inner HorizontalScrollView
        // handles long lines without wrapping or clipping.
        android.widget.ScrollView verticalScroll = new android.widget.ScrollView(this);
        verticalScroll.setFillViewport(true);
        verticalScroll.setVerticalScrollBarEnabled(true);
        verticalScroll.setBackgroundColor(0xFFFFFFFF);
        verticalScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        verticalScroll.addView(horizontalScroll, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(verticalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(page);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setStatusBarColor(0xFFF2F5FA);
            dialog.getWindow().setNavigationBarColor(0xFFF2F5FA);
        }
    }

    private void showEngineStatus() {
        if (!isJdkInstalled()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("OpenJDK 21 is not installed")
                    .setMessage("First tap Install JDK. The bundled toolchain is extracted offline into app storage.")
                    .setPositiveButton("Install JDK", (dialog, which) -> installBundledJdk())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        status.setText("Testing OpenJDK 21 native execution…");
        new Thread(() -> {
            String report;
            boolean passed = false;
            File testDir = new File(getFilesDir(), "jdk-runtime-test");
            try {
                deleteTree(testDir);
                if (!testDir.mkdirs()) throw new IllegalStateException("Cannot create runtime test folder");
                String javaVersion = runJdkCommand(new File(jdkHome, "bin/java"), "-version");
                String javacVersion = runJdkCommand(new File(jdkHome, "bin/javac"), "-version");
                File source = new File(testDir, "HelloNice.java");
                writeText(source, "public class HelloNice { public static void main(String[] a) { System.out.println(\"NICE_JAVA_RUNTIME_OK\"); } }");
                String compileOutput = runJdkCommand(new File(jdkHome, "bin/javac"), "-d", testDir.getAbsolutePath(), source.getAbsolutePath());
                String runOutput = runJdkCommand(new File(jdkHome, "bin/java"), "-cp", testDir.getAbsolutePath(), "HelloNice");
                if (!runOutput.contains("NICE_JAVA_RUNTIME_OK")) throw new IllegalStateException("Compiled program output did not match expected result: " + runOutput);
                passed = true;
                report = "OpenJDK 21 native test: PASSED\n\n" + javaVersion + "\n" + javacVersion +
                        "\nCompile: successful\nRun output: NICE_JAVA_RUNTIME_OK";
            } catch (Exception e) {
                report = "OpenJDK 21 native test: FAILED\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                deleteTree(testDir);
            }
            final boolean ok = passed;
            final String result = report;
            runOnUiThread(() -> {
                status.setText(ok ? "OpenJDK 21 native runtime test passed" : "OpenJDK 21 runtime test failed");
                new android.app.AlertDialog.Builder(this).setTitle(ok ? "JDK Runtime Ready" : "JDK Runtime Test Failed")
                        .setMessage(result).setPositiveButton("OK", null).show();
            });
        }, "Jdk21-Runtime-Test").start();
    }

    private boolean isPermissionDenied(java.io.IOException error) {
        String message = error.getMessage();
        return message != null && (message.contains("error=13") ||
                message.toLowerCase(java.util.Locale.ROOT).contains("permission denied"));
    }

    private String runJdkCommand(File executable, String... args) throws Exception {
        return runJdkCommand(executable, 30, args);
    }

    private String runJdkCommand(File executable, int timeoutSeconds, String... args) throws Exception {
        if (!executable.isFile() || !executable.canExecute()) {
            throw new IllegalStateException("JDK executable is missing or not executable: " + executable.getAbsolutePath());
        }
        List<String> command = new ArrayList<>();
        // Android app-private files may be mounted noexec. The build packages
        // the actual ARM64 JDK launcher ELFs as native libraries so PackageManager
        // extracts them into nativeLibraryDir, an executable installation path.
        String toolName = executable.getName();
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        File packagedLauncher = null;
        if ("javac".equals(toolName) || "javac.exe".equals(toolName)) {
            packagedLauncher = new File(nativeDir, "libnicejavac.so");
        } else if ("java".equals(toolName) || "java.exe".equals(toolName)) {
            packagedLauncher = new File(nativeDir, "libnicejava.so");
        }
        if (packagedLauncher != null) {
            if (!packagedLauncher.isFile() || !packagedLauncher.canExecute()) {
                throw new IllegalStateException("Android JDK launcher is missing or not executable: " + packagedLauncher);
            }
            command.add(packagedLauncher.getAbsolutePath());
        } else {
            command.add(executable.getAbsolutePath());
        }
        java.util.Collections.addAll(command, args);
        File outputFile = File.createTempFile("nicejava-process-", ".log", getCacheDir());
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(getFilesDir());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile);
            java.util.Map<String, String> env = builder.environment();
            env.put("JAVA_HOME", jdkHome.getAbsolutePath());
            env.put("PATH", new File(jdkHome, "bin").getAbsolutePath() + ":" + env.get("PATH"));
            String lib = new File(jdkHome, "lib").getAbsolutePath();
            String server = new File(jdkHome, "lib/server").getAbsolutePath();
            String existingLdPath = env.get("LD_LIBRARY_PATH");
            env.put("LD_LIBRARY_PATH", server + ":" + lib +
                    (existingLdPath == null || existingLdPath.isEmpty() ? "" : ":" + existingLdPath));
            try {
                process = builder.start();
            } catch (java.io.IOException directError) {
                // Android may deny execve() for binaries extracted into the app's
                // writable files directory (EACCES / noexec policy). In that case,
                // ask Android's system dynamic linker to load the same ARM64 ELF.
                if (!isPermissionDenied(directError)) throw directError;
                File linker = new File("/system/bin/linker64");
                if (!linker.isFile()) throw directError;
                List<String> linkerCommand = new ArrayList<>();
                linkerCommand.add(linker.getAbsolutePath());
                // Android's linker64 on some releases does not accept --library-path
                // here; it interprets it as the ELF path. Pass the library search
                // path only through LD_LIBRARY_PATH in the child environment.
                linkerCommand.addAll(command);
                ProcessBuilder fallback = new ProcessBuilder(linkerCommand);
                fallback.directory(getFilesDir());
                fallback.redirectErrorStream(true);
                fallback.redirectOutput(outputFile);
                fallback.environment().putAll(env);
                process = fallback.start();
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                String partial = outputFile.isFile() ? readText(outputFile) : "";
                throw new IllegalStateException("JDK command timed out after " + timeoutSeconds +
                        " seconds: " + executable.getName() + (partial.isEmpty() ? "" : "\n" + partial));
            }
            String output = outputFile.isFile() ? readText(outputFile) : "";
            if (process.exitValue() != 0) {
                throw new IllegalStateException(executable.getName() + " exited with code " +
                        process.exitValue() + ": " + output);
            }
            return output.trim();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (outputFile.exists()) outputFile.delete();
        }
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
        String name = currentUri != null ? "External Java file" :
                (currentFile == null ? "Untitled.java" : currentFile.getName());
        if (fileLabel != null) fileLabel.setText("WORKSPACE  /  " + name);
        if (ideWebView != null) {
            String safe = org.json.JSONObject.quote(name);
            ideWebView.evaluateJavascript("window.setFileName && window.setFileName(" + safe + ")", null);
        }
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
