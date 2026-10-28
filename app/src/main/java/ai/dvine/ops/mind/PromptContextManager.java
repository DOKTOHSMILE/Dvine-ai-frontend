package ai.dvine.ops.mind;

import java.util.List;

/** Keeps only the current instruction and a few relevant notes. Never the whole history. */
public final class PromptContextManager {
    private static final int MAX = 700;

    public String compact(String instruction, String taskState, List<String> notes) {
        StringBuilder b = new StringBuilder();
        b.append("Instruction: ").append(clip(instruction, 240)).append('\n');
        if (taskState != null && !taskState.isEmpty()) {
            b.append("Task: ").append(clip(taskState, 120)).append('\n');
        }
        if (notes != null) {
            int n = 0;
            for (String note : notes) {
                if (note == null || note.isEmpty() || n >= 3) {
                    continue;
                }
                b.append("Note: ").append(clip(note, 100)).append('\n');
                n++;
            }
        }
        String out = b.toString();
        return out.length() <= MAX ? out : out.substring(0, MAX);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
