package ai.dvine.agent;

import java.util.Locale;

/**
 * A debate between two personas on any topic the user names: Obi argues for the motion, Ivie against, in three
 * rounds (opening, rebuttal, closing), each answering what the other just said. The brain writes each turn from
 * the motion and the debate so far ({@link #prompt}); {@link #take} keeps it to a few spoken sentences. Pure Java.
 */
public final class Debate {
    private static final String[] WHO = {"obi", "ivie", "obi", "ivie", "obi", "ivie"};
    private static final String[] ROLE = {"opening", "opening", "rebuttal", "rebuttal", "closing", "closing"};
    public final String topic;
    private final StringBuilder transcript = new StringBuilder();
    private int turn;

    public Debate(String topic) {
        this.topic = topic.trim();
    }

    /** The topic when the words ask for a debate ("debate pineapple on pizza", "let Obi and Ivie debate AI"), else null. */
    public static String topicOf(String said) {
        if (said == null) {
            return null;
        }
        String t = said.trim().replaceAll("[?.!]+$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(?:(?:can you |could you |please |let'?s |let (?:obi and ivie|ivie and obi|the two of you|both of you) |i want (?:a |you to |to see a )?|"
                        + "start |have |hold |do )*(?:a |an )?)?(?:debate|argue)(?: (?:about|on|over|whether|the topic|the motion))?:? (.{3,})$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        if (m.matches()) {
            String topic = m.group(1).replaceAll("^(that|if) ", "").trim();
            return topic.toLowerCase(Locale.US).matches("^(it|this|that|something)$") ? null : topic;
        }
        return null;
    }

    public boolean done() {
        return turn >= WHO.length;
    }

    public String speaker() {
        return WHO[Math.min(turn, WHO.length - 1)];
    }

    public String role() {
        return ROLE[Math.min(turn, ROLE.length - 1)];
    }

    /** How the persona should speak this turn. */
    public String rules() {
        boolean forIt = turn % 2 == 0;
        return "You are in a friendly, sharp debate with " + (forIt ? "Ivie" : "Obi") + " in front of the user. The motion: \""
                + topic + "\". You argue " + (forIt ? "FOR" : "AGAINST") + " it, whatever you personally think. This is your "
                + role() + ". Speak in two to four short spoken sentences, no lists or headings. "
                + (turn >= 2 ? "Answer the other side's last points directly, by name. " : "Make your strongest case. ")
                + (turn >= 4 ? "Sum up why your side wins. " : "") + "/no_think";
    }

    public String prompt() {
        return "The debate so far:\n" + (transcript.length() == 0 ? "(nothing yet)\n" : transcript) + "\nYour " + role() + ":";
    }

    /** The persona's turn as written, tidied to spoken sentences; null when the brain wrote nothing. */
    public String take(String written) {
        if (written == null) {
            return null;
        }
        String s = ai.dvine.llm.ActionTags.strip(written).replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("(?m)^\\s*(Obi|Ivie|Rebuttal|Opening|Closing)\\s*:\\s*", "").replaceAll("[*#_`]", "")
                .replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] sentences = s.split("(?<=[.!?])\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < sentences.length && i < 4; i++) {
            b.append(i == 0 ? "" : " ").append(sentences[i]);
        }
        String line = b.toString();
        transcript.append(WHO[turn].equals("obi") ? "Obi" : "Ivie").append(" (").append(role()).append("): ").append(line).append('\n');
        turn++;
        return line;
    }

    public static String opening(String topic) {
        return "A debate: " + topic.trim() + ". I'll argue for it, and Ivie against.";
    }

    public static String closing() {
        return "That's our debate. Who convinced you?";
    }
}
