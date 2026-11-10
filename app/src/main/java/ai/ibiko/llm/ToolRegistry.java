package ai.ibiko.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The tools a prompt may name. Empty until something is added. */
public final class ToolRegistry {
    private final Map<String, ToolDef> tools = new LinkedHashMap<String, ToolDef>();

    public ToolRegistry add(ToolDef tool) {
        if (tool != null && tool.name != null && !tool.name.isEmpty()) {
            tools.put(tool.name, tool);
        }
        return this;
    }

    public ToolDef get(String name) {
        return name == null ? null : tools.get(name);
    }

    public List<ToolDef> all() {
        return new ArrayList<ToolDef>(tools.values());
    }
}
