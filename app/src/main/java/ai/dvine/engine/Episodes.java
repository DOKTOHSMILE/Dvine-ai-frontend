package ai.dvine.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import ai.dvine.brain.MiniJson;

/**
 * The phone's short episodic buffer: what happened (actions, scenes, touches,
 * persona switches, failures), never what the user said word for word.
 * Long-term storage and consolidation are server jobs: once an episode is
 * uploaded it is kept here only briefly, and the buffer is capped.
 */
public final class Episodes {
    /** Uploaded episodes stay this long on the phone, then the server holds them. */
    static final long KEEP_UPLOADED_MS = 3L * 24 * 3600 * 1000;
    static final int MAX_KEPT = 300;
    static final int MAX_PENDING = 2000;

    public static final class Episode {
        public final String id;
        public final String kind;
        public final String tag;
        public final String summary;
        public final float importance;
        public final long at;
        boolean uploaded;

        Episode(String id, String kind, String tag, String summary, float importance, long at, boolean uploaded) {
            this.id = id;
            this.kind = kind;
            this.tag = tag;
            this.summary = summary;
            this.importance = importance;
            this.at = at;
            this.uploaded = uploaded;
        }
    }

    private final File file;
    private final BooleanSupplier consent;
    private final List<Episode> list = new ArrayList<Episode>();
    private int counter;

    /** {@code dir} may be null (memory only, for tests). */
    public Episodes(File dir, BooleanSupplier consent) {
        this.file = dir == null ? null : new File(dir, "episodes.jsonl");
        this.consent = consent;
        load();
    }

    /** Records an episode. Tags look like "pose:pushups"; summaries are generated, never the user's words. */
    public synchronized Episode record(String kind, String tag, String summary, float importance, long now) {
        Episode e = new Episode("ep-" + now + "-" + (++counter), kind, clean(tag), summary, importance, now, false);
        list.add(e);
        prune(now);
        save();
        return e;
    }

    /** Episodes waiting to go to the server (none when sharing is off). */
    public synchronized List<Episode> pending(int max) {
        List<Episode> out = new ArrayList<Episode>();
        if (!consent.getAsBoolean()) {
            return out;
        }
        for (Episode e : list) {
            if (!e.uploaded && out.size() < max) {
                out.add(e);
            }
        }
        return out;
    }

    public synchronized void markUploaded(Collection<String> ids, long now) {
        Set<String> set = new HashSet<String>(ids);
        for (Episode e : list) {
            if (set.contains(e.id)) {
                e.uploaded = true;
            }
        }
        prune(now);
        save();
    }

    public synchronized int size() {
        return list.size();
    }

    public synchronized List<Episode> recent(int n) {
        return new ArrayList<Episode>(list.subList(Math.max(0, list.size() - n), list.size()));
    }

    /** The JSON body for POST /api/v1/episodes/sync. */
    public static String syncBody(String deviceId, List<Episode> batch) {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"deviceId\":").append(q(deviceId)).append(",\"episodes\":[");
        for (int i = 0; i < batch.size(); i++) {
            Episode e = batch.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(json(e, false));
        }
        return sb.append("]}").toString();
    }

    private void prune(long now) {
        // drop uploaded episodes once they are old (the server has them), then cap the buffer
        list.removeIf(e -> e.uploaded && now - e.at > KEEP_UPLOADED_MS);
        while (list.size() > MAX_KEPT) {
            int drop = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).uploaded) {
                    drop = i;
                    break;
                }
            }
            if (drop < 0) {
                if (list.size() <= MAX_PENDING) {
                    break; // offline for a long time: keep what has not reached the server yet
                }
                drop = weakest();
            }
            list.remove(drop);
        }
    }

    private int weakest() {
        int w = 0;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).importance < list.get(w).importance) {
                w = i;
            }
        }
        return w;
    }

    private void load() {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> m = MiniJson.obj(MiniJson.parse(line));
                    list.add(new Episode(MiniJson.str(m.get("id"), ""), MiniJson.str(m.get("kind"), ""),
                            MiniJson.str(m.get("tag"), ""), MiniJson.str(m.get("summary"), ""),
                            (float) MiniJson.num(m.get("importance"), 0.3), (long) MiniJson.num(m.get("occurredAt"), 0),
                            Boolean.TRUE.equals(m.get("uploaded"))));
                } catch (IllegalArgumentException bad) {
                    // skip a damaged line
                }
            }
        } catch (IOException ignored) {
            // start empty
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (Episode e : list) {
                w.write(json(e, true));
                w.write('\n');
            }
        } catch (IOException ignored) {
            // memory still holds them
        }
    }

    private static String json(Episode e, boolean withState) {
        StringBuilder sb = new StringBuilder("{\"id\":").append(q(e.id)).append(",\"kind\":").append(q(e.kind))
                .append(",\"tag\":").append(q(e.tag)).append(",\"summary\":").append(q(e.summary))
                .append(",\"importance\":").append(Math.round(e.importance * 100) / 100.0)
                .append(",\"occurredAt\":").append(e.at);
        if (withState) {
            sb.append(",\"uploaded\":").append(e.uploaded);
        }
        return sb.append('}').toString();
    }

    /** Tags are lower-case "group:value" with plain characters, as the server expects. */
    static String clean(String tag) {
        String t = tag.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9: _-]", "").trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
