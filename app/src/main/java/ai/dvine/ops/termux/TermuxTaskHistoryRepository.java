package ai.dvine.ops.termux;

import java.util.ArrayList;
import java.util.List;

public final class TermuxTaskHistoryRepository {
    public static final class Entry {
        public final String purpose;
        public final int exit;
        public final boolean verified;
        public final String summary;

        public Entry(String purpose, int exit, boolean verified, String summary) {
            this.purpose = purpose;
            this.exit = exit;
            this.verified = verified;
            this.summary = summary;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();

    public void add(Entry entry) {
        entries.add(entry);
        if (entries.size() > 50) {
            entries.remove(0);
        }
    }

    public Entry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    public List<Entry> all() {
        return new ArrayList<Entry>(entries);
    }
}
