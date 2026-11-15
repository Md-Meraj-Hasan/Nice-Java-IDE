package com.meraj.nicejavaide;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN = 101;
    private static final int REQUEST_SAVE_AS = 102;
    private static final String STARTER = "public class Main {\\n" +
            "    public static void main(String[] args) {\\n" +
            "        System.out.println(\"Hello from Nice Java IDE!\");\n" +
            "    }\\n" +
            "}\\n";

    private EditText editor;
    private TextView status;
    private TextView fileLabel;
    private File workspace;
    private File currentFile;
    private Uri currentUri;

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
        addTool(toolbar, "Open", v -> openDocument());
        addTool(toolbar, "Save", v -> saveFile());
        addTool(toolbar, "Save As", v -> saveAs());
        addTool(toolbar, "Find", v -> showFind());
        addTool(toolbar, "Run", v -> showEngineStatus());
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll);

        fileLabel = new TextView(this);
        fileLabel.setTextColor(0xFF93C5FD);
        fileLabel.setTextSize(12);
        fileLabel.setPadding(dp(5), dp(12), dp(5), dp(6));
        root.addView(fileLabel);

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
        status.setText("Offline workspace ready • JDK integration in progress");
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
        editor.setText("public class Untitled {\\n    public static void main(String[] args) {\\n    }\\n}\\n");
        updateFileLabel();
        status.setText("New file • use Save or Save As");
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
                editor.setText(readUri(uri));
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
                editor.setText(readUri(uri));
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
            editor.setText(readText(currentFile));
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

    private void showEngineStatus() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("OpenJDK 21 integration in progress")
                .setMessage("The editor can open and save Java files. Offline javac/java execution will be enabled after the bundled ARM64 JDK passes Android runtime checks.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void updateFileLabel() {
        if (fileLabel == null) return;
        String name = currentUri != null ? "External Java file" :
                (currentFile == null ? "Untitled.java" : currentFile.getName());
        fileLabel.setText("WORKSPACE  /  " + name);
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
