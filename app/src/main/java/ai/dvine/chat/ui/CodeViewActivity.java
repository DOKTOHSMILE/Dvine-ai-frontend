package ai.dvine.chat.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import ai.dvine.R;

/** Full-screen code. It can be copied or shared as a file. It is not executed. */
public final class CodeViewActivity extends AppCompatActivity {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_LANG = "lang";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_view);
        Ui.fitInsets(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0)); // below the status bar, above the navigation bar
        final String code = getIntent().getStringExtra(EXTRA_CODE);
        String lang = getIntent().getStringExtra(EXTRA_LANG);
        TextView title = findViewById(R.id.code_lang);
        TextView body = findViewById(R.id.code_body);
        if (title != null) {
            title.setText(lang == null || lang.isEmpty() ? "code" : lang);
        }
        if (body != null) {
            body.setText(code == null ? "" : numbered(code));
        }
        TextView back = findViewById(R.id.code_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        TextView copy = findViewById(R.id.code_copy);
        if (copy != null) {
            copy.setOnClickListener(v -> copyText(code));
        }
        TextView share = findViewById(R.id.code_share);
        if (share != null) {
            share.setOnClickListener(v -> shareFile(code, lang));
        }
    }

    private void copyText(String code) {
        ClipboardManager clips = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clips != null) {
            clips.setPrimaryClip(ClipData.newPlainText("dvine-code", code == null ? "" : code));
        }
    }

    private void shareFile(String code, String lang) {
        try {
            String name = "dvine." + (lang == null || lang.isEmpty() ? "txt" : lang);
            File file = new File(getCacheDir(), name);
            Files.write(file.toPath(), (code == null ? "" : code).getBytes(StandardCharsets.UTF_8));
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "ai.dvine.files", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share code"));
        } catch (Exception ignored) {
            copyText(code);
        }
    }

    static String numbered(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String[] lines = code.split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(i + 1).append("  ").append(lines[i]);
        }
        return b.toString();
    }
}
