package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini agents for a heavy job, on one model: a planner splits the job into a few independent parts, a fresh helper
 * (its own short memory: only its part) works on each in turn, each helper's findings are handed back as they come,
 * and a writer puts them together. A phone holds one model at a time, so the helpers take turns on it rather than
 * running side by side; what they gain is focus: a small model does much better with one short, clear part than with
 * the whole job in one long memory. Pure Java.
 */
public final class Crew {
    /** The model, for the planner and the writer. */
    public interface Brain {
        String ask(String system, String prompt);
    }

    /** A helper: a fresh agent working on one part (with the brief: the whole job, for context); its findings. */
    public interface Helper {
        String work(String part, String brief);
    }

    /** A part finished: its number, how many there are, what it was, and what was found. */
    public interface Progress {
        void done(int number, int of, String part, String found);
    }

    private Crew() {
    }

    static final int MOST = 5;

    /** A job worth splitting: an audit, a deep review, research, an investigation, a whole repository. */
    public static boolean heavy(String task) {
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        return t.matches("(?s).*\\b(audit|deep (dive|review|look)|full review|review (the|my) (whole|entire|code|repo)|research|investigate"
                + "|go through (the|my|all)|find (all|every) (the )?(bugs?|issues?|problems?)|whole (repo|repository|codebase|project)"
                + "|entire (repo|repository|codebase|project)|codebase|security review)\\b.*");
    }

    static final String PLAN = "Split the job below into 2 to " + MOST + " independent parts, each small enough for one"
            + " helper with a short memory to finish alone (for a code audit: by folder or concern, such as security, data,"
            + " UI, build; for research: by question). One per line as PART: … with what to look at and what to report."
            + " No other text. /no_think";
    static final String WRITE = "You are writing the final answer from your helpers' findings, each on one part of the"
            + " job. Put them together into one complete answer for the user: the most important findings first, every"
            + " problem with where it is and how to fix it (code where it helps), and anything a helper couldn't finish."
            + " Don't drop findings or invent new ones. /no_think";

    /** Does the job with helpers one after another (one model on the phone); see the other {@code run}. */
    public static String run(Brain brain, Helper helper, String task, Progress progress, BooleanSupplier stopped) {
        return run(brain, helper, task, progress, stopped, 1);
    }

    /**
     * Does the job with helpers; null when it couldn't be split (one agent does it then) or it was stopped. With
     * {@code side} above 1 (an API model, which has no single phone to share), that many helpers work at once, each
     * reporting as it finishes; the answer keeps the parts in their order.
     */
    public static String run(Brain brain, Helper helper, String task, Progress progress, BooleanSupplier stopped, int side) {
        final List<String> parts = parts(brain.ask(PLAN, "The job: " + task));
        if (parts.size() < 2 || stopped.getAsBoolean()) {
            return null;
        }
        final String[] results = new String[parts.size()];
        if (side > 1) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(side, parts.size()));
            List<java.util.concurrent.Future<?>> jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < parts.size(); i++) {
                final int n = i;
                jobs.add(pool.submit(() -> {
                    if (!stopped.getAsBoolean()) {
                        results[n] = work(helper, parts, n, task);
                        progress.done(n + 1, parts.size(), parts.get(n), results[n]);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> j : jobs) {
                try {
                    j.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (java.util.concurrent.ExecutionException e) {
                    // that helper failed: its part says so below
                }
            }
            pool.shutdownNow();
        } else {
            for (int i = 0; i < parts.size() && !stopped.getAsBoolean(); i++) {
                results[i] = work(helper, parts, i, task);
                progress.done(i + 1, parts.size(), parts.get(i), results[i]);
            }
        }
        if (stopped.getAsBoolean()) {
            return null;
        }
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String r = results[i] == null ? "(nothing found: the helper couldn't finish this part)" : results[i];
            found.append("\n\nPart ").append(i + 1).append(": ").append(parts.get(i)).append("\n").append(r.length() > 6000 ? r.substring(0, 6000) : r);
        }
        if (stopped.getAsBoolean()) {
            return null;
        }
        String answer = brain.ask(WRITE, "The job: " + task + "\n\nThe helpers' findings:" + found);
        answer = answer == null ? "" : answer.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim();
        // the writer failed: the parts themselves are the answer
        return answer.isEmpty() ? found.toString().trim() : answer;
    }

    private static String work(Helper helper, List<String> parts, int i, String task) {
        String brief = "You are helper " + (i + 1) + " of " + parts.size() + " on this job: " + task
                + "\nDo only your part; the others are covered by other helpers.";
        String r;
        try {
            r = helper.work(parts.get(i), brief);
        } catch (RuntimeException e) {
            r = null;
        }
        return r == null || r.trim().isEmpty() ? "(nothing found: the helper couldn't finish this part)" : r.trim();
    }

    /** The parts: each from its PART: marker to the next, lines joined (a model may write "What to look at" below it). */
    static List<String> parts(String plan) {
        List<String> out = new ArrayList<String>();
        if (plan == null) {
            return out;
        }
        String t = plan.replaceAll("(?s)<think>.*?(</think>|$)", "");
        String[] blocks = t.split("(?im)^\\s*(?:[-*\\d.]+\\s*)?\\**PART\\s*\\d*\\**\\s*:");
        for (int i = 1; i < blocks.length && out.size() < MOST; i++) {
            String p = blocks[i].replaceAll("\\s+", " ").replace("**", "").trim();
            if (p.length() >= 3) {
                out.add(p);
            }
        }
        return out;
    }
}
