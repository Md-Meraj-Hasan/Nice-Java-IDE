package com.meraj.nicejavaide;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private EditText editor;
    private TextView status;
    private File workspace;
    private File currentFile;

    private static final String STARTER = "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello from Nice Java IDE!\");\n" +
            "    }\n" +
            "}\n";

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
        addTool(toolbar, "Open", v -> openStarter());
        addTool(toolbar, "Save", v -> saveFile());
        addTool(toolbar, "Find", v -> showFind());
        addTool(toolbar, "Run", v -> showEngineStatus());
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll);

        TextView fileLabel = new TextView(this);
        fileLabel.setText("WORKSPACE  /  Main.java");
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
        status.setText("Offline workspace ready • Compiler engine not installed yet");
        status.setTextColor(0xFFFBBF24);
        status.setTextSize(11);
        status.setPadding(dp(5), dp(10), dp(5), dp(4));
        root.addView(status);

        setContentView(root);
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
        editor.setText("public class Untitled {\n    public static void main(String[] args) {\n    }\n}\n");
        status.setText("New file • Save to workspace");
    }

    private void openStarter() {
        File candidate = new File(workspace, "Main.java");
        currentFile = candidate;
        loadCurrentFile();
    }

    private void loadCurrentFile() {
        try {
            if (!currentFile.exists()) {
                writeText(currentFile, STARTER);
            }
            editor.setText(readText(currentFile));
            status.setText("Opened: " + currentFile.getName());
        } catch (Exception e) {
            status.setText("Open error: " + e.getMessage());
        }
    }

    private void saveFile() {
        try {
            writeText(currentFile, editor.getText().toString());
            status.setText("Saved: " + currentFile.getName());
            Toast.makeText(this, "Saved to app workspace", Toast.LENGTH_SHORT).show();
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
                .setTitle("Java engine not installed")
                .setMessage("The editor and local workspace are available. Offline Java compilation and execution are not enabled in this scaffold yet. The compiler/runtime must be bundled and verified before Run can execute code.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return new String(bytes, 0, Math.max(n, 0), StandardCharsets.UTF_8);
        }
    }

    private void writeText(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create folder");
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
