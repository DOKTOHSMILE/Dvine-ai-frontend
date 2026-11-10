package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Several workers on one job at the same time. The lead (the model doing the task) decides the split and calls the
 * {@code workers} tool with one part for each worker; every part is a piece of real work (writing, editing, running,
 * testing), independent of the others, on files of its own. The workers run at once, each with its own steps, and their
 * reports come back to the lead, which fits the parts together and checks the whole. Nothing here reads the pilot's
 * words: the model reads "use more workers" and makes the call. Pure Java.
 */
public final class Squad {
    private Squad() {
    }

    /** The most workers one call starts. */
    public static final int MOST = 6;

    /** One worker's whole job: it does {@code part} and gives its report (what it did, which files changed). */
    public interface Worker {
        String work(int number, String part);
    }

    /** The tool, as the lead is told about it. */
    public static final String TOOL = "<tool name=\"workers\"><part>the first worker's whole job</part><part>the second"
            + " worker's whole job</part></tool> (2 to " + MOST + " workers do these parts AT THE SAME TIME, each with every tool you"
            + " have, then their reports come back to you. Use it when the user asks for more workers or to speed the work up,"
            + " or when the job splits into parts that do not touch the same files. Each part must stand on its own: say"
            + " exactly what to do, in which folder and files, and what done looks like; a worker knows only its part."
            + " Never give two workers the same file. Afterwards you check that the parts fit together and finish the rest.)\n";

    private static final Pattern PART = Pattern.compile("(?s)<part>\\s*(.*?)\\s*</part>");

    /** The parts in a {@code workers} call's body, at most {@link #MOST}; a body with no part tags is read a line a part. */
    public static List<String> parts(String body) {
        List<String> out = new ArrayList<String>();
        Matcher m = PART.matcher(body == null ? "" : body);
        while (m.find() && out.size() < MOST) {
            if (!m.group(1).isEmpty()) {
                out.add(m.group(1));
            }
        }
        if (out.isEmpty()) {
            for (String line : (body == null ? "" : body).split("\n")) {
                String l = line.replaceFirst("^\\s*(?:[-*\u2022]|\\d+[.)])\\s*", "").trim();
                if (!l.isEmpty() && out.size() < MOST) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /** What one worker is asked: its part, and that it is one of several. */
    public static String brief(int number, int of, String part, String whole) {
        return "You are worker " + number + " of " + of + " on one job, all working at the same time. Do only your part,"
                + " completely, and touch only the files your part names; the other workers have the rest."
                + (whole == null || whole.trim().isEmpty() ? "" : "\n\nThe whole job, for context only: " + clip(whole.trim(), 1200))
                + "\n\nYour part: " + part.trim()
                + "\n\nWhen it is done, your final answer is a short report: what you did, each file you changed, what you"
                + " checked, and anything left open.";
    }

    /**
     * Runs the parts, {@code atOnce} at a time (an API answers several workers together; the device's own model one at
     * a time, so there the parts follow one another). The reports, in the parts' order, as the tool's result.
     */
    public static String run(List<String> parts, Worker worker, int atOnce, BooleanSupplier stopped) {
        if (parts == null || parts.size() < 2) {
            return "Error: workers needs at least two <part> blocks, one for each worker. Do a single part yourself.";
        }
        int n = Math.max(1, Math.min(atOnce, parts.size()));
        ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "dvine-worker");
            t.setDaemon(true);
            return t;
        });
        List<Future<String>> jobs = new ArrayList<Future<String>>();
        for (int i = 0; i < parts.size(); i++) {
            final int number = i + 1;
            final String part = parts.get(i);
            jobs.add(pool.submit(() -> stopped != null && stopped.getAsBoolean() ? "Stopped before it started." : worker.work(number, part)));
        }
        pool.shutdown();
        StringBuilder out = new StringBuilder(parts.size() + " workers finished (" + n + " at a time). Their reports:\n");
        for (int i = 0; i < jobs.size(); i++) {
            String said;
            try {
                said = jobs.get(i).get(60, TimeUnit.MINUTES);
            } catch (Exception failed) {
                said = "Error: this worker did not finish (" + failed.getClass().getSimpleName() + "). Its part is still to do.";
            }
            out.append("\n## Worker ").append(i + 1).append(": ").append(clip(parts.get(i), 160)).append('\n')
                    .append(said == null || said.trim().isEmpty() ? "Error: no report. Its part is still to do." : clip(said.trim(), 4000)).append('\n');
        }
        pool.shutdownNow();
        out.append("\nNow check that the parts fit together (read what changed, run the checks), do whatever a worker left open,"
                + " and carry on with the job.");
        return out.toString();
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "\u2026";
    }
}
