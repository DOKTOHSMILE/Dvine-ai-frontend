package ai.ibiko.llm;

/** One tool the model may call: its name, what it does, and how the arguments are written. */
public final class ToolDef {
    public final String name;
    public final String help;
    public final String arguments;

    public ToolDef(String name, String help, String arguments) {
        this.name = name;
        this.help = help;
        this.arguments = arguments;
    }
}
