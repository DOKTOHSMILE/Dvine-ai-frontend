package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The steps of a piece of work folded the way Claude Code shows them: commands in a row become "Ran 3 commands", files
 * read, listed and searched become "Explored 4 files, 2 searches", files changed become "Edited 2 files"; each opens to
 * its steps ("Ran command · Listed the files in src", "Read file · Main.java"), and each of those to its code. Other
 * steps stay as they are. Pure Java.
 */
public final class StepGroups {
    public enum Kind {
        COMMAND, EXPLORE, EDIT, WEB, OTHER
    }

    /** One step: the verb shown dim, what it acted on shown bright, where it is in the list, and its seconds. */
    public static final class Item {
        public final Kind kind;
        public final String verb, target;
        public final int index;
        public final long secs;

        Item(Kind kind, String verb, String target, int index, long secs) {
            this.kind = kind;
            this.verb = verb;
            this.target = target;
            this.index = index;
            this.secs = secs;
        }
    }

    /** Steps of one kind in a row, with the line that sums them up (OTHER: one step, its own words). */
    public static final class Group {
        public final Kind kind;
        public final String label;
        public final List<Item> items;
        public final long secs;
        /** The last original step this group covers, so a compressed banner still spans every line it folded. */
        public final int lastIndex;

        Group(Kind kind, String label, List<Item> items, long secs, int lastIndex) {
            this.kind = kind;
            this.label = label;
            this.items = items;
            this.secs = secs;
            this.lastIndex = lastIndex;
        }
    }

    /** A group this long is one banner. A row and a code view per file, rebuilt on the main thread, froze the phone. */
    public static final int BADGE_WINDOW = 24;

    /**
     * The group being worked on opens itself only while it is short enough to draw. A survey of the whole tree stays
     * one banner until the user opens it.
     */
    public static boolean startOpen(int items, boolean running) {
        return running && items > 0 && items <= 8;
    }

    /** How many badges to build when the group is open. The rest stay behind "and N more". */
    public static int window(int items, int extra) {
        if (items <= 0) {
            return 0;
        }
        int want = BADGE_WINDOW + Math.max(0, extra);
        return Math.min(items, want);
    }

    private StepGroups() {
    }

    /** What kind of step: from its words and, for a command, its detail's title ("$ ls -la"). */
    public static Kind kind(String words, String detailTitle) {
        String w = words == null ? "" : words;
        if (detailTitle != null && detailTitle.startsWith("$ ") || w.startsWith("Not run: ")) {
            return Kind.COMMAND;
        }
        if (w.startsWith("Edited ") || w.startsWith("Created ") || w.startsWith("Rewrote ") || w.startsWith("Made ")) {
            return Kind.EDIT;
        }
        if (w.startsWith("Listed the files in ") || w.startsWith("Searched for ") || w.startsWith("Read ") && detailTitle != null
                || fileRead(w)) {
            return Kind.EXPLORE;
        }
        if (w.matches("(?s)^(Fetched|Opened|Read) https?://.*") || w.startsWith("Searched the web") || w.startsWith("Looked up ")) {
            return Kind.WEB; // pages from the web: one banner, not a line each ("Fetched https://...")
        }
        return Kind.OTHER;
    }

    /** A survey line "Read path (lines 1–400)" is a file, even with no detail under it. "Read bbc.com" is not. */
    static boolean fileRead(String w) {
        if (w == null || !w.startsWith("Read ")) {
            return false;
        }
        String path = pathOf(w.substring(5));
        if (path.isEmpty() || path.indexOf(' ') >= 0) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot == path.length() - 1) {
            return false;
        }
        String ext = path.substring(dot + 1).toLowerCase(java.util.Locale.US);
        return path.indexOf('/') >= 0 || ext.matches("java|kt|kts|gradle|xml|py|js|ts|tsx|jsx|go|rs|c|cc|cpp|h|hpp|swift|rb|php|cs|sh|md|json|yml|yaml|toml|html|css|sql|dart|vue|svelte");
    }

    /** The file in "path" or "path (lines 1–400)", without the ranges. */
    static String pathOf(String target) {
        if (target == null) {
            return "";
        }
        return target.replaceFirst(" \\(lines \\d+.*$", "").replaceFirst(" — still to read$", "");
    }

    /**
     * Folds the steps. {@code words}, {@code titles} (null where a step has no detail) and {@code secs} (-1 when
     * unknown) are side by side.
     */
    public static List<Group> fold(List<String> words, List<String> titles, List<Long> secs) {
        List<Group> out = new ArrayList<Group>();
        List<Item> run = new ArrayList<Item>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            if (repeated(words, titles, i, seen)) {
                continue; // said again, or said once more with its result: one line, the fuller one
            }
            String t = i < titles.size() ? titles.get(i) : null;
            long s = i < secs.size() ? secs.get(i) : -1;
            Kind k = kind(w, t);
            if (!run.isEmpty() && (k != run.get(0).kind || k == Kind.OTHER)) {
                out.add(group(run));
                run = new ArrayList<Item>();
            }
            run.add(item(k, w, t, i, s));
        }
        if (!run.isEmpty()) {
            out.add(group(run));
        }
        return out;
    }

    /**
     * A step that adds nothing: the same words as an earlier step with no detail of its own ("Replying" twice), or a
     * line the next step repeats with its result ("Fetched X", then "Fetched X: the site said 403"; "Listed the chosen
     * repositories", then "Listed the chosen repositories: ...").
     */
    static boolean repeated(List<String> words, List<String> titles, int i, java.util.Set<String> seen) {
        String w = words.get(i) == null ? "" : words.get(i).trim();
        boolean detailed = i < titles.size() && titles.get(i) != null;
        if (i + 1 < words.size() && words.get(i + 1) != null && words.get(i + 1).startsWith(w + ":") && !w.isEmpty()) {
            return true;
        }
        if (!detailed && !seen.add(w)) {
            return true;
        }
        return false;
    }

    static Item item(Kind k, String w, String title, int index, long secs) {
        switch (k) {
            case WEB: {
                int sp = w.indexOf(' ');
                if (w.startsWith("Searched the web")) {
                    return new Item(k, "Searched the web", w.replaceFirst("^Searched the web( for)?\\s*", ""), index, secs);
                }
                return new Item(k, sp > 0 ? w.substring(0, sp) + " page" : "Page", sp > 0 ? w.substring(sp + 1) : w, index, secs);
            }
            case COMMAND:
                if (w.startsWith("Not run: ")) {
                    return new Item(k, "Not run", w.substring(9), index, secs);
                }
                return new Item(k, "Ran command", w, index, secs);
            case EDIT: {
                String verb = w.substring(0, w.indexOf(' '));
                String what = w.substring(verb.length() + 1).replaceFirst("\\s*\\(\\d+ lines?\\)$", "").replaceFirst("\\s*\\(.*\\)$", "");
                return new Item(k, verb + " file", what, index, secs);
            }
            case EXPLORE:
                if (w.startsWith("Listed the files in ")) {
                    return new Item(k, "Listed files", w.substring(20), index, secs);
                }
                if (w.startsWith("Searched for ")) {
                    return new Item(k, "Searched files", w.substring(13), index, secs);
                }
                return new Item(k, "Read file", w.substring(5), index, secs);
            default:
                return new Item(k, "", w, index, secs);
        }
    }

    static Group group(List<Item> run) {
        Kind k = run.get(0).kind;
        long secs = 0;
        boolean timed = false;
        for (Item i : run) {
            if (i.secs >= 0) {
                secs += i.secs;
                timed = true;
            }
        }
        String label;
        switch (k) {
            case COMMAND:
                label = "Ran " + run.size() + (run.size() == 1 ? " command" : " commands");
                break;
            case EDIT: {
                Set<String> files = new LinkedHashSet<String>();
                for (Item i : run) {
                    files.add(i.target);
                }
                label = "Edited " + files.size() + (files.size() == 1 ? " file" : " files");
                break;
            }
            case WEB: {
                Set<String> pages = new LinkedHashSet<String>();
                int searches = 0;
                for (Item i : run) {
                    if (i.verb.startsWith("Searched")) {
                        searches++;
                    } else {
                        pages.add(i.target.replaceFirst(": .*$", "")); // "https://x: the site said 403" is the page https://x
                    }
                }
                StringBuilder b = new StringBuilder("Fetched");
                if (!pages.isEmpty()) {
                    b.append(' ').append(pages.size()).append(pages.size() == 1 ? " page" : " pages");
                }
                if (searches > 0) {
                    b.append(pages.isEmpty() ? " " : ", ").append(searches).append(searches == 1 ? " search" : " searches");
                }
                label = pages.isEmpty() ? "Searched the web" + (searches > 1 ? " " + searches + " times" : "") : b.toString();
                break;
            }
            case EXPLORE: {
                Set<String> files = new LinkedHashSet<String>();
                int searches = 0;
                for (Item i : run) {
                    if (i.verb.startsWith("Searched")) {
                        searches++;
                    } else {
                        files.add(pathOf(i.target));
                    }
                }
                StringBuilder b = new StringBuilder("Explored");
                if (!files.isEmpty()) {
                    b.append(' ').append(files.size()).append(files.size() == 1 ? " file" : " files");
                }
                if (searches > 0) {
                    b.append(files.isEmpty() ? " " : ", ").append(searches).append(searches == 1 ? " search" : " searches");
                }
                label = b.toString();
                break;
            }
            default:
                label = run.get(0).target;
        }
        int last = run.get(0).index;
        for (Item i : run) {
            if (i.index > last) {
                last = i.index;
            }
        }
        return new Group(k, label, compress(run), timed ? secs : -1, last);
    }

    /**
     * Several reads of one file (its 400-line slices, in whatever order they finished) are one badge. The ranges
     * are shown from the top of the file, and a gap stays visible.
     */
    static List<Item> compress(List<Item> run) {
        if (run.isEmpty() || run.get(0).kind != Kind.EXPLORE) {
            return run;
        }
        List<Item> out = new ArrayList<Item>();
        java.util.Map<String, Integer> at = new java.util.LinkedHashMap<String, Integer>();
        for (Item i : run) {
            if (!"Read file".equals(i.verb)) {
                out.add(i);
                continue;
            }
            String path = pathOf(i.target);
            Integer prev = at.get(path);
            if (prev == null) {
                at.put(path, out.size());
                out.add(new Item(i.kind, i.verb, shown(path, ranges(i.target)), i.index, i.secs));
            } else {
                Item o = out.get(prev);
                java.util.List<int[]> both = ranges(o.target);
                both.addAll(ranges(i.target));
                long s = o.secs < 0 ? i.secs : i.secs < 0 ? o.secs : o.secs + i.secs;
                out.set(prev, new Item(o.kind, o.verb, shown(path, both), o.index, s));
            }
        }
        return out;
    }

    static java.util.List<int[]> ranges(String target) {
        java.util.List<int[]> out = new ArrayList<int[]>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)[–-](\\d+)").matcher(target == null ? "" : target);
        while (m.find()) {
            out.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        }
        return out;
    }

    /** Ranges from the top of the file; two that meet or overlap become one. */
    static String shown(String path, java.util.List<int[]> ranges) {
        if (ranges.isEmpty()) {
            return path;
        }
        java.util.Collections.sort(ranges, (a, b) -> a[0] - b[0]);
        java.util.List<int[]> merged = new ArrayList<int[]>();
        for (int[] r : ranges) {
            if (merged.isEmpty() || r[0] > merged.get(merged.size() - 1)[1] + 1) {
                merged.add(new int[] {r[0], r[1]});
            } else {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], r[1]);
            }
        }
        StringBuilder b = new StringBuilder(path).append(" (lines ");
        for (int i = 0; i < merged.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(merged.get(i)[0]).append('–').append(merged.get(i)[1]);
        }
        return b.append(')').toString();
    }
}
