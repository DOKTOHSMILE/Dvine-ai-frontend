package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.List;

/**
 * What an investigation of a message or post found: its subject, the claims (factual or opinion), the people,
 * places and dates in it, the original post if one was found, posts that support or contradict it, what stays
 * unverified, and every source. A similar post is never counted as proof: support and contradiction come from the
 * verification step, which the selected brain carries out over the evidence. Pure Java.
 */
public final class InvestigationReport {
    public final String message;
    public String subject = "";
    public final List<ClaimExtractor.Claim> claims = new ArrayList<ClaimExtractor.Claim>();
    public String entities = "";
    public SocialPost original;
    public final List<SocialPost> related = new ArrayList<SocialPost>();
    public final List<String> web = new ArrayList<String>();
    public final List<String> sources = new ArrayList<String>();
    public final List<String> limits = new ArrayList<String>();

    public InvestigationReport(String message) {
        this.message = message == null ? "" : message;
    }

    /** The evidence, laid out for the brain to weigh and conclude. */
    public String text() {
        StringBuilder b = new StringBuilder("Investigation of the message.\nSubject: ").append(subject).append('\n');
        if (!entities.isEmpty()) {
            b.append(entities).append('\n');
        }
        b.append("Claims:\n");
        for (ClaimExtractor.Claim c : claims) {
            b.append(c.factual ? "- [factual, to verify] " : "- [opinion] ").append(c.text).append('\n');
        }
        b.append(original == null ? "Original post: not found.\n" : "Possible original or earliest post: " + original.line() + "\n");
        if (!related.isEmpty()) {
            b.append("Posts about the same subject (discussion, not proof):\n");
            for (SocialPost p : related) {
                b.append("- ").append(p.line()).append('\n');
            }
        }
        if (!web.isEmpty()) {
            b.append("Web evidence (to weigh for and against each factual claim):\n");
            for (String w : web) {
                b.append(w).append('\n');
            }
        }
        if (!sources.isEmpty()) {
            b.append("Sources: ").append(String.join(", ", sources)).append('\n');
        }
        for (String l : limits) {
            b.append("Limit: ").append(l).append('\n');
        }
        b.append("Write the report: original source (if found), subject, claims, supporting evidence, contradicting"
                + " evidence, unverified statements, a conclusion that states its confidence, and the source links."
                + " Similar posts show what people say, not that it is true.");
        return b.toString();
    }
}
