package ai.dvine.chat.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.Map;

import ai.dvine.llm.LocalModel;
import ai.dvine.llm.ModelHub;
import ai.dvine.llm.ProviderStore;
import ai.dvine.llm.Providers;
import ai.dvine.llm.RemoteBrain;

/**
 * The AI providers sheet: the brain on this phone, or an API with the user's own key (Anthropic, OpenAI, Google
 * Gemini, OpenRouter, a custom gateway), up to three in a switching order (primary, then backups). A tapped card opens to its key (hidden, with Show), a backup key, the
 * address, the model and an optional CA certificate; Test makes one tiny call and marks it Connected. Keys are kept
 * on this phone only, encrypted ({@link ProviderStore}). Done saves and goes back to the chat.
 */
public final class ProvidersActivity extends Activity {
    private ProviderStore store;
    private Providers chosen;
    private LinearLayout cards;
    private final Map<Providers, Form> forms = new EnumMap<Providers, Form>(Providers.class);
    private final Handler main = new Handler(Looper.getMainLooper());

    /** One card's fields. */
    private static final class Form {
        EditText key, backup, address, model, ca;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProviderStore(this);
        chosen = store.active();

        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0xB3000000);
        dim.setOnClickListener(v -> finish());

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Ui.round(this, Ui.SHEET, Ui.BORDER, 30));
        int pad = Ui.dp(this, 22);
        sheet.setPadding(pad, pad, pad, pad);
        sheet.setClickable(true); // a tap inside doesn't close it
        int max = Ui.dp(this, 560);
        // the sheet fills the screen within margins and the list takes what is left, so Done is always full size
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        int side = Ui.dp(this, 16);
        sp.setMargins(side, Ui.dp(this, 48), side, Ui.dp(this, 32));
        if (getResources().getDisplayMetrics().widthPixels > max + 2 * side) {
            sp.width = max;
        }
        dim.addView(sheet, sp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = Ui.text(this, "⚿", 22, Ui.ACCENT, false);
        head.addView(icon);
        TextView title = Ui.text(this, "AI providers", 26, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 10), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        sheet.addView(head);
        TextView lead = Ui.text(this, "Choose a provider and add your key. Keys are stored on this device.", 14, Ui.SUB, false);
        sheet.addView(lead, Ui.wide(this, 12));

        // automatic switching between up to three APIs
        LinearLayout auto = new LinearLayout(this);
        auto.setGravity(Gravity.CENTER_VERTICAL);
        auto.setBackground(Ui.round(this, Ui.CARD, Ui.BORDER, 16));
        auto.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        LinearLayout autoText = new LinearLayout(this);
        autoText.setOrientation(LinearLayout.VERTICAL);
        autoText.addView(Ui.text(this, "Switch automatically", 16, Ui.TEXT, true));
        autoText.addView(Ui.text(this, "If the primary fails, is out of credit or goes quiet, a backup answers. Up to three.",
                12, Ui.SUB, false));
        auto.addView(autoText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(store.autoSwitch());
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT));
        toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        toggle.setOnCheckedChangeListener((b, on) -> {
            store.setAutoSwitch(on);
            fill();
        });
        auto.addView(toggle);
        sheet.addView(auto, Ui.wide(this, 14));

        ScrollView scroll = new ScrollView(this);
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = Ui.dp(this, 12);
        sheet.addView(scroll, lp);

        TextView done = Ui.button(this, "Done", true);
        done.setOnClickListener(v -> {
            saveAll();
            setResult(RESULT_OK);
            finish();
        });
        sheet.addView(done, Ui.wide(this, 16));
        setContentView(dim);
        fill();
    }

    private void fill() {
        saveAll();
        cards.removeAllViews();
        forms.clear();
        for (Providers p : Providers.values()) {
            cards.addView(card(p), Ui.wide(this, cards.getChildCount() == 0 ? 0 : 12));
        }
    }

    private View card(final Providers p) {
        boolean on = p == chosen;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, on ? Ui.CARD_ON : Ui.CARD, on ? Ui.BORDER_ON : Ui.BORDER, 22));
        int pad = Ui.dp(this, 18);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String place = place(p);
        TextView radio = Ui.text(this, place != null ? "◉" : "○", 24, place != null ? Ui.CORAL : Ui.GREY, false);
        row.addView(radio);
        TextView name = Ui.text(this, p.title, 18, Ui.TEXT, true);
        name.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (place != null) {
            TextView pl = Ui.pill(this, place);
            pl.setTextColor(Ui.ON_CORAL);
            pl.setBackground(Ui.round(this, Ui.CORAL, 0, 14));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.rightMargin = Ui.dp(this, 6);
            row.addView(pl, pp);
        }
        String badge = badge(p);
        if (badge != null) {
            row.addView(Ui.pill(this, badge));
        }
        card.addView(row);
        card.setOnClickListener(v -> {
            if (chosen != p) {
                chosen = p;
                fill();
            }
        });
        if (!on) {
            return card;
        }
        card.addView(Ui.text(this, p.about, 13, Ui.SUB, false), Ui.wide(this, 10));
        if (!p.api()) {
            phoneBrain(card);
            if (!store.order().isEmpty()) {
                TextView only = Ui.button(this, "Use only this phone", false);
                only.setOnClickListener(v -> {
                    for (Providers q : store.order()) {
                        store.removeBackup(q);
                    }
                    store.setActive(Providers.ON_PHONE);
                    fill();
                });
                card.addView(only, Ui.wide(this, 12));
            }
            return card;
        }
        card.addView(useAs(p), Ui.wide(this, 14));
        final Form f = new Form();
        forms.put(p, f);
        // the key, hidden, with Show
        FrameLayout keyBox = new FrameLayout(this);
        f.key = Ui.field(this, "API key", store.key(p), true, false);
        f.key.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 70), Ui.dp(this, 14));
        keyBox.addView(f.key);
        TextView show = Ui.text(this, "Show", 15, Ui.ACCENT, false);
        show.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        show.setOnClickListener(v -> {
            boolean hidden = (f.key.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            f.key.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            f.key.setSelection(f.key.getText().length());
            show.setText(hidden ? "Hide" : "Show");
        });
        keyBox.addView(show, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        card.addView(keyBox, Ui.wide(this, 14));

        f.backup = Ui.field(this, "Backup key (used when the first is refused or out of credit)", store.backupKey(p), true, false);
        final TextView addBackup = Ui.text(this, "Add backup key", 15, Ui.ACCENT, false);
        addBackup.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), 0, Ui.dp(this, 4));
        boolean hasBackup = !store.backupKey(p).isEmpty();
        f.backup.setVisibility(hasBackup ? View.VISIBLE : View.GONE);
        addBackup.setVisibility(hasBackup ? View.GONE : View.VISIBLE);
        addBackup.setOnClickListener(v -> {
            f.backup.setVisibility(View.VISIBLE);
            addBackup.setVisibility(View.GONE);
            f.backup.requestFocus();
        });
        card.addView(addBackup);
        card.addView(f.backup, Ui.wide(this, 10));

        f.address = Ui.field(this, "https://… (the API's base address)", store.address(p), false, false);
        card.addView(f.address, Ui.wide(this, 12));
        f.model = Ui.field(this, "Model", store.model(p), false, false);
        card.addView(f.model, Ui.wide(this, 12));
        String[] models = p.suggestions();
        if (models.length > 0) {
            HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
            chipsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(this);
            for (final String m : models) {
                TextView chip = Ui.text(this, m, 13, Ui.TEXT, false);
                chip.setBackground(Ui.round(this, Ui.FIELD, 0, 14));
                chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
                chip.setOnClickListener(v -> f.model.setText(m));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.rightMargin = Ui.dp(this, 8);
                chips.addView(chip, cp);
            }
            chipsScroll.addView(chips);
            card.addView(chipsScroll, Ui.wide(this, 8));
        }
        f.ca = Ui.field(this, "CA certificate (optional)", store.certificate(p), false, true);
        card.addView(f.ca, Ui.wide(this, 12));
        card.addView(Ui.text(this, "Paste a CA certificate (PEM) only if the endpoint uses a private or regional CA not trusted by the"
                + " system. It is trusted in addition to the system CAs; the chain is still verified.", 13, Ui.SUB, true), Ui.wide(this, 10));
        if (p == Providers.ANTHROPIC) {
            card.addView(Ui.text(this, "Claude Opus 5.5, Opus 5, Sonnet 5.5 and Fable 5.1 are asked with Anthropic's refusal fallback on:"
                    + " if the model declines, Anthropic answers with another model in the same call.", 13, Ui.SUB, false), Ui.wide(this, 8));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        final TextView status = Ui.text(this, "", 13, Ui.SUB, false);
        TextView test = Ui.text(this, "Test", 15, Ui.TEXT, true);
        test.setBackground(Ui.round(this, Ui.FIELD, Ui.BORDER_ON, 16));
        test.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        test.setOnClickListener(v -> test(p, status));
        actions.addView(test);
        status.setPadding(Ui.dp(this, 12), 0, 0, 0);
        actions.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView forget = Ui.text(this, "Remove", 14, Ui.ACCENT, false);
        forget.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), 0, Ui.dp(this, 10));
        forget.setOnClickListener(v -> {
            store.clear(p);
            forms.remove(p);
            if (chosen == p) {
                fill();
            }
        });
        actions.addView(forget);
        card.addView(actions, Ui.wide(this, 14));
        return card;
    }

    /** The phone's own brain: whether it is here, and the download. */
    private void phoneBrain(LinearLayout card) {
        final ModelHub hub = ModelHub.get(this);
        final LocalModel m = hub.model;
        String state;
        switch (m.state()) {
            case READY: state = "Installed and running."; break;
            case INSTALLED: state = "Installed. It starts when the chat opens."; break;
            case LOADING: state = "Starting…"; break;
            case DOWNLOADING: state = "Downloading: " + hub.status.percent() + "%"; break;
            case UNSUPPORTED: state = "Can't run on this phone: " + m.problem() + "."; break;
            case FAILED: state = "Couldn't start: " + m.problem() + "."; break;
            default: state = "Not downloaded yet. " + m.spec.sizeWords() + ", best on Wi-Fi."; break;
        }
        card.addView(Ui.text(this, state, 14, Ui.TEXT, false), Ui.wide(this, 12));
        if (m.state() == LocalModel.State.NOT_INSTALLED) {
            TextView get = Ui.button(this, "Download the brain", true);
            get.setOnClickListener(v -> {
                hub.startDownload(this);
                get.setText("Downloading in the background…");
                get.setEnabled(false);
                get.setAlpha(0.6f);
            });
            card.addView(get, Ui.wide(this, 12));
        }
        card.addView(Ui.text(this, "With an API chosen, the phone's brain still answers when there is no connection.", 13, Ui.SUB, false),
                Ui.wide(this, 12));
    }

    /** "Primary", "Backup 1", "Backup 2" (or, with switching off, only "Primary"); null when not used. */
    private String place(Providers p) {
        if (!p.api()) {
            return store.order().isEmpty() ? "In use" : null;
        }
        int i = store.place(p);
        if (i < 0 || (i > 0 && !store.autoSwitch())) {
            return null;
        }
        return i == 0 ? "Primary" : "Backup " + i;
    }

    /** Primary · Backup · Off, for one API. */
    private View useAs(final Providers p) {
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(Ui.round(this, Ui.FIELD, 0, 14));
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        int at = store.place(p);
        String[] labels = {"Primary", "Backup", "Off"};
        boolean[] on = {at == 0, at > 0, at < 0};
        for (int i = 0; i < 3; i++) {
            final int which = i;
            TextView t = Ui.text(this, labels[i], 14, on[i] ? Ui.ON_CORAL : Ui.TEXT, on[i]);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(this, 40));
            if (on[i]) {
                t.setBackground(Ui.round(this, Ui.CORAL, 0, 11));
            }
            t.setOnClickListener(v -> {
                saveAll();
                if (which == 0) {
                    store.setActive(p);
                } else if (which == 1) {
                    if (store.place(p) == 0) { // the primary steps back: the next takes the lead
                        store.removeBackup(p);
                        java.util.List<Providers> rest = store.order();
                        store.setActive(rest.isEmpty() ? Providers.ON_PHONE : rest.get(0));
                    }
                    if (!store.addBackup(p)) {
                        android.widget.Toast.makeText(this, "Three are already in the order. Turn one off first.",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    boolean wasPrimary = store.place(p) == 0;
                    store.removeBackup(p);
                    if (wasPrimary) {
                        java.util.List<Providers> rest = store.order();
                        store.setActive(rest.isEmpty() ? Providers.ON_PHONE : rest.get(0));
                    }
                }
                fill();
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.text(this, "Use as", 13, Ui.SUB, false));
        box.addView(seg, Ui.wide(this, 6));
        return box;
    }

    private String badge(Providers p) {
        if (!p.api()) {
            LocalModel m = ModelHub.get(this).model;
            return m.localReady() ? "Ready" : m.installed() ? "Installed" : null;
        }
        return store.connected(p) ? "Connected" : store.hasKey(p) ? "Key saved" : null;
    }

    private void saveAll() {
        for (Map.Entry<Providers, Form> e : forms.entrySet()) {
            Form f = e.getValue();
            store.save(e.getKey(), f.address.getText().toString(), f.model.getText().toString(), f.key.getText().toString(),
                    f.backup.getText().toString(), f.ca.getText().toString());
        }
    }

    /** One tiny call with these settings; on success the card says Connected. */
    private void test(final Providers p, final TextView status) {
        saveAll();
        String why = RemoteBrain.check(store.address(p), store.model(p), store.key(p));
        if (why != null) {
            status.setText("Can't test: " + why + ".");
            return;
        }
        final RemoteBrain brain = store.brain(p);
        status.setText("Testing…");
        new Thread(() -> {
            java.util.List<String[]> turns = new java.util.ArrayList<String[]>();
            turns.add(new String[]{"user", "Reply with the single word OK."});
            final String answer = brain.chat("You are a connection test.", turns, 16, 0.2f, null);
            final String problem = brain.problem();
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                boolean ok = answer != null && !answer.trim().isEmpty();
                store.setConnected(p, ok);
                status.setText(ok ? "Connected: " + brain.model + " answered." : "Failed: " + problem + ".");
                if (ok) {
                    fill();
                }
            });
        }, "dvine-provider-test").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAll();
    }
}
