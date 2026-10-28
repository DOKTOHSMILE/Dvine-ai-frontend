package ai.dvine.ops.pipe;

import java.util.ArrayList;
import java.util.List;

public final class SemanticTypes {
    private SemanticTypes() {
    }

    public static final class Decomposition {
        public String original = "";
        public String action = "";
        public String target = "";
        public String parent = "";
        public final List<String> preserve = new ArrayList<String>();
        public final List<String> negations = new ArrayList<String>();
        public boolean question;
        public boolean ambiguous;
        public String modality = "text";
        public double transcriptConfidence = 1;
    }

    public static final class Interpretation {
        public String status = "understood";
        public String intent = "chat";
        public String operation = "";
        public String target = "";
        public String parent = "";
        public final List<String> constraints = new ArrayList<String>();
        public boolean clarification;
        public String clarify = "";
        public String confidenceLabel = "grounded";
    }

    public static final class Plan {
        public final List<String> steps = new ArrayList<String>();
        public String tool = "";
        public boolean destructive;
    }

    public static final class ToolResult {
        public boolean ok;
        public boolean verified;
        public String evidence = "";
        public String error = "";
        public String speech = "";
        public String solidText = "";
        public String subject = "";
    }
}
