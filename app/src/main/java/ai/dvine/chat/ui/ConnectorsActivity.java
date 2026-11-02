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
 * Connectors: the services DVINE may use when it answers. Each card has an on/off switch and its
 * status; a tapped card opens its token (hidden, with Show), the MCP server's address, and Test. The model is only
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
        Ui.fitInsets(root);
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
        if (k == ConnectorStore.Kind.GITHUB) {
            githubSignIn(inside);
        }
        final EditText token = Ui.field(this, k == ConnectorStore.Kind.MCP ? "Token (optional)"
                : k == ConnectorStore.Kind.GITHUB ? "Or paste a token" : "Token", store.token(k), true, false);
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
        if (k == ConnectorStore.Kind.WEB) {
            webSearchSettings(inside);
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
                    if (why == null && k == ConnectorStore.Kind.GITHUB) {
                        try {
                            store.setGithubUser(ai.dvine.connect.GitHubLogin.user(ai.dvine.connect.Connectors.https(), store.token(k)));
                        } catch (java.io.IOException | RuntimeException ignored) {
                            // the name is a nicety
                        }
                    }
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

    // ------------------------------------------------------------ web search

    /** Which search: free (Wikipedia), or the whole web with the user's Brave Search, Tavily or Google key. */
    private void webSearchSettings(LinearLayout inside) {
        final ai.dvine.net.WebSearch.Settings now = store.webSearch();
        final String[] keys = {"", "brave", "tavily", "google"};
        final String[] names = {"Free", "Brave", "Tavily", "Google"};
        final String[] chosen = {now.provider};
        final LinearLayout row = new LinearLayout(this);
        row.setBackground(Ui.round(this, Ui.FIELD, 0, 14));
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        final EditText key = Ui.field(this, "API key", now.key, true, false);
        final EditText cx = Ui.field(this, "Search engine ID (cx)", now.cx, false, false);
        final TextView how = Ui.text(this, "", 12, Ui.SUB, false);
        final Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            row.removeAllViews();
            for (int i = 0; i < keys.length; i++) {
                final String kk = keys[i];
                boolean on = kk.equals(chosen[0]);
                TextView t = Ui.text(this, names[i], 14, on ? Ui.ON_CORAL : Ui.TEXT, on);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(Ui.dp(this, 38));
                if (on) {
                    t.setBackground(Ui.round(this, Ui.CORAL, 0, 11));
                }
                t.setOnClickListener(v -> {
                    chosen[0] = kk;
                    paint[0].run();
                });
                row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            key.setVisibility(chosen[0].isEmpty() ? View.GONE : View.VISIBLE);
            cx.setVisibility("google".equals(chosen[0]) ? View.VISIBLE : View.GONE);
            switch (chosen[0]) {
                case "brave": how.setText("The whole web. A key from api.search.brave.com (there is a free plan)."); break;
                case "tavily": how.setText("The whole web, made for AI answers. A key from tavily.com (free credits each month)."); break;
                case "google": how.setText("Google results through Programmable Search: an API key and a search engine ID from programmablesearchengine.google.com."); break;
                default: how.setText("Wikipedia's search, several articles at a time. No key. For the whole web, choose a provider and add its key.");
            }
        };
        paint[0].run();
        inside.addView(row, Ui.wide(this, 12));
        inside.addView(how, Ui.wide(this, 8));
        inside.addView(key, Ui.wide(this, 10));
        inside.addView(cx, Ui.wide(this, 10));
        TextView save = Ui.button(this, "Save", true);
        save.setOnClickListener(v -> {
            store.setWebSearch(chosen[0], key.getText().toString(), cx.getText().toString());
            android.widget.Toast.makeText(this, "Web search: " + store.webSearch().label()
                    + (chosen[0].isEmpty() || store.webSearch().keyed() ? "." : " needs its key; using Wikipedia until then."),
                    android.widget.Toast.LENGTH_LONG).show();
            fill();
        });
        inside.addView(save, Ui.wide(this, 12));
    }

    // ------------------------------------------------------------ GitHub sign-in

    private volatile boolean signingIn;

    /**
     * The GitHub card's ways in: who is signed in (with Sign out), "Sign in with GitHub" in the browser (you sign in
     * on github.com with your email or username and password, then approve DVINE with a short code), and "Create a
     * token" (GitHub's token page, filled in). GitHub doesn't let apps take your password directly.
     */
    private void githubSignIn(LinearLayout inside) {
        final ConnectorStore.Kind k = ConnectorStore.Kind.GITHUB;
        if (!store.githubUser().isEmpty() && !store.token(k).isEmpty()) {
            LinearLayout who = new LinearLayout(this);
            who.setGravity(Gravity.CENTER_VERTICAL);
            who.addView(Ui.text(this, "Signed in as @" + store.githubUser(), 15, Ui.TEXT, true),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView out = Ui.text(this, "Sign out", 15, Ui.ACCENT, false);
            out.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, Ui.dp(this, 8));
            out.setOnClickListener(v -> {
                store.setToken(k, "");
                store.setGithubUser("");
                store.setOn(k, false);
                fill();
            });
            who.addView(out);
            inside.addView(who, Ui.wide(this, 10));
        }
        TextView browser = Ui.button(this, "Sign in with GitHub", true);
        browser.setOnClickListener(v -> {
            if (store.githubClientId().isEmpty()) {
                askClientId();
            } else {
                startDeviceSignIn();
            }
        });
        inside.addView(browser, Ui.wide(this, 12));
        inside.addView(Ui.text(this, "Opens github.com, where you sign in with your email or username and password, then approve DVINE"
                + " with a short code.", 12, Ui.SUB, false), Ui.wide(this, 6));
        TextView make = Ui.text(this, "Create a token on GitHub \u203A", 15, Ui.ACCENT, false);
        make.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        make.setOnClickListener(v -> open(ai.dvine.connect.GitHubLogin.newTokenUrl()));
        inside.addView(make);
    }

    private void open(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No browser on this phone.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Browser sign-in needs a GitHub OAuth app's client ID (made once, free); asked for the first time only. */
    private void askClientId() {
        final EditText id = Ui.field(this, "Client ID (looks like Ov23li…)", "", false, false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(Ui.text(this, "Browser sign-in goes through a GitHub OAuth app of your own (once, free): on GitHub, Settings \u203A"
                + " Developer settings \u203A OAuth Apps \u203A New OAuth App (any name; homepage and callback can be"
                + " https://github.com), tick \u201cEnable Device Flow\u201d, and paste its Client ID here.", 13, Ui.SUB, false));
        box.addView(id, Ui.wide(this, 12));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sign in with GitHub")
                .setView(box)
                .setNeutralButton("Open GitHub", (d, w) -> open("https://github.com/settings/applications/new"))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    store.setGithubClientId(id.getText().toString());
                    if (!store.githubClientId().isEmpty()) {
                        startDeviceSignIn();
                    }
                })
                .show();
    }

    /** Shows the code, opens github.com/login/device, and waits for the approval. */
    private void startDeviceSignIn() {
        final ai.dvine.connect.GitHubLogin login = new ai.dvine.connect.GitHubLogin(ai.dvine.connect.Connectors.https(),
                store.githubClientId());
        android.widget.Toast.makeText(this, "Asking GitHub for a code\u2026", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final ai.dvine.connect.GitHubLogin.Code code;
            try {
                code = login.start();
            } catch (java.io.IOException | RuntimeException e) {
                main.post(() -> android.widget.Toast.makeText(this, "Couldn't start: " + e.getMessage() + ".",
                        android.widget.Toast.LENGTH_LONG).show());
                return;
            }
            main.post(() -> showCode(login, code));
        }, "dvine-github-code").start();
    }

    private void showCode(final ai.dvine.connect.GitHubLogin login, final ai.dvine.connect.GitHubLogin.Code code) {
        if (isFinishing()) {
            return;
        }
        TextView big = Ui.text(this, code.userCode, 30, Ui.TEXT, true);
        big.setGravity(Gravity.CENTER);
        big.setTextIsSelectable(true);
        big.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, 0, p, 0);
        box.addView(Ui.text(this, "On GitHub, sign in, type this code and approve DVINE. This screen finishes by itself.",
                14, Ui.SUB, false));
        box.addView(big);
        signingIn = true;
        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sign in with GitHub")
                .setView(box)
                .setPositiveButton("Copy code and open GitHub", null)
                .setNegativeButton("Cancel", (d, w) -> signingIn = false)
                .setOnCancelListener(d -> signingIn = false)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            android.content.ClipboardManager clips = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clips != null) {
                clips.setPrimaryClip(android.content.ClipData.newPlainText("GitHub code", code.userCode));
            }
            open(code.verifyUrl);
        });
        new Thread(() -> {
            int wait = Math.max(5, code.intervalSeconds);
            long until = System.currentTimeMillis() + code.expiresSeconds * 1000L;
            String why = null, token = null;
            while (signingIn && System.currentTimeMillis() < until) {
                try {
                    Thread.sleep(wait * 1000L);
                    if (!signingIn) {
                        return;
                    }
                    ai.dvine.connect.GitHubLogin.Poll got = login.poll(code, wait);
                    if (got.token != null) {
                        token = got.token;
                        break;
                    }
                    wait = Math.max(wait, got.waitSeconds);
                } catch (InterruptedException e) {
                    return;
                } catch (java.io.IOException | RuntimeException e) {
                    why = e.getMessage();
                    break;
                }
            }
            String user = "";
            if (token != null) {
                try {
                    user = ai.dvine.connect.GitHubLogin.user(ai.dvine.connect.Connectors.https(), token);
                } catch (java.io.IOException | RuntimeException ignored) {
                    // signed in either way
                }
            }
            final String t = token, u = user, problem = token == null && why == null ? "the code ran out" : why;
            main.post(() -> {
                signingIn = false;
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (isFinishing()) {
                    return;
                }
                if (t != null) {
                    store.setToken(ConnectorStore.Kind.GITHUB, t);
                    store.setGithubUser(u);
                    store.setOn(ConnectorStore.Kind.GITHUB, true);
                    android.widget.Toast.makeText(this, u.isEmpty() ? "Signed in to GitHub." : "Signed in as @" + u + ".",
                            android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(this, "Not signed in: " + problem + ".", android.widget.Toast.LENGTH_LONG).show();
                }
                fill();
            });
        }, "dvine-github-wait").start();
    }

    private String status(ConnectorStore.Kind k) {
        if (!store.on(k)) {
            return "Off";
        }
        if (store.ready(k) && k == ConnectorStore.Kind.GITHUB && !store.githubUser().isEmpty()) {
            return "Connected as @" + store.githubUser();
        }
        if (k == ConnectorStore.Kind.WEB) {
            return "On \u00B7 " + store.webSearch().label();
        }
        if (store.ready(k)) {
            return k == ConnectorStore.Kind.MCP ? "Connected: " + store.mcpTools().size() + " tools" : "Connected";
        }
        switch (k) {
            case CALENDAR: return "Needs calendar permission";
            case MCP: return "Needs an address, then Test";
            case GITHUB: return "Sign in, or paste a token";
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
