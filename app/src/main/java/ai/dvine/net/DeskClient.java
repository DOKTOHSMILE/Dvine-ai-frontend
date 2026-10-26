package ai.dvine.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import ai.dvine.brain.MiniJson;
import ai.dvine.turn.TurnLine;

/**
 * The online client (M7): when the phone can't answer by itself, the
 * question goes to the server's Obi/Ivie deliberation and comes back as
 * lines to think and say. The phone keeps one desk room per user (created
 * on first use, recreated if the server lost it) and every question carries
 * a fresh request id, so a retry never runs it twice. Blocking: call it off
 * the main thread. Any failure returns null and the phone keeps its
 * offline answer.
 */
public final class DeskClient {
    /** One HTTP call; swapped for a fake in tests. */
    public interface Http {
        SessionClient.Reply send(String method, String url, String token, String json) throws IOException;
    }

    /** Where the phone remembers its desk room. */
    public interface RoomStore {
        String get();

        void put(String roomId);
    }

    private final Supplier<String> root;
    private final Supplier<String> token;
    private final Runnable invalidate;
    private final RoomStore rooms;
    private final Http http;

    public DeskClient(Supplier<String> root, Supplier<String> token, Runnable invalidate, RoomStore rooms, Http http) {
        this.root = root;
        this.token = token;
        this.invalidate = invalidate;
        this.rooms = rooms;
        this.http = http == null ? DeskClient::https : http;
    }

    /** The server's lines for this question (THINK and SAY only), or null when it can't be reached. */
    public List<TurnLine> ask(String text, String onScreen) {
        String base = root.get();
        if (base == null || text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                String t = token.get();
                if (t == null) {
                    return null;
                }
                String room = room(base, t);
                if (room == null) {
                    return null;
                }
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("requestId", "ph-" + UUID.randomUUID());
                body.put("text", text.length() > 4000 ? text.substring(0, 4000) : text);
                if ("obi".equals(onScreen) || "ivie".equals(onScreen)) {
                    body.put("activeAgent", onScreen);
                }
                SessionClient.Reply r = http.send("POST", base + "/api/v1/desk-rooms/" + room + "/commands", t,
                        MiniJson.write(body));
                if (r.code == 401) {
                    invalidate.run(); // token expired or revoked: sign in again once
                    continue;
                }
                if (r.code == 404) {
                    rooms.put(null); // the server no longer has our room: make a new one once
                    continue;
                }
                if (r.code != 200 && r.code != 201) {
                    return null;
                }
                String taskId = MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("taskId"), null);
                if (taskId == null) {
                    return null;
                }
                SessionClient.Reply ev = http.send("GET", base + "/api/v1/tasks/" + taskId + "/events?after=0", t, null);
                if (ev.code != 200) {
                    return null;
                }
                List<TurnLine> lines = lines(ev.body);
                return lines.isEmpty() ? null : lines;
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** THINK and SAY events with a persona become lines; STATUS events are the task machine and stay silent. */
    static List<TurnLine> lines(String json) {
        List<TurnLine> out = new ArrayList<TurnLine>();
        for (Object o : MiniJson.list(MiniJson.parse(json))) {
            Map<String, Object> e = MiniJson.obj(o);
            String cue = MiniJson.str(e.get("cue"), "");
            String agent = MiniJson.str(e.get("agent"), "");
            String message = MiniJson.str(e.get("message"), "").trim();
            if ("THINK".equals(cue) && message.matches("(?i)(obi|ivie) is thinking\\.?")) {
                message = ""; // a placeholder, not a thought: the bubble shows thinking dots
            } else if (!("SAY".equals(cue) || "THINK".equals(cue)) || message.isEmpty()) {
                continue;
            }
            if (!"obi".equals(agent) && !"ivie".equals(agent)) {
                continue;
            }
            out.add(new TurnLine(agent, cue, message.length() > 500 ? message.substring(0, 500) : message));
        }
        return out;
    }

    private String room(String base, String t) throws IOException {
        String room = rooms.get();
        if (room != null) {
            return room;
        }
        SessionClient.Reply r = http.send("POST", base + "/api/v1/desk-rooms", t, "{\"title\":\"phone\"}");
        if (r.code != 201) {
            return null;
        }
        room = MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("deskRoomId"), null);
        if (room != null && room.matches("[A-Za-z0-9-]{8,64}")) {
            rooms.put(room);
            return room;
        }
        return null;
    }

    static SessionClient.Reply https(String method, String url, String token, String json) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("only https");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(25000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (json != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            return new SessionClient.Reply(code, in == null ? "" : KnowledgeStore.read(in));
        } finally {
            c.disconnect();
        }
    }
}
