package ai.dvine.brain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What DVINE learned while talking, kept on the phone. Entries are uploaded
 * only after the user agrees, and secrets are dropped before anything is written.
 */
public final class LearningJournal {
    public static final String TAUGHT_FACT = "TAUGHT_FACT";
    public static final String CORRECTION = "CORRECTION";
    public static final String UNANSWERED = "UNANSWERED";
    public static final String FEEDBACK = "FEEDBACK";

    private static final Pattern LONG_DIGITS = Pattern.compile("\\d[\\d\\s-]{10,}\\d");
    private static final Pattern SECRET_WORDS = Pattern.compile(
            "\\b(password|passcode|pin|cvv|otp|seed phrase|private key|recovery phrase|secret key|api key|token)\\b");

    public static final class Entry {
        public final String id;
        public final String kind;
        public final String text;
        public final String answer;
        public final long createdAt;
        public boolean uploaded;

        Entry(String id, String kind, String text, String answer, long createdAt, boolean uploaded) {
            this.id = id;
            this.kind = kind;
            this.text = text;
            this.answer = answer;
            this.createdAt = createdAt;
            this.uploaded = uploaded;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("kind", kind);
            m.put("text", text);
            m.put("answer", answer);
            m.put("createdAt", (double) createdAt);
            m.put("uploaded", uploaded);
            return m;
        }
    }

    private final File file;
    private final File consentFile;
    private final List<Entry> entries = new ArrayList<Entry>();
    private boolean consent;

    public LearningJournal(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        file = new File(dir, "journal.jsonl");
        consentFile = new File(dir, "consent");
        consent = consentFile.exists();
        load();
    }

    /** True when text looks like a password, card number, key or code. */
    public static boolean sensitive(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.US);
        return LONG_DIGITS.matcher(lower).find() || SECRET_WORDS.matcher(lower).find();
    }

    /** Adds an entry unless it holds sensitive data. Returns the entry, or null if it was dropped. */
    public synchronized Entry add(String kind, String text, String answer) {
        if (sensitive(text) || sensitive(answer)) {
            return null;
        }
        Entry e = new Entry(UUID.randomUUID().toString(), kind, text == null ? "" : text,
                answer == null ? "" : answer, System.currentTimeMillis(), false);
        entries.add(e);
        append(e);
        return e;
    }

    public synchronized List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    /** Latest taught answer whose key matches, or null. */
    public synchronized String recall(String key) {
        String k = normalizeKey(key);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if ((TAUGHT_FACT.equals(e.kind) || CORRECTION.equals(e.kind)) && normalizeKey(e.text).equals(k)) {
                return e.answer;
            }
        }
        return null;
    }

    public synchronized int taughtCount() {
        int n = 0;
        for (Entry e : entries) {
            if (TAUGHT_FACT.equals(e.kind) || CORRECTION.equals(e.kind)) {
                n++;
            }
        }
        return n;
    }

    /** Removes the newest taught fact. Returns false when there was nothing to forget. */
    public synchronized boolean forgetLast() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            String kind = entries.get(i).kind;
            if (TAUGHT_FACT.equals(kind) || CORRECTION.equals(kind)) {
                entries.remove(i);
                rewrite();
                return true;
            }
        }
        return false;
    }

    public synchronized void forgetAll() {
        entries.clear();
        rewrite();
    }

    public synchronized boolean consent() {
        return consent;
    }

    public synchronized void setConsent(boolean on) {
        consent = on;
        try {
            if (on) {
                new FileOutputStream(consentFile).close();
            } else if (consentFile.exists() && !consentFile.delete()) {
                throw new IOException("cannot clear consent");
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Entries waiting to go to the server, oldest first, at most {@code max}. */
    public synchronized List<Entry> pending(int max) {
        List<Entry> out = new ArrayList<Entry>();
        if (!consent) {
            return out;
        }
        for (Entry e : entries) {
            if (!e.uploaded) {
                out.add(e);
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    public synchronized void markUploaded(List<String> ids) {
        boolean changed = false;
        for (Entry e : entries) {
            if (!e.uploaded && ids.contains(e.id)) {
                e.uploaded = true;
                changed = true;
            }
        }
        if (changed) {
            rewrite();
        }
    }

    /** Body for POST /api/v1/learning/sync. */
    public static String syncBody(String deviceId, List<Entry> batch) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("schemaVersion", 1.0);
        body.put("deviceId", deviceId);
        List<Object> list = new ArrayList<Object>();
        for (Entry e : batch) {
            Map<String, Object> m = e.toMap();
            m.remove("uploaded");
            list.add(m);
        }
        body.put("entries", list);
        return MiniJson.write(body);
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.US).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> m = MiniJson.obj(MiniJson.parse(line));
                    entries.add(new Entry(MiniJson.str(m.get("id"), UUID.randomUUID().toString()),
                            MiniJson.str(m.get("kind"), FEEDBACK), MiniJson.str(m.get("text"), ""),
                            MiniJson.str(m.get("answer"), ""), (long) MiniJson.num(m.get("createdAt"), 0),
                            Boolean.TRUE.equals(m.get("uploaded"))));
                } catch (IllegalArgumentException ignored) {
                    // a torn last line after a crash is skipped, not fatal
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void append(Entry e) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(MiniJson.write(e.toMap()));
            w.write('\n');
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void rewrite() {
        File tmp = new File(file.getParentFile(), "journal.jsonl.tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (Entry e : entries) {
                w.write(MiniJson.write(e.toMap()));
                w.write('\n');
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        if (!tmp.renameTo(file)) {
            throw new IllegalStateException("cannot replace journal");
        }
    }
}
