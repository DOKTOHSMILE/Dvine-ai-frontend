package ai.dvine.ops.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Picks the task the user means and refuses to resume a destructive step whose approval expired. */
public final class TaskResumeManager {
    public static final class Choice {
        public final TaskRecord task;
        public final String clarify;
        public final boolean refused;

        private Choice(TaskRecord task, String clarify, boolean refused) {
            this.task = task;
            this.clarify = clarify;
            this.refused = refused;
        }

        public static Choice one(TaskRecord task) {
            return new Choice(task, null, false);
        }

        public static Choice ask(String clarify) {
            return new Choice(null, clarify, false);
        }

        public static Choice refuse(String why) {
            return new Choice(null, why, true);
        }
    }

    public Choice choose(String utterance, List<TaskRecord> paused) {
        List<TaskRecord> open = new ArrayList<TaskRecord>();
        if (paused != null) {
            for (TaskRecord t : paused) {
                if (t.state == TaskState.PAUSED || t.state == TaskState.WAITING_FOR_USER) {
                    open.add(t);
                }
            }
        }
        if (open.isEmpty()) {
            return Choice.ask("Nothing is paused.");
        }
        String u = utterance == null ? "" : utterance.toLowerCase(Locale.US);
        if (u.matches(".*(resume it|resume that|continue it|carry on)\\b.*") && !namesOne(u, open) && open.size() > 1) {
            return Choice.ask("Which task should I resume? " + list(open));
        }
        if (open.size() > 1 && !namesOne(u, open) && ambiguous(u)) {
            return Choice.ask("Which task should I resume? " + list(open));
        }
        TaskRecord hit = null;
        for (TaskRecord t : open) {
            if (names(u, t)) {
                if (hit != null) {
                    return Choice.ask("Which task should I resume? " + list(open));
                }
                hit = t;
            }
        }
        if (hit == null) {
            hit = open.size() == 1 ? open.get(0) : null;
        }
        if (hit == null) {
            return Choice.ask("Which task should I resume? " + list(open));
        }
        if (hit.destructive && !hit.destructiveAuthorized) {
            return Choice.refuse("That paused task would change something important, and its approval is no longer valid. I have not resumed it.");
        }
        return Choice.one(hit);
    }

    private static boolean ambiguous(String u) {
        return u.matches(".*(resume|continue|carry on|pick up).*") && !u.matches(".*(resume it|resume that).*");
    }

    private static boolean namesOne(String u, List<TaskRecord> open) {
        int n = 0;
        for (TaskRecord t : open) {
            if (names(u, t)) {
                n++;
            }
        }
        return n == 1;
    }

    private static boolean names(String u, TaskRecord t) {
        String blob = (t.description + " " + t.instruction + " " + t.kind).toLowerCase(Locale.US);
        for (String w : new String[]{"motorcycle", "server", "image", "folder", "model", "storage", "wheel", "log"}) {
            if (u.contains(w) && blob.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static String list(List<TaskRecord> open) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < open.size(); i++) {
            if (i > 0) {
                b.append(i == open.size() - 1 ? ", or " : ", ");
            }
            b.append(open.get(i).description.isEmpty() ? open.get(i).instruction : open.get(i).description);
        }
        return b.append('.').toString();
    }
}
