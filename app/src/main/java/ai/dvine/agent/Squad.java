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
    public static final String TOOL = "<tool name=\"workers\"><part role=\"UCIX\" minutes=\"10\">the first agent's whole job</part>"
            + "<part role=\"CORX\" minutes=\"15\">the second agent's whole job</part></tool> (2 to " + MOST + " agents do these parts AT"
            + " THE SAME TIME, each with every tool you have, then their reports come back to you. role is the agent whose work"
            + " the part is; minutes is the time ROLX allows it (left out: " + Squad.MINUTES + "). Use it when the user asks for more"
            + " workers or to speed the work up, or when the job splits into parts that do not touch the same files. Each part"
            + " must stand on its own: say exactly what to do, in which folder and files, and what done looks like; an agent"
            + " knows only its part. Never give two agents the same file. Afterwards you check that the parts fit together,"
            + " send back any that failed or ran out of time, and finish the rest.)\n";

    /** The same tool for a specialist: its own mini workers, the hands that carry out the pieces it designs. */
    public static final String HANDS = "<tool name=\"workers\"><part>one piece, fully specified</part><part>another piece</part></tool>"
            + " (your hands: 2 to " + Squad.MOST_HANDS + " mini workers carry out pieces of YOUR part at the same time. Design the pieces"
            + " yourself first, exactly, each on its own files; then check what they hand back.)\n";

    /** The minutes a part gets when the lead names none, and the most it can be given. */
    public static final int MINUTES = 15, MOST_MINUTES = 120;
    /** The most mini workers one specialist starts. */
    public static final int MOST_HANDS = 4;

    /** One part of a job: whose work it is, how long it may take, and what to do. */
    public static final class Part {
        public final Org role;
        public final int minutes;
        public final String text;

        Part(Org role, int minutes, String text) {
            this.role = role;
            this.minutes = minutes;
            this.text = text;
        }
    }

    private static final Pattern ASSIGNED = Pattern.compile("(?s)<part\\b([^>]*)>\\s*(.*?)\\s*</part>");

    /** The parts of a call with the agent and the time each was given; a body with no part tags is read a line a part. */
    public static List<Part> assignments(String body) {
        List<Part> out = new ArrayList<Part>();
        Matcher m = ASSIGNED.matcher(body == null ? "" : body);
        while (m.find() && out.size() < MOST) {
            if (m.group(2).isEmpty()) {
                continue;
            }
            Matcher role = Pattern.compile("(?i)\\b(?:role|agent|owner)\\s*=\\s*[\"']?([A-Za-z]+)").matcher(m.group(1));
            Matcher mins = Pattern.compile("(?i)\\b(?:minutes|time|mins?)\\s*=\\s*[\"']?(\\d{1,3})").matcher(m.group(1));
            Org o = role.find() ? Org.of(role.group(1)) : null;
            int t = mins.find() ? Integer.parseInt(mins.group(1)) : MINUTES;
            out.add(new Part(o != null && o.worker() ? o : null, Math.max(1, Math.min(MOST_MINUTES, t)), m.group(2)));
        }
        if (out.isEmpty()) {
            for (String p : parts(body)) {
                out.add(new Part(null, MINUTES, p));
            }
        }
        return out;
    }

    private static final Pattern PART = Pattern.compile("(?s)<part\\b[^>]*>\\s*(.*?)\\s*</part>");

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
     * The same hands for any feature (writing sections, reading pages, code parts): every part done by its own worker,
     * {@code atOnce} at a time, and what each gave back, whole, in the parts' order. A worker that failed or did not
     * finish gives "Error: …".
     */
    public static List<String> all(List<String> parts, Worker worker, int atOnce, BooleanSupplier stopped) {
        List<String> out = new ArrayList<String>();
        if (parts == null || parts.isEmpty()) {
            return out;
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
        for (Future<String> j : jobs) {
            try {
                out.add(j.get(60, TimeUnit.MINUTES));
            } catch (Exception failed) {
                out.add("Error: this worker did not finish (" + failed.getClass().getSimpleName() + "). Its part is still to do.");
            }
        }
        pool.shutdownNow();
        return out;
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
        List<String> said = all(parts, worker, atOnce, stopped);
        StringBuilder out = new StringBuilder(parts.size() + " workers finished (" + n + " at a time). Their reports:\n");
        for (int i = 0; i < said.size(); i++) {
            String one = said.get(i);
            out.append("\n## Worker ").append(i + 1).append(": ").append(clip(parts.get(i), 160)).append('\n')
                    .append(one == null || one.trim().isEmpty() ? "Error: no report. Its part is still to do." : clip(one.trim(), 4000)).append('\n');
        }
        String all = out.toString();
        boolean recheck = all.contains("\nError:") || all.contains("Time is up") || all.contains("did not finish");
        out.append(recheck ? "\nCPGC: at least one part failed or ran out of time. Read its report, then send that part back"
                + " (a new workers call with what is left, more exactly specified) or fix it yourself before anything is"
                + " reported done. " : "\n");
        out.append("Now check that the parts fit together (read what changed, run the checks), do whatever a worker left open,"
                + " and carry on with the job.");
        return out.toString();
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "\u2026";
    }
}
