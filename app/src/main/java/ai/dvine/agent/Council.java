package ai.dvine.agent;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The three think tanks working on one question together (the engineering plan's bounded deliberation, extended to
 * Ibiko): Obi analyses its structure, Ivie reviews that for people and what is missing, Ibiko works out how to do it
 * in practice; one more round only when Ivie disagrees on something material; then one answer for the user, with a
 * short line saying what each checked. Simple questions don't come here ({@link #wanted}). Any model: the phone's or
 * an API. Pure Java.
 */
public final class Council {
    /** The model: instructions and a prompt in, its reply out (null when it failed or was stopped). */
    public interface Brain {
        String ask(String system, String prompt);
    }

    /** Each tank's turn as it finishes: a line for the steps, the tank, and what it said. */
    public interface Steps {
        void step(String line, String who, String said);
    }

    /** What came of it: the answer, each tank's view, and whether a second round was needed. */
    public static final class Result {
        public final String answer, obi, ivie, ibiko;
        public final boolean secondRound;

        Result(String answer, String obi, String ivie, String ibiko, boolean secondRound) {
            this.answer = answer;
            this.obi = obi;
            this.ivie = ivie;
            this.ibiko = ibiko;
            this.secondRound = secondRound;
        }
    }

    private Council() {
    }

    /** Said outright: "think together", "all three", "council", "think tank". */
    static final java.util.regex.Pattern ASKED = java.util.regex.Pattern.compile(
            "(?i)\\b(think (it )?(through )?together|work (it )?out together|all three( of you)?|the three of you|council|think ?tanks?)\\b");
    /** A question worth three views: deciding, planning, comparing, designing, judging. */
    static final java.util.regex.Pattern WEIGHTY = java.util.regex.Pattern.compile(
            "(?i)\\b(should i|should we|which is better|pros and cons|compare|decide|decision|choose|strategy|strategic|plan for|"
                    + "best way|advise|advice|recommend|evaluate|analy[sz]e|assess|design|trade-?offs?|risks? of|worth it|"
                    + "business|invest|career|launch)\\b");

    /**
     * Whether the three should work on this together: asked outright, or (when {@code auto}) a long or weighty
     * question. A greeting, a quick fact or a command goes the usual way.
     */
    public static boolean wanted(String question, boolean auto) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return false;
        }
        if (ASKED.matcher(q).find()) {
            return true;
        }
        if (!auto) {
            return false;
        }
        int words = q.split("\\s+").length;
        return WEIGHTY.matcher(q).find() && words >= 6;
    }

    static final String OBI = "You are Obi, the planner, in a council of three (Obi plans, Ivie checks it against the real"
            + " world and its UI/UX, Ibiko builds). Your part: the plan. In plain short lines: the goal, the plan as numbered"
            + " steps, the key assumptions and risks, and how sure you are (low, medium or high). Be concrete; no greeting;"
            + " under 180 words. Don't write the final answer: the council merges it. /no_think";
    static final String IVIE = "You are Ivie, in a council of three (Obi plans, you check it, Ibiko builds). Your part:"
            + " match Obi's plan against how the real world behaves: how people will actually use it or react, habits,"
            + " money, time and local conditions that get in the way, and, for anything people see or touch, the UI/UX (what"
            + " the screens or the experience should be like). Say what to change. Under 160 words. End with one line:"
            + " VERDICT: AGREE, or VERDICT: DISAGREE: and the one point that matters most. /no_think";
    static final String IBIKO = "You are Ibiko, the builder, in a council of three (Obi plans, Ivie checks it, you build)."
            + " Your part: how you will carry the plan out. The build steps in order (what you make first, the tools,"
            + " languages or services, how you will test it), what it costs in time or money, and anything that makes it"
            + " hard. Under 150 words; numbered steps. /no_think";
    static final String RECHECK = "You are Obi, the planner. Ivie checked your plan against the real world and disagrees on"
            + " the point below. In under 80 words: keep the plan or change it, and say why. /no_think";

    /** Something to build (an app, a site, a tool…): after the council, Ibiko is offered the plan to carry out. */
    public static boolean building(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.US);
        return q.matches("(?s).*\\b(build|create|make|develop|design|launch|set up|code|program|start)\\b.*\\b(app|apps|website|site|web app|tool"
                + "|system|bot|game|api|dashboard|store|shop|platform|feature|product|software|program|plugin|extension|landing page)\\b.*")
                || Handoff.forIbiko(question);
    }

    static String synth(String speaker) {
        String who = speaker == null || speaker.isEmpty() ? "Obi" : speaker.substring(0, 1).toUpperCase(Locale.US) + speaker.substring(1);
        return "You are " + who + ", answering for a council of three (Obi, Ivie, Ibiko) that worked on the user's"
                + " question together (Obi made the plan, Ivie checked it against the real world and its UI/UX, Ibiko"
                + " worked out how to build it). Write ONE answer for the user from their notes: the plan first, as Ivie"
                + " adjusted it, then why, the risks to watch, and the build or action steps. Where they disagreed, say so plainly and"
                + " which way the evidence points; never invent agreement. Speak as yourself, warmly and clearly; no"
                + " headings about the council. End with one short line naming what each of the three looked at."
                + " /no_think";
    }

    /** Works on the question; null when it was stopped or the model gave nothing to merge. */
    public static Result run(Brain brain, String question, String context, String speaker, Steps steps, BooleanSupplier stopped) {
        String base = (context == null || context.trim().isEmpty() ? "" : "What we know (the chat so far):\n" + context.trim() + "\n\n")
                + "The user asks: " + question.trim();
        if (stopped.getAsBoolean()) {
            return null;
        }
        String obi = clean(brain.ask(OBI, base));
        if (obi.isEmpty() || stopped.getAsBoolean()) {
            return null;
        }
        steps.step("Obi made the plan", "Obi", obi);
        String ivie = clean(brain.ask(IVIE, base + "\n\nObi's plan:\n" + obi));
        if (stopped.getAsBoolean()) {
            return null;
        }
        steps.step(disagrees(ivie) ? "Ivie checked it against the real world and disagrees on one point"
                : "Ivie checked it against the real world and its UI/UX", "Ivie", ivie);
        boolean second = false;
        if (disagrees(ivie)) {
            String recheck = clean(brain.ask(RECHECK, base + "\n\nYour plan:\n" + obi + "\n\nIvie disagrees: " + point(ivie)));
            if (!recheck.isEmpty()) {
                obi = obi + "\n\nAfter Ivie's point: " + recheck;
                second = true;
                steps.step("Obi revised the plan on Ivie's point", "Obi", recheck);
            }
        }
        if (stopped.getAsBoolean()) {
            return null;
        }
        String ibiko = clean(brain.ask(IBIKO, base + "\n\nObi's plan:\n" + obi + "\n\nIvie's check:\n" + ivie));
        if (stopped.getAsBoolean()) {
            return null;
        }
        if (!ibiko.isEmpty()) {
            steps.step("Ibiko worked out how to build it", "Ibiko", ibiko);
        }
        String notes = base + "\n\nObi (the plan):\n" + obi + "\n\nIvie (real world, UI/UX):\n" + ivie
                + (ibiko.isEmpty() ? "" : "\n\nIbiko (how to build it):\n" + ibiko);
        // a small model may copy a template line ("Obi checked …; Ivie considered …") word for word: dropped
        String answer = clean(brain.ask(synth(speaker), notes)).replaceAll("(?m)^.*(checked|considered|worked out) \u2026.*$\\n?", "").trim();
        if (answer.isEmpty() || stopped.getAsBoolean()) {
            return null;
        }
        return new Result(answer, obi, ivie, ibiko, second);
    }

    static boolean disagrees(String review) {
        return review != null && review.matches("(?is).*VERDICT:\\s*DISAGREE.*");
    }

    static String point(String review) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)VERDICT:\\s*DISAGREE:?\\s*(.*)").matcher(review);
        return m.find() ? m.group(1).trim() : review;
    }

    private static String clean(String reply) {
        return reply == null ? "" : reply.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim();
    }
}
