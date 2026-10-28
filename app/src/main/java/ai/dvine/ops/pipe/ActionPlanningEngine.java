package ai.dvine.ops.pipe;

public final class ActionPlanningEngine {
    public SemanticTypes.Plan plan(String intent, SemanticTypes.Decomposition decomposition) {
        SemanticTypes.Plan plan = new SemanticTypes.Plan();
        switch (intent) {
            case "explain":
                plan.tool = "speech";
                plan.steps.add("explain");
                break;
            case "clock":
                plan.tool = "clock";
                plan.steps.add("read-clock");
                break;
            case "picker":
            case "learn":
                plan.tool = "picker";
                plan.steps.add("open-picker");
                break;
            case "news":
            case "answer":
                plan.tool = "brain";
                plan.steps.add("answer");
                break;
            case "delete":
                plan.tool = "files";
                plan.destructive = true;
                plan.steps.add("confirm");
                plan.steps.add("delete");
                break;
            case "storage":
                plan.tool = "termux";
                plan.steps.add("probe");
                plan.steps.add("explain");
                break;
            case "ssh":
                plan.tool = "ssh";
                plan.steps.add("connect");
                plan.steps.add("read");
                plan.steps.add("explain");
                break;
            case "find":
                plan.tool = "files";
                plan.steps.add("search");
                plan.steps.add("choose");
                plan.steps.add("inspect");
                break;
            case "reconstruct":
                plan.tool = "reconstruct";
                plan.steps.add("inspect");
                plan.steps.add("build");
                plan.steps.add("verify");
                break;
            case "remove":
                plan.tool = "scene";
                plan.steps.add("select");
                plan.steps.add("edit");
                plan.steps.add("verify");
                break;
            case "research":
                plan.tool = "research";
                plan.steps.add("search");
                plan.steps.add("summarize");
                break;
            case "reject":
            case "clarify":
                plan.tool = "speech";
                plan.steps.add("ask");
                break;
            default:
                plan.tool = "none";
                break;
        }
        return plan;
    }
}
