package ai.dvine.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How DVINE works on a real task, step by step, and holds itself to it:
 *
 * <pre>
 * 1 UNDERSTAND  read the code that is already there (the parts the task touches, and code that does something
 *               similar) before deciding anything. Nothing is changed.
 * 2 PLAN        write the blueprint in PLAN.md: the goal, what was found, what is reused, the numbered steps with
 *               the files each one touches, the risks, and how it will be checked.
 * 3 CHECK       scrutinise the blueprint for gaps: every step names its files, nothing new duplicates code already
 *               there, risks have an answer, there is a test, no open questions are left. {@link #check} lists
 *               what is missing; building cannot start until it is empty.
 * 4 BUILD       build the steps in order, fast, ticking each in PLAN.md. No going round in circles: the same
 *               call repeated, or the same failure again, is stopped and sent back to the blueprint.
 * 5 VERIFY      run the check the blueprint names; fix what fails; then the report.
 *
 * A change from the user while working: added to the blueprint (Changes and new steps), the blueprint is checked
 * again, then building goes on from the first unticked step. Ticked steps are not redone.
 * </pre>
 *
 * The phase is the line {@code Phase: …} in PLAN.md, so a task carried on later picks up where it was. Pure Java.
 */
public final class Blueprint {
    public enum Phase { UNDERSTAND, PLAN, CHECK, BUILD, VERIFY, DONE }

    /** What the agent is told, ahead of its tools, for a real task. */
    public static final String RULES = "Blueprint: work in this order and do not skip ahead.\n"
            + "1 UNDERSTAND: read before deciding. List and read the files the task touches, and search for code that"
            + " already does something similar (search the names, the words, the screens). Change nothing yet.\n"
            + "2 PLAN: write PLAN.md in the project folder with these sections: '# Blueprint: <the task>', 'Phase: PLAN',"
            + " '## Goal', '## Found' (what is there now, file:name), '## Reuse' (existing code you will use instead of"
            + " writing it again, file:name, or 'none: <why>'), '## Steps' (numbered '- [ ] 1. … (files: a, b)'), '## Risks'"
            + " (each with its answer after ' -> '), '## Check' (the build or test that proves it works), '## Changes'"
            + " (what the user adds later).\n"
            + "3 CHECK: set 'Phase: CHECK' and scrutinise the plan for loopholes: a step without files, new code that"
            + " duplicates something in Found, a risk without an answer, no test, an open question. Fix the plan. When"
            + " nothing is missing, set 'Phase: BUILD'. Files other than PLAN.md cannot be changed before that.\n"
            + "4 BUILD: do the steps in order, quickly, ticking '- [x]' in PLAN.md as each is done. Do not re-read what"
            + " you already read or redo a ticked step. If something fails twice, stop and fix the plan instead of trying"
            + " the same thing again.\n"
            + "5 VERIFY: set 'Phase: VERIFY', run the Check, fix what fails, then give the report.\n"
            + "If the user adds something while you work: add it under '## Changes' and as new steps, set 'Phase: CHECK',"
            + " check the whole blueprint again, then carry on building from the first unticked step.\n"
            + "The plan is a list that grows. A new task or a new idea is ADDED to the PLAN.md that is there: never delete"
            + " or rewrite the steps already in it, done or not, and never start a fresh plan over an old one. New steps go"
            + " after the existing ones; only when the user says the new work is a priority do they go right after the last"
            + " ticked step, to be done next. The only edit to an existing step is ticking it when it is finished and"
            + " checked; then move to the next one. A write that drops a step is refused.\n";

    /** Told to the agent with the user's addition, instead of the plain "read it and continue". */
    public static final String CHANGE = "The user added this while you were working. Do not start over and do not redo"
            + " ticked steps. Add it to PLAN.md under '## Changes' and as new numbered steps (after the steps already there;"
            + " right after the last ticked one only if the user calls it a priority; never remove or reword an existing"
            + " step), set 'Phase: CHECK', check the"
            + " whole blueprint again for gaps (does it clash with a step already built? does it reuse what is there?), set"
            + " 'Phase: BUILD', then carry on from the first unticked step.";

    private static final Pattern PHASE = Pattern.compile("(?im)^\\s*Phase:\\s*(UNDERSTAND|PLAN|CHECK|BUILD|VERIFY|DONE)\\b");
    private static final Pattern STEP = Pattern.compile("(?m)^\\s*[-*]\\s*\\[([ xX])\\]\\s*(.+)$");

    private String plan = "";
    private String planPath;
    private final Map<String, Integer> calls = new HashMap<String, Integer>();
    private final Map<String, Integer> failures = new HashMap<String, Integer>();
    private Phase announced;
    private boolean changesPending;

    /** True for a task that builds or changes something: those follow the blueprint. A quick question does not. */
    public static boolean applies(String task) {
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        if (t.trim().matches("(?s)^(please\\s+)?(clone|pull|fetch|download|install|update local|sync)\\b.{0,200}")) {
            return false; // a repository to fetch or packages to install: one command, nothing to plan
        }
        if (t.trim().length() > 240) {
            return true;
        }
        if (t.trim().split("\\s+").length < 6) {
            return false; // "make a hello script": a quick job, done directly
        }
        return t.matches("(?s).*\\b(build|make|create|add|implement|develop|design|fix|refactor|upgrade|rewrite|wire|"
                + "integrate|migrate|port|feature|redesign|restructure|set up|setup|convert|replace|remove|delete the|"
                + "update the|improve|optimi[sz]e)\\b.*") && !t.matches("(?s)^\\s*(git |run |ls |cat |what |who |why |how much |is |are |does |do )\\b.*");
    }

    /** The phase the blueprint is in: UNDERSTAND until PLAN.md says otherwise. */
    public Phase phase() {
        return phaseOf(plan);
    }

    static Phase phaseOf(String planText) {
        Matcher m = PHASE.matcher(planText == null ? "" : planText);
        Phase last = null;
        while (m.find()) {
            last = Phase.valueOf(m.group(1).toUpperCase(Locale.US));
        }
        if (last != null) {
            return last;
        }
        return planText == null || planText.trim().isEmpty() ? Phase.UNDERSTAND : Phase.PLAN;
    }

    /** A blueprint carried on from an earlier run (PLAN.md as it is on disk). */
    public void resume(String path, String text) {
        planPath = path;
        plan = text == null ? "" : text;
    }

    public static boolean isPlanFile(String path) {
        return path != null && path.replace('\\', '/').toLowerCase(Locale.US).matches("(.*/)?plan\\.md");
    }

    public String planPath() {
        return planPath;
    }

    /**
     * Whether a tool call may run now. Null: go ahead. Otherwise the reason it may not, given back to the agent as the
     * tool's result: nothing but PLAN.md is changed before the blueprint has passed its check.
     */
    public String gate(String tool, String path) {
        boolean changes = tool.equals("write_file") || tool.equals("edit_file") || tool.equals("make_file") || tool.equals("zip")
                || tool.equals("unzip") || tool.equals("git");
        if (!changes || isPlanFile(path)) {
            return null;
        }
        Phase p = phase();
        if (p == Phase.UNDERSTAND || p == Phase.PLAN || p == Phase.CHECK) {
            List<String> missing = check(plan);
            return "Blueprint: not built yet. The phase is " + p.name() + "; files other than PLAN.md change only after"
                    + " the blueprint passes its check and says 'Phase: BUILD'."
                    + (missing.isEmpty() ? " Nothing is missing: set 'Phase: BUILD' in PLAN.md, then make this change."
                            : " Still missing: " + String.join("; ", missing) + ".");
        }
        return null;
    }

    private final List<String> justTicked = new ArrayList<String>();

    /** The steps ticked by the last write of PLAN.md (taken once), for the Scribe's record. */
    public List<String> takeTicked() {
        List<String> out = new ArrayList<String>(justTicked);
        justTicked.clear();
        return out;
    }

    /** PLAN.md was written or edited: its new text. What the agent is told back (the check, or the next phase). */
    public String planned(String path, String text) {
        planPath = path;
        String before = plan;
        java.util.Set<String> was = new java.util.HashSet<String>();
        for (String[] st : steps(before)) {
            if ("x".equals(st[0])) {
                was.add(st[1]);
            }
        }
        for (String[] st : steps(text == null ? "" : text)) {
            if ("x".equals(st[0]) && !was.contains(st[1])) {
                justTicked.add(st[1]);
            }
        }
        plan = text == null ? "" : text;
        if (phaseOf(before) != Phase.BUILD && phase() == Phase.BUILD) {
            List<String> missing = check(plan);
            if (!missing.isEmpty()) {
                // it said BUILD with gaps left: not yet
                plan = plan.replaceAll("(?im)^(\\s*Phase:\\s*)BUILD\\b", "$1CHECK");
                return "Blueprint check failed, so the phase stays CHECK: " + String.join("; ", missing)
                        + ". Fix these in PLAN.md (and set 'Phase: BUILD' again) before building.";
            }
            changesPending = false;
            return "Blueprint checked: nothing missing. Build the steps in order now; tick each one in PLAN.md.";
        }
        if (phase() == Phase.CHECK) {
            List<String> missing = check(plan);
            return missing.isEmpty() ? "Blueprint check: nothing missing. Set 'Phase: BUILD' and start building."
                    : "Blueprint check, still missing: " + String.join("; ", missing) + ".";
        }
        return null;
    }

    /**
     * The scrutiny: what the blueprint still lacks before building may start. Empty when it is complete.
     */
    public static List<String> check(String planText) {
        String p = planText == null ? "" : planText;
        List<String> out = new ArrayList<String>();
        if (p.trim().isEmpty()) {
            out.add("PLAN.md is not written yet (understand the code, then write the blueprint)");
            return out;
        }
        String goal = section(p, "Goal");
        if (goal.trim().length() < 10) {
            out.add("a '## Goal' that says what done looks like");
        }
        if (section(p, "Found").trim().length() < 10) {
            out.add("a '## Found' section: what is in the code now (read it first)");
        }
        String reuse = section(p, "Reuse");
        if (reuse.trim().isEmpty()) {
            out.add("a '## Reuse' section: the existing code used instead of writing it again, or 'none: <why>'");
        }
        List<String[]> steps = steps(p);
        if (steps.isEmpty()) {
            out.add("numbered steps under '## Steps' ('- [ ] 1. … (files: …)')");
        }
        for (String[] s : steps) {
            if (!s[1].toLowerCase(Locale.US).matches("(?s).*\\(files?:.*\\).*") && !s[1].toLowerCase(Locale.US).contains("files:")) {
                out.add("step '" + clip(s[1], 50) + "' names no files");
                break;
            }
        }
        String risks = section(p, "Risks");
        if (risks.trim().isEmpty()) {
            out.add("a '## Risks' section (what could break, and the answer to each)");
        } else {
            for (String line : risks.split("\n")) {
                String l = line.trim();
                if (l.matches("^[-*].+") && !l.contains("->") && !l.contains("→") && !l.toLowerCase(Locale.US).matches(".*\\bnone\\b.*")) {
                    out.add("the risk '" + clip(l.replaceFirst("^[-*]\\s*", ""), 50) + "' has no answer (add ' -> what prevents it')");
                    break;
                }
            }
        }
        if (section(p, "Check").trim().length() < 5) {
            out.add("a '## Check': the build or test that proves it works");
        }
        if (p.matches("(?s).*\\b(TODO|TBD)\\b.*") || p.matches("(?s).*\\?\\?.*")) {
            out.add("open questions (TODO, TBD or ??) answered");
        }
        // new code that duplicates something found: the same file named under Found and as a new file in a step
        for (String[] s : steps) {
            Matcher m = Pattern.compile("(?i)\\b(new|create)\\s+([\\w./-]+\\.\\w+)").matcher(s[1]);
            while (m.find()) {
                String name = m.group(2).replaceAll(".*/", "");
                if (section(p, "Found").contains(name.replaceAll("\\.\\w+$", ""))) {
                    out.add("step '" + clip(s[1], 40) + "' creates " + name + " though Found lists it: reuse or change it instead");
                }
            }
        }
        return out;
    }

    /** The steps: {done ("x" or " "), text}. */
    public static List<String[]> steps(String planText) {
        List<String[]> out = new ArrayList<String[]>();
        Matcher m = STEP.matcher(section(planText == null ? "" : planText, "Steps"));
        while (m.find()) {
            out.add(new String[] { m.group(1).trim().isEmpty() ? " " : "x", m.group(2).trim() });
        }
        return out;
    }

    /** A step's own words, without its tick, its number or its spacing: the same step however it is renumbered or moved. */
    static String stepKey(String step) {
        return (step == null ? "" : step).replaceFirst("^\\s*\\d+[.)]\\s*", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
    }

    /**
     * A plan is added to, never replaced. What a new version of a plan file would lose from the one on disk: a step
     * that is gone, or a finished step that is no longer ticked. Steps may be renumbered and reordered (a priority
     * moves up to be the next one to do) and new ones added; a step leaves the list only by being done and ticked.
     * Null when the new version keeps everything.
     */
    public static String kept(String onDisk, String proposed) {
        java.util.LinkedHashMap<String, String> now = new java.util.LinkedHashMap<String, String>();
        Matcher n = STEP.matcher(proposed == null ? "" : proposed);
        while (n.find()) {
            String key = stepKey(n.group(2));
            String tick = n.group(1).trim().isEmpty() ? " " : "x";
            now.put(key, "x".equals(now.get(key)) ? "x" : tick);
        }
        List<String> gone = new ArrayList<String>(), unticked = new ArrayList<String>();
        Matcher o = STEP.matcher(onDisk == null ? "" : onDisk);
        while (o.find()) {
            String key = stepKey(o.group(2));
            if (key.isEmpty()) {
                continue;
            }
            boolean done = !o.group(1).trim().isEmpty();
            if (!now.containsKey(key)) {
                if (!gone.contains(o.group(2).trim())) {
                    gone.add(o.group(2).trim());
                }
            } else if (done && !"x".equals(now.get(key))) {
                unticked.add(o.group(2).trim());
            }
        }
        if (gone.isEmpty() && unticked.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("Not written: a plan is added to, never replaced.");
        if (!gone.isEmpty()) {
            b.append(" Your new version drops ").append(gone.size()).append(gone.size() == 1 ? " step" : " steps")
                    .append(" that are in the plan now: ").append(String.join(" | ", gone.size() > 8 ? gone.subList(0, 8) : gone))
                    .append(gone.size() > 8 ? " | …" : "").append('.');
        }
        if (!unticked.isEmpty()) {
            b.append(" It also unticks finished work: ").append(String.join(" | ", unticked.size() > 8 ? unticked.subList(0, 8) : unticked)).append('.');
        }
        b.append(" Keep every step that is there, word for word, with its tick; add the new work as new steps after them. If the"
                + " user made the new work a priority, put its steps right after the last ticked one so they are next. A step is"
                + " only ever changed by ticking it when it is done.");
        return b.toString();
    }

    /** The first step not yet ticked, or null. */
    public String nextStep() {
        for (String[] s : steps(plan)) {
            if (!"x".equals(s[0])) {
                return s[1];
            }
        }
        return null;
    }

    /** "3 of 7": steps done of all. */
    public String progress() {
        List<String[]> all = steps(plan);
        int done = 0;
        for (String[] s : all) {
            if ("x".equals(s[0])) {
                done++;
            }
        }
        return done + " of " + all.size();
    }

    static String section(String text, String name) {
        Matcher m = Pattern.compile("(?ims)^#{2,3}\\s*" + Pattern.quote(name) + "\\b[^\\n]*\\n(.*?)(?=^#{1,3}\\s|\\z)").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Going round in circles: the same call made a third time, or the same failure a second time. A warning to give
     * the agent with the result, or null.
     */
    public String circling(String tool, String args, String result) {
        String key = tool + "\u0000" + args;
        int n = calls.containsKey(key) ? calls.get(key) + 1 : 1;
        calls.put(key, n);
        boolean failed = result != null && result.matches("(?s).*(\\[exit [1-9]\\d*\\]|\\bError\\b|error:|FAILED|No such file|isn't in).*");
        int f = 0;
        if (failed) {
            String sig = tool + "\u0000" + firstError(result);
            f = failures.containsKey(sig) ? failures.get(sig) + 1 : 1;
            failures.put(sig, f);
        }
        if (n >= 3 && !tool.equals("run")) {
            return "Blueprint: this is the " + nth(n) + " time you made exactly this call. You already have its result above;"
                    + " use it and move to the next step" + (nextStep() == null ? "" : " (" + clip(nextStep(), 60) + ")") + ".";
        }
        if (f >= 2) {
            return "Blueprint: the same failure again. Do not try the same thing a third time: work out why from the"
                    + " message, change the plan in PLAN.md if the approach is wrong, then carry on.";
        }
        return null;
    }

    private static String firstError(String result) {
        for (String line : result.split("\n")) {
            if (line.matches("(?i).*(error|failed|no such|exit [1-9]|isn't in).*")) {
                return line.trim().replaceAll("\\d+", "#");
            }
        }
        return result.length() > 80 ? result.substring(0, 80) : result;
    }

    private static String nth(int n) {
        return n == 3 ? "third" : n + "th";
    }

    /** The user added something: the blueprint goes back to CHECK (on disk too, when the agent next writes it). */
    public void reopen() {
        changesPending = true;
        if (phase() == Phase.BUILD || phase() == Phase.VERIFY || phase() == Phase.DONE) {
            plan = plan.replaceAll("(?im)^(\\s*Phase:\\s*)(BUILD|VERIFY|DONE)\\b", "$1CHECK");
        }
    }

    /**
     * The agent says it is finished. A reason to go on (unticked steps, a change not yet planned), or null.
     */
    public String unfinished() {
        if (changesPending) {
            return "Blueprint: the user's addition is not in PLAN.md yet. Add it under '## Changes' and as steps, check the"
                    + " blueprint, build it, then report.";
        }
        if (phase() == Phase.UNDERSTAND || plan.trim().isEmpty()) {
            return null; // it answered without needing a plan
        }
        String next = nextStep();
        if (next != null) {
            return "Blueprint: " + progress() + " steps are done; '" + clip(next, 80) + "' is not ticked. Build the rest (or"
                    + " remove a step from PLAN.md with the reason), then report.";
        }
        return null;
    }

    /** A new phase reached since the last call: the line shown to the user, or null. */
    public String announce() {
        Phase p = phase();
        if (p == announced) {
            return null;
        }
        announced = p;
        switch (p) {
            case UNDERSTAND:
                return "Understanding the task: reading the code that is already there";
            case PLAN:
                return "Drawing the blueprint";
            case CHECK:
                return "Checking the blueprint for gaps";
            case BUILD:
                return "Blueprint complete: building " + progress().replaceFirst("^\\d+ of ", "") + " steps";
            case VERIFY:
                return "Verifying the build";
            default:
                return "Blueprint done";
        }
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
