package ai.ibiko;

import java.io.File;
import java.util.List;

/**
 * Auto-correction on the way in: the person's typos are fixed before a brain reads the message ({@link
 * ai.ibiko.learn.TypoFix}), and "no, it's …" after any model's reply is kept as the right answer, so every brain gets
 * it next time (Ibiko 1's memory goes into every model's brief, and its G8 check corrects a reply that contradicts it).
 */
public final class AutoCorrect {
    /** Settings › Chat: fix typos in the person's messages. */
    public static final String TYPOS = "fix_typos";

    private final MainActivity a;
    private ai.ibiko.learn.OwnWords own;

    AutoCorrect(MainActivity a) {
        this.a = a;
    }

    private synchronized ai.ibiko.learn.OwnWords own() {
        if (own == null) {
            own = new ai.ibiko.learn.OwnWords(new File(a.getFilesDir(), "learning/own_words.tsv"));
        }
        return own;
    }

    /**
     * The message with its clear typos fixed, for the brain; the chat keeps what was typed. The note says what was read
     * differently, and a tap on it keeps the person's spelling from then on.
     */
    String typos(String text) {
        if (text == null || !a.prefs.getBoolean(TYPOS, true)) {
            return text;
        }
        if (!ai.ibiko.learn.WordBook.ready()) {
            new Thread(ai.ibiko.learn.WordBook::ensure, "ibiko-words").start(); // ready for the next message
            return text;
        }
        final ai.ibiko.learn.OwnWords mine = own();
        ai.ibiko.learn.TypoFix.Result r = ai.ibiko.learn.TypoFix.fix(text, ai.ibiko.learn.TypoFix.DEVICE, mine.own());
        new Thread(() -> mine.heard(text, ai.ibiko.learn.TypoFix.DEVICE), "ibiko-own-words").start();
        if (r.fixes.isEmpty()) {
            return text;
        }
        final List<String[]> fixes = r.fixes;
        a.note(r.said() + " Tap to keep your spelling.");
        android.view.View n = a.findViewById(R.id.composer_note);
        if (n != null) {
            n.setOnClickListener(v -> {
                for (String[] f : fixes) {
                    mine.keep(f[0]);
                }
                a.note("Kept. I won't change " + (fixes.size() == 1 ? "“" + fixes.get(0)[0] + "”" : "those words") + " again.");
            });
        }
        return r.text;
    }

    /**
     * "No, it's …" right after a reply from any model: the reply is marked wrong for that question and the person's words
     * kept as its answer. Ibiko 1 already takes its own corrections ({@link ai.ibiko.ibiko1.Ibiko1}).
     */
    void correction(String text) {
        if (text == null || a.ibikoOwn() || !ai.ibiko.ibiko1.Inquiry.corrects(text) || a.chats == null || a.chats.current() == null) {
            return;
        }
        String[] last = lastExchange(a.chats.current().messages);
        if (last == null) {
            return;
        }
        String right = ai.ibiko.ibiko1.Inquiry.correction(text);
        ai.ibiko.ibiko1.Knowledge k = a.ibiko1().knowledge();
        k.reject(last[0], last[1]);
        k.confirm(last[0], right);
        a.ibiko1().rate(last[0], last[1], false);
        a.note("Noted for next time: when you ask “" + clip(last[0].replaceAll("[?.!]+$", ""), 60) + "”, the answer is “"
                + clip(right, 80) + "”.");
    }

    /** {the question, the reply} when the last message is a reply to a question, or null. */
    static String[] lastExchange(List<ai.ibiko.chat.ChatBook.Message> ms) {
        int i = ms.size() - 1;
        while (i >= 0 && !("assistant".equals(ms.get(i).role) && "text".equals(ms.get(i).kind))) {
            if ("user".equals(ms.get(i).role)) {
                return null; // the person spoke since the last reply
            }
            i--;
        }
        if (i < 1 || ms.get(i).body == null || ms.get(i).body.trim().isEmpty()) {
            return null;
        }
        for (int j = i - 1; j >= 0; j--) {
            ai.ibiko.chat.ChatBook.Message m = ms.get(j);
            if ("user".equals(m.role)) {
                String q = m.body == null ? "" : m.body.trim();
                return ai.ibiko.ibiko1.Knowledge.meaning(q).isEmpty() ? null : new String[]{q, ms.get(i).body.trim()};
            }
        }
        return null;
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
