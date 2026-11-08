package ai.dvine.agent;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The one agent on a job: it looks at the work itself, asks the model for a plan, follows the plan one step at a
 * time in order, checks each result, and goes on until the job is done — a short memory, one small task each time,
 * the plan written down outside the model so nothing is lost between steps. There are no helpers and no writer: on
 * one model request at a time the helpers only wait in a queue, and the writer drops what the helpers found.
 * Pure Java.
 */
public final class Crew {
    /** The model: each ask is one step of the agent. */
    public interface Brain {
        String ask(String system, String prompt);
    }

    private Crew() {
    }

    /** A job worth going through in several steps: an audit, a deep review, research, an investigation. */
    public static boolean heavy(String task) {
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        return t.matches("(?s).*\\b(audit|deep (dive|review|look)|full review|review (the|my) (whole|entire|code|repo)|research|investigate"
                + "|go through (the|my|all)|find (all|every) (the )?(bugs?|issues?|problems?)|whole (repo|repository|codebase|project)"
                + "|entire (repo|repository|codebase|project)|codebase|security review)\\b.*");
    }

    /** The job's plan, kept on the side (the model is never trusted with its own list): numbered steps, done ✓. */
    static final String PLAN = "PLAN";

    /** The fewest parts a plan is ever broken into, so a small job still has a real plan. */
    static final int MOST = 5;

    /** One named part of a plan: the order of the work, never the finished work itself. */
    public static final class PlanStep {
        /** The part's name, as written in the plan (Step 1, Step 2, …). */
        public final String name;
        /** What the part says to do, in order. */
        public final String body;

        public PlanStep(String name, String body) {
            this.name = name;
            this.body = body;
        }
    }

    /** How many parts a plan may have for this many workers: at least MOST, never fewer than asked for. */
    public static int plan(int workers) {
        return Math.max(MOST, workers);
    }

    /** The plan the model is asked for on a job: the parts, named and in order, never the finished file. */
    static String planPrompt(String instruction) {
        return "You are planning a job on the phone. Write the plan only: " + plan(instruction) + " parts as"
                + " sections named \"### Step 1\", \"### Step 2\", …, in the order they have to happen, each part"
                + " short enough for one worker to do alone. The plan is the order of the work — never the work"
                + " itself, never a finished file, never code or tool names. No other text.";
    }

    static String planPrompt() {
        return planPrompt("");
    }

    /** The plan's parts, in the order they were planned: every section the model named, none reordered. */
    public static java.util.List<PlanStep> partsOf(String plan) {
        java.util.List<PlanStep> parts = new java.util.ArrayList<PlanStep>();
        if (plan == null || plan.trim().isEmpty()) {
            return parts;
        }
        PlanStep current = null;
        for (String line : plan.split("\n")) {
            String s = line.trim();
            if (s.startsWith("###")) {
                String title = s.replaceAll("^#+\\s*", "").trim();
                if (title.isEmpty()) {
                    continue;
                }
                current = new PlanStep(title, "");
                parts.add(current);
            } else if (current != null && !s.isEmpty()) {
                current = new PlanStep(current.name, current.body.isEmpty() ? s : current.body + "\n" + s);
                parts.set(parts.size() - 1, current);
            }
        }
        return parts;
    }

    /** The plan the agent asks for when the job is heavy: steps in the order they have to be done. */
    static String planPrompt() {
        return "You are an agent working on this job on the phone, one step at a time. Write a numbered plan first"
                + " (1. 2. 3.) of the steps it takes — each small enough for one short answer: what to look at, what"
                + " to do — in the order they have to happen, then the first step on its own after PLAN. No other text."
                + " /no_think";
    }

    /** The system prompt: a plan, one step, a result. Everything else is noise in a small memory. */
    static final String STEP = "You are an agent on a job, one step at a time. You are given the job, the plan so far"
            + " and the last result. Reply with only the next step as STEP: … (one line), or DONE when the job is"
            + " finished. No other text. /no_think";

    /**
     * Does the job as one agent: plans it, follows the plan step by step, checks each result against the job, and
     * stops at the step cap, when the model says DONE, or when stopped. Null when stopped; the result is what the
     * agent found, step by step.
     */
    public static String run(Brain brain, String task, Progress progress, BooleanSupplier stopped) {
        final String job = task == null ? "" : task.trim();
        if (brain == null || job.isEmpty() || stopped.getAsBoolean()) {
            return null;
        }
        String raw = brain.ask(planPrompt(), "The job: " + job);
        String plan = raw == null ? "" : raw.replaceAll("(?s).*?PLAN", "").replaceAll("</?final>", "").trim();
        if (plan.isEmpty()) {
            plan = raw == null || raw.trim().isEmpty() ? job : raw.trim();
        }
        if (progress != null) {
            progress.plan(plan);
        }
        // the steps, in order, the plan kept on the side; a step is one line the model can do alone
        java.util.List<String> steps = new java.util.ArrayList<String>();
        for (String line : plan.split("\n")) {
            String s = line.replaceAll("^\\s*\\d+[.)]\\s*", "").replaceAll("^[-*]\\s*", "").trim();
            if (s.length() >= 3) {
                steps.add(s);
            }
        }
        StringBuilder done = new StringBuilder();
        String result = "";
        for (int i = 0; i < steps.size(); i++) {
            if (stopped.getAsBoolean()) {
                return null;
            }
            String step = steps.get(i);
            result = brain.ask(STEP, "The job: " + job + "\n\nThe plan so far:\n" + plan
                    + "\n\nDone so far: " + (done.length() == 0 ? "(nothing yet)" : done.toString())
                    + "\n\nThe step to do now: " + step);
            if (result == null) {
                result = "";
            }
            result = result.replaceAll("(?s).*?STEP", "").replaceAll("</?final>", "").trim();
            if (progress != null) {
                progress.step(i + 1, steps.size(), step, result);
            }
            if (result.isEmpty() || result.startsWith("DONE") || result.equalsIgnoreCase("DONE")) {
                // the model stopped itself; the step it did not do is marked so nothing is missing from the answer
                done.append('\n').append("[").append(i + 1).append("/").append(steps.size()).append("] ")
                        .append(step).append(": ").append(result.isEmpty() ? "(nothing: the step could not be done)"
                                : "(the agent stopped here)");
                break;
            }
            done.append('\n').append("[").append(i + 1).append("/").append(steps.size()).append("] ")
                    .append(step).append(": ").append(result);
        }
        if (stopped.getAsBoolean()) {
            return null;
        }
        if (progress != null) {
            progress.finished(result);
        }
        return done.length() == 0 ? job : done.toString().trim();
    }

    /** A step finished: its number, how many there are, what it was, and what was found. */
    public interface Progress {
        void step(int number, int of, String step, String found);

        /** The job is done: the last result, or null when it never ran. */
        void finished(String result);

        /** The plan the agent wrote: the steps, in order, to check before the run starts. */
        void plan(String plan);
    }
}