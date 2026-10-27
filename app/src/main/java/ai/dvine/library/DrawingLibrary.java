package ai.dvine.library;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.render.field.SolidSpec;

/**
 * The user's saved 3D drawings, on the phone only: each kept under the name the user gave it, with what the user
 * said about it and DVINE's own note of what it shows (the thing, its parts, its colours), so it can be found again
 * by its name or by describing it ("my red car with no wheels"). Names are unique, ignoring case. Pure Java.
 */
public final class DrawingLibrary {
    public static final class Entry {
        public final String name, subject, description, summary;
        public final long saved;
        final String file;

        Entry(String name, String subject, String description, String summary, long saved, String file) {
            this.name = name;
            this.subject = subject;
            this.description = description;
            this.summary = summary;
            this.saved = saved;
            this.file = file;
        }

        /** "a cat with orange body, green eyes", what the user said first when they said something. */
        public String about() {
            return description.isEmpty() ? summary : description;
        }
    }

    /** What a search found: the best entries, best first, and how sure (100 the name exactly). */
    public static final class Found {
        public final List<Entry> entries;
        public final int score;

        Found(List<Entry> entries, int score) {
            this.entries = entries;
            this.score = score;
        }
    }

    private final File dir;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public DrawingLibrary(File dir) {
        this.dir = dir;
        dir.mkdirs();
        try {
            File index = new File(dir, "index.tsv");
            if (index.exists()) {
                for (String line : new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String[] f = line.split("\t", -1);
                    if (f.length == 6) {
                        entries.put(key(f[0]), new Entry(f[0], f[1], f[2], f[3], Long.parseLong(f[4]), f[5]));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // a damaged index: start again (the drawings' own files stay)
        }
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<Entry>(entries.values());
        Collections.reverse(out); // newest first
        return out;
    }

    /** The drawing already saved under this name (any case), or null. */
    public Entry named(String name) {
        return name == null ? null : entries.get(key(name));
    }

    /** Keeps the drawing under {@code name}, replacing one of the same name. */
    public Entry save(String name, String subject, String description, SolidSpec spec, long now) throws IOException {
        Entry old = named(name);
        String file = old != null ? old.file : "d" + now + "_" + entries.size() + ".solid";
        try (FileOutputStream o = new FileOutputStream(new File(dir, file))) {
            o.write(spec.save().getBytes(StandardCharsets.UTF_8));
        }
        Entry e = new Entry(clean(name), clean(subject == null ? "" : subject), clean(description == null ? "" : description),
                clean(summarise(subject, spec)), now, file);
        entries.remove(key(name));
        entries.put(key(name), e);
        writeIndex();
        return e;
    }

    public SolidSpec open(Entry e) {
        try {
            return SolidSpec.load(new String(Files.readAllBytes(new File(dir, e.file).toPath()), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return new SolidSpec();
        }
    }

    public boolean delete(Entry e) {
        if (entries.remove(key(e.name)) == null) {
            return false;
        }
        new File(dir, e.file).delete();
        try {
            writeIndex();
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    /**
     * The saved drawings these words point to: the name exactly (100), the name's words all there (80), or most of
     * the words found in the name, the thing drawn, what the user said about it and DVINE's note (up to 70).
     */
    public Found find(String words) {
        List<Entry> best = new ArrayList<Entry>();
        int top = 0;
        if (words == null) {
            return new Found(best, 0);
        }
        String q = key(words.replaceAll("(?i)^(my|the|a|an|that|this) ", "").replaceAll("(?i)^(saved )?(drawing|design|model)s? (called|named|of) ", ""));
        List<String> asked = words(q);
        for (Entry e : entries.values()) {
            int score;
            String n = key(e.name);
            if (n.equals(q)) {
                score = 100;
            } else if (!asked.isEmpty() && words(n).containsAll(asked) || !words(n).isEmpty() && asked.containsAll(words(n))) {
                score = 80;
            } else {
                List<String> about = words(n + " " + e.subject + " " + e.description + " " + e.summary);
                int hit = 0;
                for (String w : asked) {
                    if (about.contains(w) || about.contains(w.replaceAll("s$", ""))) {
                        hit++;
                    }
                }
                score = asked.isEmpty() ? 0 : 70 * hit / asked.size();
            }
            if (score > top) {
                top = score;
                best.clear();
            }
            if (score == top && score > 0) {
                best.add(e);
            }
        }
        return new Found(best, top);
    }

    /** DVINE's own note of a drawing: "cat: body, head, ears, legs, tail; orange, green, pink". */
    static String summarise(String subject, SolidSpec spec) {
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<String>(), colours = new java.util.LinkedHashSet<String>();
        for (SolidSpec.Part p : spec.parts) {
            String[] w = p.name.split(" ");
            parts.add(w[w.length - 1]);
            colours.add(colourName(p.colour));
        }
        return (subject == null ? "" : subject + ": ") + String.join(", ", parts) + "; " + String.join(", ", colours);
    }

    /** The nearest everyday colour name. */
    public static String colourName(int c) {
        String[][] names = {{"white", "F2F2F2"}, {"black", "202020"}, {"grey", "9AA0A6"}, {"silver", "C8CDD2"}, {"red", "E53935"},
                {"orange", "FF9A2E"}, {"yellow", "FFD23F"}, {"gold", "D4A83A"}, {"green", "43A047"}, {"teal", "2EC4B6"},
                {"blue", "3D6BFF"}, {"navy", "22306F"}, {"sky blue", "8FD3FF"}, {"purple", "8E5BD8"}, {"pink", "FF7FB6"},
                {"brown", "8B5A2B"}, {"tan", "D8B48A"}, {"beige", "E8D8B8"}, {"cyan", "2EE6FF"}};
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        String best = "grey";
        long bd = Long.MAX_VALUE;
        for (String[] n : names) {
            int v = Integer.parseInt(n[1], 16);
            long d = sq(r - ((v >> 16) & 255)) * 3 + sq(g - ((v >> 8) & 255)) * 4 + sq(b - (v & 255)) * 2;
            if (d < bd) {
                bd = d;
                best = n[0];
            }
        }
        return best;
    }

    private static long sq(int v) {
        return (long) v * v;
    }

    private void writeIndex() throws IOException {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries.values()) {
            b.append(e.name).append('\t').append(e.subject).append('\t').append(e.description).append('\t').append(e.summary)
                    .append('\t').append(e.saved).append('\t').append(e.file).append('\n');
        }
        File tmp = new File(dir, "index.tsv.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(b.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(dir, "index.tsv"))) {
            throw new IOException("index not saved");
        }
    }

    private static String clean(String s) {
        return s.replaceAll("[\\t\\n\\r]", " ").replaceAll("\\s+", " ").trim();
    }

    static String key(String s) {
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static final java.util.Set<String> SMALL = new java.util.HashSet<String>(java.util.Arrays.asList(
            "a", "an", "the", "my", "of", "with", "and", "that", "this", "is", "it", "its", "in", "on", "one", "drawing", "design", "model", "saved", "i", "called", "named"));

    private static List<String> words(String s) {
        List<String> out = new ArrayList<String>();
        for (String w : key(s).split(" ")) {
            if (!w.isEmpty() && !SMALL.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}
