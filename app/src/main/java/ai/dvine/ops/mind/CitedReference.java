package ai.dvine.ops.mind;

import java.util.List;

/**
 * What a world question may be told. Two sources, or an honest single source.
 * Nothing, when the pages do not support a claim.
 */
public final class CitedReference {
    private CitedReference() {
    }

    public static String collect(String question, InternetResearchEngine.Fetcher fetcher, long now) {
        if (fetcher == null || question == null || question.isBlank()) {
            return null;
        }
        ClaimCheck.Report report = new ClaimCheck().inspect(question, true,
                List.of(ClaimCheck.wikipedia(fetcher), ClaimCheck.wikidata(fetcher),
                        ClaimCheck.duckDuckGo(fetcher), ClaimCheck.reddit(fetcher)), now);
        if (report.verdict != ClaimCheck.Verdict.VERIFIED
                && report.verdict != ClaimCheck.Verdict.SINGLE_SOURCE
                && report.verdict != ClaimCheck.Verdict.CONFLICT) {
            return null;
        }
        return report.speech;
    }
}
