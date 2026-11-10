package ai.ibiko.llm;

import java.util.List;

/**
 * The section of the system prompt that tells the brain it has tools: which tags it may use, what each one does and
 * what the app expects back. Built from the real {@link ToolRegistry} names and the real {@link ToolDef} help text,
 * so it cannot drift away from the handlers, and empty when nothing is registered. Plain text: the app sends the
 * prompt to llama.cpp, and this paragraph is the only place the model learns what a [[tool:...]] tag is — without it
 * the model has no way to know that calling tools is what its caller wants.
 * Pure Java, no Android types, so it can be measured off the device.
 */
public final class ToolHow {
    private ToolHow() {
    }

    /** The sentence that marks the section. Its absence from a prompt means the brain was never told about tools. */
    public static final String HAS_TOOLS = "You have tools.";

    /** True when a prompt already carries the tool section. */
    public static boolean mentions(String prompt) {
        return prompt != null && prompt.contains(HAS_TOOLS);
    }

    /** True when a tool of that name exists in the registry. */
    public static boolean knows(ToolRegistry r, String name) {
        return r != null && r.get(name) != null;
    }

    private static final String INTRO =
            " You reach them by ending your reply with [[tool:name]] followed by the arguments on the same line,"
                    + " exactly like this: [[tool:open app|whatsapp]] — no spaces inside the brackets, the"
                    + " arguments after the closing ] with | between them. The app reads the tag, runs the tool"
                    + " and sends you the result; you then answer from what came back. Only use a name listed"
                    + " below, never a name you invent, and call one tool per reply. When you have what you need,"
                    + " answer normally and stop: do not call the same tool again for the same request. Never"
                    + " write [[switch:...]] here, and never claim to have run a tool you did not call.";

    /** The whole section for this registry, empty when nothing is registered. */
    public static String text(ToolRegistry r) {
        return text(r == null ? null : r.all());
    }

    /** The section for an explicit list of tools (used by tests with a hand-built registry). */
    public static String text(List<ToolDef> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder(HAS_TOOLS);
        s.append(INTRO);
        for (ToolDef tool : tools) {
            if (tool == null || tool.name == null || tool.name.isEmpty()) {
                continue;
            }
            s.append(" [[").append(tool.name).append("]] ").append(tool.help == null ? "" : tool.help.trim());
            String a = tool.arguments == null ? "" : tool.arguments.trim();
            if (!a.isEmpty()) {
                s.append(" Arguments: ").append(a).append('.');
            }
        }
        return s.toString();
    }
}