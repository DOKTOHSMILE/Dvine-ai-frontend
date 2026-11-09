package ai.dvine.chat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.List;

import ai.dvine.agent.LearnedIntents;
import ai.dvine.brain.LearningJournal;
import ai.dvine.chat.ChatBook;
import ai.dvine.turn.Meaning;

/**
 * What DVINE remembers, each thing on its own line with a way to forget it (the engineering plan's memory review and
 * deletion): the notes every chat sees (taught with "remember …", or kept from a deleted chat), the facts and
 * corrections the user taught, how it learned to read their phrasings, and the words it learned as commands. The
 * chat screen shares the same live stores ({@link ChatBook#open} and the others), so a forgotten thing stays forgotten.
 * The answers learned from the API models are under Usage, with their own delete.
 */
public final class MemoryActivity extends AppCompatActivity {
    private LinearLayout body;
    private ChatBook chats;
    private LearningJournal journal;
    private LearnedIntents intents;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        chats = ChatBook.open(new File(getFilesDir(), "chats.json"));
        journal = LearningJournal.open(new File(getFilesDir(), "learning"));
        intents = LearnedIntents.open(new File(getFilesDir(), "learned_intents.txt"));
        prefs = getSharedPreferences("dvine", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 8));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "What DVINE remembers", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 34), 1));
        root.addView(head);

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
        TextView intro = Ui.text(this, "Kept on this device. Forget anything with ✕; it is gone from every chat.", 14, Ui.SUB, false);
        intro.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), 0);
        body.addView(intro);

        // ---- notes
        List<String> notes = chats.notes();
        LinearLayout n = section("Notes every chat sees", notes.size(), "Forget all notes?", () -> chats.forgetAllNotes());
        if (notes.isEmpty()) {
            empty(n, "None yet. Say “remember …”, or delete a chat: what you said there is kept here.");
        }
        for (int i = notes.size() - 1; i >= 0; i--) {
            final String note = notes.get(i);
            n.addView(row(note, null, () -> chats.forgetNote(note)));
        }

        // ---- what the pilot said about themselves, told to every brain
        final ai.dvine.learn.PilotProfile profile = new ai.dvine.learn.PilotProfile(new File(getFilesDir(), "brain/pilot.tsv"));
        List<String> about = profile.lines();
        LinearLayout pr = section("What DVINE knows about you", about.size(), "Forget everything it knows about you?", profile::clear);
        if (about.isEmpty()) {
            empty(pr, "None yet. When you mention where you live, your work or your project, it is kept here and every model is told.");
        }
        for (final String line : about) {
            final String key = line.substring(0, line.indexOf(':'));
            pr.addView(row(line, null, () -> profile.forget(key)));
        }

        // ---- answers confirmed with the check
        final ai.dvine.learn.Knowledge known = new ai.dvine.learn.Knowledge(new File(getFilesDir(), "brain/knowledge.tsv"));
        List<ai.dvine.learn.Knowledge.Fact> sure = known.confirmed();
        LinearLayout kc = section("Answers you marked correct", sure.size(), "Forget every answer you marked correct?", known::clear);
        if (sure.isEmpty()) {
            empty(kc, "None yet. Tap the check under an answer that's right: DVINE answers it again however it's asked.");
        }
        for (int i = sure.size() - 1; i >= 0; i--) {
            final ai.dvine.learn.Knowledge.Fact f = sure.get(i);
            kc.addView(row("“" + f.question + "”", f.answer.length() > 140 ? f.answer.substring(0, 140) + "…" : f.answer,
                    () -> known.forget(f.question)));
        }

        // ---- taught facts and corrections
        LinearLayout t = section("Facts you taught and corrections", journal.taughtCount(), "Forget every fact you taught?",
                () -> journal.forgetTaught());
        List<LearningJournal.Entry> all = journal.all();
        int shown = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            final LearningJournal.Entry e = all.get(i);
            if (!LearningJournal.TAUGHT_FACT.equals(e.kind) && !LearningJournal.CORRECTION.equals(e.kind)) {
                continue;
            }
            t.addView(row(LearningJournal.sentence(e.text, e.answer), LearningJournal.CORRECTION.equals(e.kind) ? "A correction" : null,
                    () -> journal.forget(e.id)));
            shown++;
        }
        if (shown == 0) {
            empty(t, "None yet. Tell DVINE a fact, or correct it, and it is kept here.");
        }

        // ---- phrasings
        final Meaning.Memory meanings = Meaning.Memory.read(prefs.getString("meanings", null));
        List<String[]> read = meanings.entries();
        LinearLayout m = section("How it reads your words", read.size(), "Forget how it reads your words?", () -> {
            meanings.forgetAll();
            prefs.edit().putString("meanings", meanings.write()).apply();
        });
        int listed = 0;
        for (int i = read.size() - 1; i >= 0; i--) {
            final String[] r = read.get(i);
            if ("NONE".equals(r[1])) {
                continue; // "not a command" is kept so it isn't read twice; nothing to show
            }
            String meant;
            try {
                meant = Meaning.Command.valueOf(r[1]).canonical;
            } catch (IllegalArgumentException ex) {
                meant = r[1];
            }
            m.addView(row("“" + r[0] + "”", "means “" + meant + "”", () -> {
                meanings.forget(r[0]);
                prefs.edit().putString("meanings", meanings.write()).apply();
            }));
            listed++;
        }
        if (listed == 0) {
            empty(m, "None yet. When you say a command your own way (“are you done?”), it is kept here.");
        }

        // ---- learned command words
        List<String[]> learned = intents.entries();
        LinearLayout c = section("Words it learned as actions", learned.size(), "Forget the words it learned as actions?",
                () -> intents.forgetAll());
        if (learned.isEmpty()) {
            empty(c, "None yet.");
        }
        for (int i = learned.size() - 1; i >= 0; i--) {
            final String[] r = learned.get(i);
            c.addView(row("“" + r[0] + "”", "does “" + r[1] + "”", () -> intents.forget(r[0])));
        }

        // ---- what was done in the user's name (kept, not forgettable here: a record)
        java.util.List<String> done = ai.dvine.act.ActionLog.read(getFilesDir());
        LinearLayout a = section("Actions done in your name", 0, null, null);
        if (done.isEmpty()) {
            empty(a, "None yet. Posts, shares and copies DVINE carries out are listed here, with whether you approved them.");
        }
        for (int i = 0; i < Math.min(done.size(), 50); i++) {
            String[] f = done.get(i).split("\t", 6);
            if (f.length < 6) {
                continue;
            }
            TextView what = Ui.text(this, f[5], 15, Ui.TEXT, false);
            what.setPadding(0, Ui.dp(this, 8), 0, 0);
            a.addView(what);
            TextView sub = Ui.text(this, f[0] + " \u00B7 " + f[1] + " \u00B7 " + f[2] + " \u00B7 " + f[3] + " \u00B7 " + f[4], 12, Ui.SUB, false);
            sub.setPadding(0, 0, 0, Ui.dp(this, 8));
            a.addView(sub);
        }

        TextView more = Ui.text(this, "Answers learned from the API models are under Usage and learning.", 13, Ui.SUB, false);
        more.setPadding(Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6), 0);
        body.addView(more);
    }

    /** A titled card with a "Forget all" link (asked first) when it holds anything. */
    private LinearLayout section(String name, int count, final String ask, final Runnable forgetAll) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 4));
        top.addView(Ui.text(this, name, 15, Ui.SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
        if (count > 0) {
            TextView all = Ui.text(this, "Forget all", 14, Ui.ACCENT, false);
            all.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6));
            all.setContentDescription("Forget all: " + name);
            all.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(ask)
                    .setPositiveButton("Forget", (d, w) -> {
                        forgetAll.run();
                        fill();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            top.addView(all);
        }
        body.addView(top);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        body.addView(card, Ui.wide(this, 4));
        return card;
    }

    /** One remembered thing, with ✕ to forget it at once. */
    private LinearLayout row(String what, String under, final Runnable forget) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 48));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        words.addView(Ui.text(this, what, 15, Ui.TEXT, false));
        if (under != null) {
            words.addView(Ui.text(this, under, 13, Ui.SUB, false));
        }
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView x = new ImageView(this);
        x.setImageResource(ai.dvine.R.drawable.ic_close);
        x.setColorFilter(Ui.SUB);
        int p = Ui.dp(this, 12);
        x.setPadding(p, p, p, p);
        x.setContentDescription("Forget: " + what);
        x.setOnClickListener(v -> {
            forget.run();
            fill();
        });
        r.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return r;
    }

    private void empty(LinearLayout card, String text) {
        TextView t = Ui.text(this, text, 14, Ui.SUB, false);
        t.setPadding(0, Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        card.addView(t);
    }
}
