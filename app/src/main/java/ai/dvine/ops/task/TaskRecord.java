package ai.dvine.ops.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recoverable task. Secrets and conversation transcripts do not belong here. */
public final class TaskRecord {
    public String id;
    public String instruction = "";
    public String description = "";
    public TaskState state = TaskState.QUEUED;
    public final List<String> completed = new ArrayList<String>();
    public final List<String> remaining = new ArrayList<String>();
    public String checkpoint = "";
    public final List<String> assets = new ArrayList<String>();
    public String permissionNote = "";
    public int retries;
    public String lastError = "";
    public String cancelStatus = "";
    public long createdAt;
    public long updatedAt;
    public boolean destructive;
    public boolean destructiveAuthorized;
    public String idempotencyKey = "";
    public String kind = "general";
    public final Map<String, String> facts = new LinkedHashMap<String, String>();

    public boolean stepDone(String step) {
        return completed.contains(step);
    }

    public String oneLine() {
        String d = description == null || description.isEmpty() ? instruction : description;
        return d + " · " + state.name().toLowerCase().replace('_', ' ');
    }
}
