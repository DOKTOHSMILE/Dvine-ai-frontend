package ai.ibiko.dvine1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How answers came about, not only what they were. Every finished reply, from any brain, leaves the way it was
 * reached: the kind of request and the ordered steps that led to the answer (looked it up, read the pages, read the
 * files, ran a command, edited, checked, worked it out). The same way seen again grows stronger; the pilot's check
 * makes it much stronger and a cross weakens it, but nothing waits for a tap: a way is learned the moment it is used.
 *
 * <p>What is learned is used in two places. DVINE 1 follows the strongest way it knows for that kind of request when
 * it answers by itself, and any brain is handed it as a hint ("for requests like this, what worked before: …"), so the
 * next answer starts from a method instead of from nothing. A way that keeps failing is dropped, so the methods change
 * as the pilot's work changes. Pure Java; kept in {@code brain/methods.tsv} on the device.
 */
public final class Methods {
    /** One kind of step in reaching an answer. */
    public enum Step {
        RECALL("check what is already known"), SEARCH("search the web"), READ_PAGE("read the best pages"),
        READ_FILES("read the files"), FIND("search the code"), PLAN("plan before building"), WRITE("write the files"),
        EDIT("make the change"), RUN("run it"), TEST("run the checks"), CALC("work it out exactly"),
        GIT("commit and push"), WORKERS("split it among workers"), TOGETHER("think it through together"),
        FUNCTION("use the app's own function"), ANSWER("write the answer");

        public final String said;

        Step(String said) {
            this.said = said;
        }
    }

    /** One learned way of handling a kind of request. */
    public static final class Way {
        public final String kind;
        public final List<Step> steps;
        public int seen, right, wrong;
        public long last;

        Way(String kind, List<Step> steps) {
            this.kind = kind;
            this.steps = steps;
        }

        /** How far it is trusted: use counts a little, the pilot's check a lot, a cross against it more. */
        public int strength() {
            return Math.min(seen, 10) + right * 3 - wrong * 4;
        }

        /** "search the web, read the best pages, then write the answer". */
        public String said() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < steps.size(); i++) {
                b.append(i == 0 ? "" : i == steps.size() - 1 ? ", then " : ", ").append(steps.get(i).said);
            }
            return b.toString();
        }
    }

    static final int MOST = 400;
    /** A way is offered as a hint once it has been seen twice, or the pilot marked it right. */
    static final int TRUSTED = 2;

    private final File file;
    private final List<Way> ways = new ArrayList<Way>();

    public Methods(File file) {
        this.file = file;
        load();
    }

    /**
     * The kind of request, so that one way serves every request of that kind: what answer it wants and how it opens
     * ("how do i", "fix the", "write a"), without its particulars.
     */
    public static String kind(String request) {
        String t = (request == null ? "" : request).toLowerCase(Locale.US).replaceAll("https?://\\S+", " ")
                .replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim()
                .replaceFirst("^(?:(?:hey|hi|ok|okay|please|obi|ivie|ibiko|can you|could you|would you|i want you to|i need you to|kindly)\\s+)+", "");
        String[] w = t.isEmpty() ? new String[0] : t.split(" ");
        StringBuilder open = new StringBuilder();
        for (int i = 0; i < w.length && i < 3; i++) {
            if (i > 0 && !w[i].matches("is|are|was|were|do|does|did|can|i|you|we|the|a|an|to|me|my|this|that|it|many|much|long|old|far|about|up|out|into|for")) {
                break; // the request's own subject starts here
            }
            open.append(open.length() == 0 ? "" : " ").append(w[i]);
        }
        return Knowledge.kind(request).name().toLowerCase(Locale.US) + ":" + open;
    }

    /** The steps a turn showed in the chat ("Searching the web for …", "Read app/Main.java", "Ran 2 commands"), as kinds, in order. */
    public static List<Step> steps(List<String> shown) {
        List<Step> out = new ArrayList<Step>();
        for (String line : shown == null ? new ArrayList<String>() : shown) {
            Step s = step(line);
            if (s != null && (out.isEmpty() || out.get(out.size() - 1) != s)) {
                out.add(s); // the same kind twice in a row is one step of the method
            }
        }
        if (!out.isEmpty() && out.get(out.size() - 1) != Step.ANSWER) {
            out.add(Step.ANSWER);
        }
        return out.size() > 12 ? new ArrayList<Step>(out.subList(0, 12)) : out;
    }

    static Step step(String line) {
        String l = line == null ? "" : line.toLowerCase(Locale.US).trim();
        if (l.isEmpty() || l.matches("^(thinking|replying|writing|asking|reading your (question|message)|understood as).*")) {
            return null; // the brain being asked is not a step of the method
        }
        if (l.matches(".*\\b(started \\d+ workers|worker \\d+ of).*")) return Step.WORKERS;
        if (l.matches("^(searching the web|searched the web|looking up|not in my memory).*")) return Step.SEARCH;
        if (l.matches("^(fetched|reading https?|read https?|opened https?|reading (what i found|en\\.|\\w+\\.(org|com))).*") || l.contains("source")) return Step.READ_PAGE;
        if (l.matches("^(checking what i already know|found it|looking through my memory|done from what dvine learned).*")) return Step.RECALL;
        if (l.matches("^(blueprint|planning|plan |surveying|codebase map).*")) return Step.PLAN;
        if (l.matches("^(read |listed |explored |reading the files).*")) return Step.READ_FILES;
        if (l.matches("^(searched for|looked for|found \\d+ match).*")) return Step.FIND;
        if (l.matches("^(created |rewrote |wrote |made ).*")) return Step.WRITE;
        if (l.matches("^(edited |changed ).*")) return Step.EDIT;
        if (l.matches("^(ran |tested |checked ).*\\b(test|tests|check|gradle|pytest|unittest|lint|build)\\b.*")) return Step.TEST;
        if (l.matches("^(ran |installed |run |running ).*") || l.endsWith(" in linux")) return Step.RUN;
        if (l.matches("^(cloned|pulled|pushed|committed|opened a pull request|merged|git ).*")) return Step.GIT;
        if (l.matches("^(working it out|calculat).*")) return Step.CALC;
        if (l.matches(".*\\b(think(ing)? together|obi, ivie and ibiko)\\b.*")) return Step.TOGETHER;
        if (l.matches("^(that's something the app does|using \\S+\\.\\S+).*") || l.contains("§")) return Step.FUNCTION;
        return null;
    }

    /**
     * A reply was reached this way: learned at once, with no tap needed. The same way for the same kind of request
     * grows stronger each time.
     */
    public synchronized Way observe(String request, List<Step> steps, long now) {
        if (request == null || request.trim().isEmpty() || steps == null || steps.size() < 2) {
            return null; // a bare answer has no method to learn
        }
        String kind = kind(request);
        Way w = find(kind, steps);
        if (w == null) {
            w = new Way(kind, new ArrayList<Step>(steps));
            ways.add(w);
        }
        w.seen++;
        w.last = now;
        prune();
        save();
        return w;
    }

    /** The pilot marked the answer: the way that reached it last for that kind of request is trusted more, or less. */
    public synchronized void judge(String request, boolean right) {
        String kind = kind(request);
        Way latest = null;
        for (Way w : ways) {
            if (w.kind.equals(kind) && (latest == null || w.last > latest.last)) {
                latest = w;
            }
        }
        if (latest == null) {
            return;
        }
        if (right) {
            latest.right++;
        } else {
            latest.wrong++;
        }
        prune();
        save();
    }

    /** The strongest way known for this kind of request, or null when none is trusted yet. */
    public synchronized Way recall(String request) {
        String kind = kind(request);
        Way best = null;
        for (Way w : ways) {
            if (w.kind.equals(kind) && (w.seen >= TRUSTED || w.right > 0) && w.strength() > 0
                    && (best == null || w.strength() > best.strength() || w.strength() == best.strength() && w.last > best.last)) {
                best = w;
            }
        }
        return best;
    }

    /** For any brain, with the request: the way that worked before for requests like it; "" when none is known. */
    public synchronized String hint(String request) {
        Way w = recall(request);
        return w == null ? "" : " For requests like this one, the way that worked before on this device: " + w.said()
                + ". Follow it unless this request clearly needs another way.";
    }

    public synchronized int size() {
        return ways.size();
    }

    /** How many ways are trusted enough to be used. */
    public synchronized int trusted() {
        int n = 0;
        for (Way w : ways) {
            if ((w.seen >= TRUSTED || w.right > 0) && w.strength() > 0) {
                n++;
            }
        }
        return n;
    }

    public synchronized void clear() {
        ways.clear();
        save();
    }

    private Way find(String kind, List<Step> steps) {
        for (Way w : ways) {
            if (w.kind.equals(kind) && w.steps.equals(steps)) {
                return w;
            }
        }
        return null;
    }

    /** A way the pilot keeps marking wrong goes; past the limit, the weakest and oldest go first. */
    private void prune() {
        for (int i = ways.size() - 1; i >= 0; i--) {
            if (ways.get(i).strength() <= -4) {
                ways.remove(i);
            }
        }
        if (ways.size() > MOST) {
            Collections.sort(ways, (a, b) -> a.strength() != b.strength() ? b.strength() - a.strength() : Long.compare(b.last, a.last));
            while (ways.size() > MOST) {
                ways.remove(ways.size() - 1);
            }
        }
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t");
                if (p.length < 6) {
                    continue;
                }
                List<Step> steps = new ArrayList<Step>();
                for (String s : p[1].split(",")) {
                    try {
                        steps.add(Step.valueOf(s));
                    } catch (IllegalArgumentException old) {
                        // a step kind from another version: left out
                    }
                }
                if (steps.size() < 2) {
                    continue;
                }
                Way w = new Way(p[0], steps);
                w.seen = Integer.parseInt(p[2]);
                w.right = Integer.parseInt(p[3]);
                w.wrong = Integer.parseInt(p[4]);
                w.last = Long.parseLong(p[5]);
                ways.add(w);
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable file: it starts again from what it sees next
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Way w : ways) {
            StringBuilder s = new StringBuilder();
            for (Step st : w.steps) {
                s.append(s.length() == 0 ? "" : ",").append(st.name());
            }
            b.append(w.kind.replace('\t', ' ')).append('\t').append(s).append('\t').append(w.seen).append('\t').append(w.right)
                    .append('\t').append(w.wrong).append('\t').append(w.last).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (IOException e) {
            // kept in memory; written at the next change
        }
    }
}
