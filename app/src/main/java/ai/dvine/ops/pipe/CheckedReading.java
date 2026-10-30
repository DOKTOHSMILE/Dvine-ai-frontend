package ai.dvine.ops.pipe;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.dvine.llm.LocalModel;

/**
 * A model may only name an operation from the fixed list. Anything else is
 * ignored. This does not run the model on the screen thread; the caller asks.
 */
public final class CheckedReading implements BrainInterpretationEngine.ModelReading {
    public interface Ask {
        String ask(String prompt);
    }

    public static final String SYSTEM = "Reply with one line: operation=<answer|clarify|detach|delete|none|research|chat|reconstruct|explain> and optionally target=<name>. No other words.";

    private static final Set<String> ALLOWED = Set.of("answer", "clarify", "detach", "delete", "none", "research", "chat", "reconstruct", "explain");
    private static final Pattern LINE = Pattern.compile("operation=([a-z_]+)(?:\\s+target=([a-z0-9][a-z0-9 \\-]{0,40}))?");

    private final Ask ask;

    public CheckedReading(Ask ask) {
        this.ask = ask;
    }

    /** Uses the loaded model. Returns nothing when it is not ready, so the rules stand. */
    public static CheckedReading of(LocalModel model) {
        return new CheckedReading(prompt -> {
            if (model == null || !model.ready()) {
                return null;
            }
            return model.task(SYSTEM, prompt, 48, null);
        });
    }

    public SemanticTypes.Interpretation read(SemanticTypes.Decomposition decomposition) {
        if (ask == null || decomposition == null) {
            return null;
        }
        String raw;
        try {
            raw = ask.ask(decomposition.original == null ? "" : decomposition.original);
        } catch (RuntimeException e) {
            return null;
        }
        if (raw == null) {
            return null;
        }
        Matcher matcher = LINE.matcher(raw.toLowerCase(Locale.US).replace('\n', ' '));
        if (!matcher.find()) {
            return null;
        }
        String operation = matcher.group(1);
        if (!ALLOWED.contains(operation) || "none".equals(operation)) {
            return null;
        }
        SemanticTypes.Interpretation proposed = new SemanticTypes.Interpretation();
        proposed.operation = operation;
        proposed.intent = operation;
        proposed.target = matcher.group(2) == null ? "" : matcher.group(2).trim();
        proposed.status = "understood";
        return proposed;
    }
}
