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
        COMMAND, EXPLORE, EDIT, OTHER
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

        Group(Kind kind, String label, List<Item> items, long secs) {
            this.kind = kind;
            this.label = label;
            this.items = items;
            this.secs = secs;
        }
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
        if (w.startsWith("Listed the files in ") || w.startsWith("Searched for ") || w.startsWith("Read ") && detailTitle != null) {
            return Kind.EXPLORE;
        }
        return Kind.OTHER;
    }

    /**
     * Folds the steps. {@code words}, {@code titles} (null where a step has no detail) and {@code secs} (-1 when
     * unknown) are side by side.
     */
    public static List<Group> fold(List<String> words, List<String> titles, List<Long> secs) {
        List<Group> out = new ArrayList<Group>();
        List<Item> run = new ArrayList<Item>();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
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

    static Item item(Kind k, String w, String title, int index, long secs) {
        switch (k) {
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
            case EXPLORE: {
                Set<String> files = new LinkedHashSet<String>();
                int searches = 0;
                for (Item i : run) {
                    if (i.verb.startsWith("Searched")) {
                        searches++;
                    } else {
                        files.add(i.target);
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
        return new Group(k, label, run, timed ? secs : -1);
    }
}
