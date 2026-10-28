package ai.dvine.ops.pipe;

/** One pass from words to a checked interpretation. It does not execute tools. */
public final class NaturalLanguageActionEngine {
    private final ConversationInputProcessor input = new ConversationInputProcessor();
    private final ConversationDecompositionEngine decompose = new ConversationDecompositionEngine();
    private final BrainInterpretationEngine brain = new BrainInterpretationEngine();
    private final ContextualMeaningResolver context = new ContextualMeaningResolver();
    private final IntentDeterminationEngine intents = new IntentDeterminationEngine();
    private final ActionPlanningEngine planning = new ActionPlanningEngine();
    private final ToolExecutionRouter router = new ToolExecutionRouter();

    public static final class Read {
        public final SemanticTypes.Decomposition decomposition;
        public final SemanticTypes.Interpretation interpretation;
        public final String intent;
        public final SemanticTypes.Plan plan;
        public final String tool;

        public Read(SemanticTypes.Decomposition decomposition, SemanticTypes.Interpretation interpretation, String intent,
                    SemanticTypes.Plan plan, String tool) {
            this.decomposition = decomposition;
            this.interpretation = interpretation;
            this.intent = intent;
            this.plan = plan;
            this.tool = tool;
        }
    }

    public Read read(String text, double confidence, SemanticKnowledgeRepository memory, BrainInterpretationEngine.ModelReading model) {
        SemanticTypes.Decomposition d = input.begin(text, "text", confidence);
        decompose.fill(d);
        if (!d.target.isEmpty()) {
            d.target = context.retarget(d.target, d.parent.isEmpty() ? "object" : d.parent, memory);
        }
        SemanticTypes.Interpretation interpretation = brain.interpret(d, model);
        String intent = intents.intent(interpretation);
        SemanticTypes.Plan plan = planning.plan(intent, d);
        return new Read(d, interpretation, intent, plan, router.route(plan));
    }
}
