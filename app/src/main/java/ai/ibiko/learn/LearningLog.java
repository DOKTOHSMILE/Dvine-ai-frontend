package ai.ibiko.learn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.ibiko.brain.MiniJson;

/**
 * Ibiko learning from the API models it talks to: every answer an API model gives (the instructions, the
 * conversation, the request, the reply, which model, and Ibiko's steps, which arrive as their own calls) is kept on
 * this phone, one JSON line each. It is used two ways. Now: when the phone's own model answers, the closest past
 * answers are given to it as examples ({@link #examplesFor}). Later: {@link #exportForTraining} writes the lot in
 * the chat format fine-tuning tools read, to train Ibiko's own model on a GPU. Keys are never part of a record;
 * nothing leaves the phone unless the user exports it. Pure Java.
 */
public final class LearningLog {
    /** The file stops growing past this; the oldest half is dropped. */
    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final File file;

    public LearningLog(File dir) {
        dir.mkdirs();
        this.file = new File(dir, "interactions.jsonl");
    }

    public File file() {
        return file;
    }

    /** One API answer: the instructions, the turns (role, text), the reply, and who wrote it. */
    public synchronized void record(String system, List<String[]> turns, String reply, String source, String tier, long at) {
        if (reply == null || reply.trim().isEmpty() || turns == null || turns.isEmpty()) {
            return;
        }
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("at", (double) at);
        r.put("source", source == null ? "" : source);
        r.put("tier", tier == null ? "" : tier);
        r.put("system", system == null ? "" : system.replace("/no_think", "").trim());
        List<Object> msgs = new ArrayList<Object>();
        for (String[] t : turns) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("role", "assistant".equals(t[0]) ? "assistant" : "user");
            m.put("content", t[1] == null ? "" : t[1]);
            msgs.add(m);
        }
        r.put("messages", msgs);
        r.put("reply", reply.trim());
        try {
            if (file.length() > MAX_BYTES) {
                dropOldestHalf();
            }
            try (FileOutputStream o = new FileOutputStream(file, true)) {
                o.write((MiniJson.write(r) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // learning is a bonus: a full disk must not stop the answer
        }
    }

    /** How many answers are kept. */
    public synchronized int count() {
        int n = 0;
        for (String line : lines()) {
            if (!line.trim().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public synchronized long bytes() {
        return file.length();
    }

    public synchronized void clear() {
        file.delete();
    }

    /**
     * The closest past answers to this request, as examples for the phone's own model ("A question like yours, and
     * how it was answered"), at most {@code most} of them and {@code maxChars} long; empty when none is close.
     * Only conversation answers count (not one-line orders or Ibiko's tool steps).
     */
    public synchronized String examplesFor(String request, int most, int maxChars) {
        Set<String> want = words(request);
        if (want.size() < 2) {
            return "";
        }
        List<double[]> scored = new ArrayList<double[]>();
        List<String[]> pairs = new ArrayList<String[]>();
        for (String line : lines()) {
            Map<String, Object> r;
            try {
                r = MiniJson.obj(MiniJson.parse(line));
            } catch (RuntimeException e) {
                continue;
            }
            String reply = MiniJson.str(r.get("reply"), "");
            if (reply.startsWith("DO ") || reply.startsWith("SAY ") || reply.startsWith("CANNOT") || reply.contains("<tool ")) {
                continue; // routing orders and agent steps aren't answers to imitate
            }
            List<Object> msgs = MiniJson.list(r.get("messages"));
            if (msgs.isEmpty()) {
                continue;
            }
            String asked = MiniJson.str(MiniJson.obj(msgs.get(msgs.size() - 1)).get("content"), "");
            int cut = asked.lastIndexOf("The user says: ");
            if (cut >= 0) {
                asked = asked.substring(cut + 15);
            }
            Set<String> have = words(asked);
            int same = 0;
            for (String w : want) {
                if (have.contains(w)) {
                    same++;
                }
            }
            double score = same / (double) (want.size() + have.size() - same);
            if (score >= 0.3) {
                scored.add(new double[]{score, pairs.size()});
                pairs.add(new String[]{asked.trim(), reply.trim()});
            }
        }
        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        StringBuilder out = new StringBuilder();
        Set<String> used = new HashSet<String>();
        for (double[] s : scored) {
            String[] p = pairs.get((int) s[1]);
            if (used.contains(p[0]) || used.size() >= most) {
                continue;
            }
            String block = "Q: " + clip(p[0], 300) + "\nA: " + clip(p[1], 700) + "\n";
            if (out.length() + block.length() > maxChars) {
                break;
            }
            used.add(p[0]);
            out.append(block);
        }
        return out.length() == 0 ? "" : "Examples of how questions like this were answered before (use them as a guide):\n" + out;
    }

    /**
     * All answers in the chat format fine-tuning tools read: one {"messages": [system, …turns, assistant reply]} a
     * line. Returns how many were written.
     */
    public synchronized int exportForTraining(java.io.OutputStream out) throws IOException {
        int n = 0;
        for (String line : lines()) {
            Map<String, Object> r;
            try {
                r = MiniJson.obj(MiniJson.parse(line));
            } catch (RuntimeException e) {
                continue;
            }
            List<Object> msgs = new ArrayList<Object>();
            String system = MiniJson.str(r.get("system"), "");
            if (!system.isEmpty()) {
                Map<String, Object> s = new LinkedHashMap<String, Object>();
                s.put("role", "system");
                s.put("content", system);
                msgs.add(s);
            }
            msgs.addAll(MiniJson.list(r.get("messages")));
            Map<String, Object> a = new LinkedHashMap<String, Object>();
            a.put("role", "assistant");
            a.put("content", MiniJson.str(r.get("reply"), ""));
            msgs.add(a);
            Map<String, Object> example = new LinkedHashMap<String, Object>();
            example.put("messages", msgs);
            out.write((MiniJson.write(example) + "\n").getBytes(StandardCharsets.UTF_8));
            n++;
        }
        return n;
    }

    private List<String> lines() {
        try {
            if (!file.isFile()) {
                return new ArrayList<String>();
            }
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }

    private void dropOldestHalf() throws IOException {
        List<String> all = lines();
        List<String> keep = all.subList(all.size() / 2, all.size());
        Files.write(file.toPath(), (String.join("\n", keep) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static final Set<String> SMALL = new HashSet<String>(java.util.Arrays.asList("the", "a", "an", "is", "are", "of",
            "to", "in", "on", "and", "or", "for", "with", "what", "how", "why", "who", "me", "my", "you", "your", "it", "i",
            "can", "do", "does", "please", "about", "this", "that", "be", "was", "were"));

    static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        if (s == null) {
            return out;
        }
        for (String w : s.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !SMALL.contains(w)) {
                out.add(w.replaceAll("(ing|ed|es|s)$", ""));
            }
        }
        return out;
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
