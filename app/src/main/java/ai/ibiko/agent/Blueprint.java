package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How Ibiko works on a real task, step by step, and holds itself to it:
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

    /**
     * What the agent is told, ahead of its tools, for a real task. Plan as much as the task needs and no more, start
     * as soon as the goal and the steps are clear, keep the plan a living list, prove each step, report plainly.
     */
    public static final String RULES = "Blueprint: how to work on this task.\n"
            + "1 UNDERSTAND: read what the task touches before deciding: list and read those files, and search for code that"
            + " already does something similar. Change nothing yet. Read only what the task needs.\n"
            + "2 PLAN: write PLAN.md in the project folder: '# Blueprint: <the task>', '## Goal' (what done looks like) and"
            + " '## Steps', numbered in order ('- [ ] 1. … (files: a, b; check: how this step is proved done, such as the"
            + " test to run or the lines to read back)'), one change per step, small enough to check. Add only the sections"
            + " that help: '## Found' (what is there now), '## Reuse' (existing code to use instead of writing it again),"
            + " '## Risks' (each with its answer after ' -> '), '## Check' (the build or test that proves it all works),"
            + " '## Changes'. When the task is on a GitHub repository add '## Repository': 'owner/name -> repos/<name>', the"
            + " one folder it is cloned into; the first step is to pull it there (clone it the first time), and every read,"
            + " edit, command and push uses that same folder, never projects/<name> or memory/projects/<name>: those paths"
            + " do not exist. Building starts as soon as PLAN.md has a goal and steps.\n"
            + "3 BUILD: one step at a time, in order, quickly. After each step run its check (read the changed lines back,"
            + " run the test) and tick '- [x]' in PLAN.md only when the check passed; a step whose check fails is fixed"
            + " before the next one starts. Do not re-read what you already read or redo a ticked step.\n"
            + "4 ADAPT: the plan is a living list. When you learn that a step not yet done is wrong, unneeded or too big,"
            + " reword, split or drop it, and say why in one line under '## Changes'. A ticked step stays as it is, and the"
            + " plan is never replaced by a new one: new work is added as new steps (right after the last ticked one when the"
            + " user calls it a priority). If something fails twice the same way, change the plan instead of trying again.\n"
            + "5 VERIFY and report: run the final check, fix what fails, then report what was done, what was checked and what"
            + " is left, saying only what you verified.\n"
            + "If the user adds something while you work: put it under '## Changes' and as new steps in PLAN.md, then carry on"
            + " from the first unticked step.\n"
            + "Once a codebase map has been written for this task, the survey is finished: do not survey the repository again"
            + " and do not start workers to read it. Write PLAN.md (a goal and steps) and build.\n";

    /** Told to the agent with the user's addition, instead of the plain "read it and continue". */
    public static final String CHANGE = "The user added this while you were working. Do not start over and do not redo"
            + " ticked steps. Add it to PLAN.md under '## Changes' and as new numbered steps (after the steps already there;"
            + " right after the last ticked one only if the user calls it a priority), adjust any unfinished step it changes"
            + " (does it clash with a step already built? does it reuse what is there?), then carry on from the first unticked"
            + " step.";

    /** Told once the repository has been surveyed, so the next reply writes the plan and builds instead of reading again. */
    public static final String BUILD_NOW = "The repository is already surveyed. Do not read it again and do not start readers."
            + " Write PLAN.md now, exactly in this shape, then build step 1 (other files can be written as soon as this plan"
            + " is saved):\n"
            + "# Blueprint: <the task>\n## Goal\n<what done looks like>\n## Steps\n"
            + "- [ ] 1. <one change> (files: path; check: how you prove it)\n";

    /**
     * After the survey, more reading is the loop that never builds. Null when this call may run. Otherwise the words
     * to hand back: write the plan and build. A few reads are still allowed. Another audit, and any worker started
     * only to look, is not.
     */
    public static String afterSurvey(boolean surveyed, Phase phase, String tool, int looksSoFar) {
        if (!surveyed || tool == null) {
            return null;
        }
        if (phase == Phase.BUILD || phase == Phase.VERIFY || phase == Phase.DONE) {
            return null;
        }
        boolean workers = tool.equals("workers");
        boolean looking = workers || tool.equals("read_file") || tool.equals("list_files") || tool.equals("search")
                || tool.equals("fetch");
        if (!looking) {
            return null;
        }
        if (!workers && looksSoFar < 3) {
            return null;
        }
        return BUILD_NOW;
    }

    /** A resumed plan that already has a goal and steps is built, not surveyed again. False when it was not moved. */
    public boolean startBuilding() {
        if (!required(plan).isEmpty()) {
            return false;
        }
        Phase p = phase();
        if (p == Phase.BUILD || p == Phase.VERIFY || p == Phase.DONE) {
            return false;
        }
        build();
        return true;
    }

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
        if (pointed(task)) {
            return false; // a compiler error that names the file: one edit, not a survey of the repo
        }
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        String first = t.trim().split("\\n\\s*\\n", 2)[0]; // the request itself, before any notes added under it
        if (first.matches("(?s)^(please\\s+)?(clone|pull|fetch|download|install|update local|sync)\\b.{0,200}")) {
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

    /**
     * A compiler error that names the file and the line. That is one edit. A paste of the log is long, which used to
     * start the whole blueprint (read the repo, write PLAN.md, check it) before the import was added.
     */
    public static boolean pointed(String task) {
        if (task == null || task.isEmpty()) {
            return false;
        }
        String t = task.toLowerCase(Locale.US);
        boolean namesLine = t.matches("(?s).*\\.[a-z]{1,5}:\\d+.*");
        boolean compiler = t.contains("cannot be resolved") || t.contains("cannot find symbol")
                || t.contains("does not exist") || t.contains("error:");
        return namesLine && compiler;
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

    /** The plan as Ibiko holds it (its phase line as it now stands). */
    public String text() {
        return plan;
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
            if (changesPending) {
                return "Blueprint: first add the user's new request to PLAN.md (under '## Changes' and as steps), then build.";
            }
            List<String> missing = required(plan);
            if (missing.isEmpty()) {
                build(); // a goal and steps: building starts, no ceremony
                return null;
            }
            return "Blueprint: not started yet. Files other than PLAN.md change once PLAN.md has a goal and steps. Still"
                    + " missing: " + String.join("; ", missing) + ".";
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
        if (!plan.equals(before)) {
            changesPending = false; // the plan was written after the user's addition: it is in there now
        }
        List<String> missing = required(plan);
        if (!missing.isEmpty()) {
            if (phase() == Phase.BUILD) {
                plan = plan.replaceAll("(?im)^(\\s*Phase:\\s*)BUILD\\b", "$1CHECK"); // it said BUILD with no plan to build
            }
            return "Blueprint check failed: PLAN.md still needs " + String.join("; ", missing) + ". Building starts once it has them.";
        }
        if (phase() == Phase.UNDERSTAND || phase() == Phase.PLAN || phase() == Phase.CHECK) {
            build();
            String said = "Plan noted (" + steps(plan).size() + (steps(plan).size() == 1 ? " step" : " steps")
                    + "). Build them in order now; tick each one in PLAN.md when its check passes.";
            List<String> worth = check(plan);
            if (!adviceGiven && !worth.isEmpty()) {
                adviceGiven = true; // said once, never a wall to get past
                said += " Worth adding if it helps (not required): " + String.join("; ", worth) + ".";
            }
            return said;
        }
        return null;
    }

    private boolean adviceGiven;

    /** Building starts: the plan's phase line says BUILD (added when there is none). */
    private void build() {
        if (PHASE.matcher(plan).find()) {
            plan = plan.replaceAll("(?im)^(\\s*Phase:\\s*)(UNDERSTAND|PLAN|CHECK)\\b", "$1BUILD");
        } else {
            plan = plan + (plan.endsWith("\n") ? "" : "\n") + "Phase: BUILD\n";
        }
    }

    /** What building really needs: PLAN.md, a goal, and at least one step. Empty when it may start. */
    public static List<String> required(String planText) {
        String p = planText == null ? "" : planText;
        List<String> out = new ArrayList<String>();
        if (p.trim().isEmpty()) {
            out.add("PLAN.md is not written yet (understand the code, then write the blueprint)");
            return out;
        }
        if (section(p, "Goal").trim().length() < 10) {
            out.add("a '## Goal' that says what done looks like");
        }
        if (steps(p).isEmpty()) {
            out.add("numbered steps under '## Steps' ('- [ ] 1. … (files: …)')");
        }
        return out;
    }

    /**
     * The full scrutiny, given once as advice when the plan is written: what a thorough blueprint would also have.
     * Only {@link #required} holds building back.
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
        for (String[] s : steps) {
            if (!s[1].toLowerCase(Locale.US).matches("(?s).*\\bcheck(ed)?\\s*:.*")) {
                out.add("step '" + clip(s[1], 50) + "' says no 'check:' (how it is proved done before it is ticked)");
                break;
            }
        }
        // numbered 1, 2, 3… in order, none twice: a step out of order is a step done before what it needs
        int want = 1;
        for (String[] s : steps) {
            Matcher n = Pattern.compile("^(\\d+)\\.").matcher(s[1]);
            if (n.find()) {
                int got = Integer.parseInt(n.group(1));
                if (got != want) {
                    out.add("the steps are not numbered in order (step " + got + " where " + want + " was expected)");
                    break;
                }
                want++;
            }
        }
        // work on a GitHub repository: the one folder it lives in is written down
        if (p.matches("(?is).*\\b(git\\s+(clone|pull|push)|github|pull request)\\b.*") && section(p, "Repository").trim().isEmpty()) {
            out.add("a '## Repository' section: 'owner/name -> repos/<name>', the folder it is pulled into and worked in");
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
        String body = section(planText == null ? "" : planText, "Steps");
        Matcher m = STEP.matcher(body);
        while (m.find()) {
            out.add(new String[] { m.group(1).trim().isEmpty() ? " " : "x", m.group(2).trim() });
        }
        if (!out.isEmpty()) {
            return out;
        }
        // a numbered list is still a plan: building waits on a goal and steps, not on the checkbox characters
        Matcher n = Pattern.compile("(?m)^\\s*(?:[-*]\\s+)?(\\d+)[.)]\\s+(.+)$").matcher(body);
        while (n.find()) {
            out.add(new String[] { " ", n.group(1) + ". " + n.group(2).trim() });
        }
        return out;
    }

    /** A step's own words, without its tick, its number or its spacing: the same step however it is renumbered or moved. */
    static String stepKey(String step) {
        String key = (step == null ? "" : step).replaceFirst("^\\s*\\d+[.)]\\s*", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
        return RepoFolder.place(key);
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
        // a living list: an unfinished step may be reworded, split or dropped. What is protected is finished work, and
        // the plan as a whole (a new plan written over the old one)
        List<String> goneDone = new ArrayList<String>();
        int old = 0;
        Matcher o2 = STEP.matcher(onDisk == null ? "" : onDisk);
        while (o2.find()) {
            String key = stepKey(o2.group(2));
            if (key.isEmpty()) {
                continue;
            }
            old++;
            if (!o2.group(1).trim().isEmpty() && !now.containsKey(key)) {
                goneDone.add(o2.group(2).trim());
            }
        }
        boolean replaced = old > 0 && gone.size() == old && old > 1;
        if (goneDone.isEmpty() && unticked.isEmpty() && !replaced) {
            return null;
        }
        StringBuilder b = new StringBuilder("Not written: a plan is added to and adjusted, never replaced, and finished work stays.");
        if (replaced) {
            b.append(" Your new version drops every step in the plan now: ")
                    .append(String.join(" | ", gone.size() > 8 ? gone.subList(0, 8) : gone)).append(gone.size() > 8 ? " | …" : "")
                    .append('.');
        } else if (!goneDone.isEmpty()) {
            b.append(" Your new version drops finished ").append(goneDone.size() == 1 ? "step" : "steps").append(": ")
                    .append(String.join(" | ", goneDone.size() > 8 ? goneDone.subList(0, 8) : goneDone)).append('.');
        }
        if (!unticked.isEmpty()) {
            b.append(" It also unticks finished work: ").append(String.join(" | ", unticked.size() > 8 ? unticked.subList(0, 8) : unticked)).append('.');
        }
        b.append(" Keep the ticked steps as they are. An unfinished step may be reworded, split or dropped (say why under"
                + " '## Changes'); new work goes in as new steps.");
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
                return "Plan ready: building " + progress().replaceFirst("^\\d+ of ", "") + " steps";
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
