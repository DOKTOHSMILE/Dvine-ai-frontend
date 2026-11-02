package ai.dvine.chat.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.dvine.connect.ConnectorStore;

/**
 * Connectors, as in Claude: the services the brain may use when it answers. Each card has an on/off switch and its
 * status; a tapped card opens its token (hidden, with Show), the MCP server's address, and Test. The brain is only
 * told about connectors that are on and ready.
 */
public final class ConnectorsActivity extends AppCompatActivity {
    private ConnectorStore store;
    private LinearLayout body;
    private ConnectorStore.Kind open;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final int CALENDAR = 41;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        store = new ConnectorStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 4));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Connectors", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.gap(this, 1), new LinearLayout.LayoutParams(Ui.dp(this, 34), 1));
        root.addView(head);
        TextView lead = Ui.text(this, "Let DVINE use your services when it answers. Tokens stay on this phone, encrypted.",
                14, Ui.SUB, false);
        lead.setPadding(pad + Ui.dp(this, 6), Ui.dp(this, 4), pad, Ui.dp(this, 8));
        root.addView(lead);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        fill();
    }

    private void fill() {
        body.removeAllViews();
        for (ConnectorStore.Kind k : ConnectorStore.Kind.values()) {
            body.addView(card(k), Ui.wide(this, 12));
        }
    }

    private View card(final ConnectorStore.Kind k) {
        boolean expanded = k == open;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.CARD, expanded ? Ui.BORDER_ON : Ui.BORDER, 18));
        int p = Ui.dp(this, 16);
        card.setPadding(p, Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, k.title.substring(0, 1), 15, Ui.ON_CORAL, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Ui.CORAL, 0, 9));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        words.addView(Ui.text(this, k.title, 16, Ui.TEXT, true));
        words.addView(Ui.text(this, status(k), 12, store.ready(k) ? Ui.CORAL : Ui.SUB, false));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(store.on(k));
        toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        toggle.setOnCheckedChangeListener((b, on) -> {
            store.setOn(k, on);
            if (on && k == ConnectorStore.Kind.CALENDAR && !store.calendarAllowed()) {
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, CALENDAR);
            }
            if (on && (k == ConnectorStore.Kind.GITHUB || k == ConnectorStore.Kind.NOTION || k == ConnectorStore.Kind.MCP)) {
                open = k; // it needs a token or an address: open it
            }
            fill();
        });
        row.addView(toggle);
        card.addView(row);
        card.setOnClickListener(v -> {
            open = open == k ? null : k;
            fill();
        });
        if (!expanded) {
            return card;
        }
        LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        inside.setPadding(0, 0, Ui.dp(this, 8), 0);
        inside.addView(Ui.text(this, k.about, 13, Ui.SUB, false), Ui.wide(this, 10));
        final EditText url = Ui.field(this, "https://… (the MCP server's address)", store.mcpUrl(), false, false);
        final EditText name = Ui.field(this, "A short name for its tools (for example: tasks)", store.mcpName().equals("mcp") ? "" : store.mcpName(),
                false, false);
        if (k == ConnectorStore.Kind.MCP) {
            inside.addView(url, Ui.wide(this, 12));
            inside.addView(name, Ui.wide(this, 10));
        }
        final EditText token = Ui.field(this, k == ConnectorStore.Kind.MCP ? "Token (optional)" : "Token", store.token(k), true, false);
        if (k == ConnectorStore.Kind.GITHUB || k == ConnectorStore.Kind.NOTION || k == ConnectorStore.Kind.MCP) {
            FrameLayout box = new FrameLayout(this);
            token.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 70), Ui.dp(this, 14));
            box.addView(token);
            TextView show = Ui.text(this, "Show", 15, Ui.ACCENT, false);
            show.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            show.setOnClickListener(v -> {
                boolean hidden = (token.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
                token.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                show.setText(hidden ? "Hide" : "Show");
            });
            box.addView(show, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.CENTER_VERTICAL));
            inside.addView(box, Ui.wide(this, 10));
        }
        if (k == ConnectorStore.Kind.MCP && !store.mcpTools().isEmpty()) {
            StringBuilder t = new StringBuilder("Tools: ");
            for (int i = 0; i < store.mcpTools().size() && i < 12; i++) {
                t.append(i == 0 ? "" : ", ").append(store.mcpTools().get(i).name);
            }
            inside.addView(Ui.text(this, t.toString(), 13, Ui.SUB, false), Ui.wide(this, 10));
        }
        if (k != ConnectorStore.Kind.WEB) {
            final TextView result = Ui.text(this, "", 13, Ui.SUB, false);
            TextView test = Ui.button(this, "Save and test", true);
            test.setOnClickListener(v -> {
                if (k == ConnectorStore.Kind.MCP) {
                    store.setMcp(url.getText().toString(), name.getText().toString());
                }
                store.setToken(k, token.getText().toString());
                store.setOn(k, true);
                result.setText("Testing…");
                new Thread(() -> {
                    final String why = store.test(k);
                    main.post(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        android.widget.Toast.makeText(this, why == null ? k.title + " is connected." : "Didn't work: " + why + ".",
                                android.widget.Toast.LENGTH_LONG).show();
                        fill();
                    });
                }, "dvine-connector-test").start();
            });
            inside.addView(test, Ui.wide(this, 12));
            inside.addView(result, Ui.wide(this, 6));
        }
        card.addView(inside);
        return card;
    }

    private String status(ConnectorStore.Kind k) {
        if (!store.on(k)) {
            return "Off";
        }
        if (store.ready(k)) {
            return k == ConnectorStore.Kind.MCP ? "Connected: " + store.mcpTools().size() + " tools" : "Connected";
        }
        switch (k) {
            case CALENDAR: return "Needs calendar permission";
            case MCP: return "Needs an address, then Test";
            default: return "Needs a token";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR) {
            fill();
        }
    }
}
