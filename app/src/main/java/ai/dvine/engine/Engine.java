package ai.dvine.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

import ai.dvine.brain.BrainAnswer;
import ai.dvine.brain.MiniJson;

/**
 * The on-device control engine. It turns what the brain
 * decided into validated commands, owns the body through the
 * {@link Supervisor}, keeps working memory and a simple affect state, records
 * episodes, and can report what it is doing. Everything here runs on the
 * phone, offline and in real time. Secondary jobs (the long-term episode
 * archive, consolidation into favourites, open-ended reasoning) belong to the
 * server; their results only flow back in as hints ({@link #applyProfile}).
 */
public final class Engine implements Supervisor.Listener {
    public final Supervisor supervisor;
    public final Affect affect = new Affect();
    public final WorkingMemory memory = new WorkingMemory();
    public final Episodes episodes;
    private final BodyPort port;
    private final Map<String, List<String>> favourites = new LinkedHashMap<String, List<String>>();
    private String persona = "obi";
    private float[] shownMood = {0f, 0f, 0f};

    public Engine(BodyPort port, File dir, BooleanSupplier consent) {
        this.port = port;
        this.episodes = new Episodes(dir, consent);
        this.supervisor = new Supervisor(port, this);
    }

    // ------------------------------------------------------------ inputs

    /** What the user just said (the words stay in short-term memory only). */
    public void onHeard(String text, long now) {
        String t = text.toLowerCase(Locale.US).trim();
        if (t.matches(".*\\b(thank(s| you)|well done|good job|great|amazing|i love|nice one|brilliant)\\b.*")) {
            affect.appraise("praise");
        } else if (t.matches(".*\\b(wrong|mistake|incorrect|not right|that's bad|you failed)\\b.*")) {
            affect.appraise("criticism");
        } else if (t.endsWith("?") || t.matches("^(what|why|how|who|when|where|which|can|could|is|are|do|does)\\b.*")) {
            affect.appraise("question");
        }
        memory.put("topic", t.length() > 60 ? t.substring(0, 60) : t, 2, 90_000L, now);
    }

    public void setPersona(String who, long now) {
        if (!who.equals(persona)) {
            persona = who;
            episodes.record("PERSONA", "persona:" + who, "switched to " + who, 0.2f, now);
        }
        memory.put("persona", who, 5, 3_600_000L, now);
    }

    /** Carries out what the brain answered, through the supervisor. */
    public void perform(BrainAnswer a, long now) {
        if (a.command != null) {
            switch (a.command) {
                case "TURN":
                    submit(new Command("body.turn", null, Command.PRIORITY_USER, now));
                    break;
                case "POSE":
                    if (submit(new Command("body.pose", a.commandValue, Command.PRIORITY_USER, now)) == Command.Status.RUNNING) {
                        affect.appraise("exercise");
                        memory.put("doing", a.commandValue, 6, 600_000L, now);
                        episodes.record("ACTION", "pose:" + a.commandValue, "moved into " + a.commandValue, 0.4f, now);
                    }
                    break;
                case "STOP":
                    submit(new Command("body.stop", null, Command.PRIORITY_STOP, now));
                    affect.appraise("stop");
                    memory.put("doing", "standing", 6, 600_000L, now);
                    break;
                default:
                    break;
            }
        }
        if (a.sketch != null) {
            Command c = new Command("draw.sketch", a.sketch.title, Command.PRIORITY_USER, now);
            c.payload = a.sketch;
            submit(c);
            episodes.record("ACTION", "sketch:" + a.sketch.title, "drew " + a.sketch.title, 0.3f, now);
        }
        if (a.object3d != null) {
            submit(new Command("draw.object", a.object3d, Command.PRIORITY_USER, now));
            episodes.record("ACTION", "object:" + a.object3d, "built " + a.object3d, 0.3f, now);
        }
        String source = a.source == null ? "" : a.source.toLowerCase(Locale.US);
        if (a.needsOnline || source.equals("offline")) {
            affect.appraise("unknown");
            episodes.record("FAILURE", "unknown:offline", "could not answer offline", 0.5f, now);
        } else if (source.equals("learn") || source.equals("memory")) {
            affect.appraise("learned");
            episodes.record("LEARN", "learn:" + source, "learned something new", 0.7f, now);
        } else if (!source.isEmpty()) {
            episodes.record("TALK", "talk:" + source, "answered from " + source, 0.15f, now);
        }
    }

    /** Behaviour-rate update (about 10 Hz): timed commands, mood drift, expression. */
    public void tick(long now) {
        supervisor.tick(now);
        affect.decay(now);
        float[] m = affect.expression();
        if (Math.abs(m[0] - shownMood[0]) + Math.abs(m[1] - shownMood[1]) + Math.abs(m[2] - shownMood[2]) * 0.05f > 0.01f) {
            shownMood = m;
            port.setMood(m[0], m[1], m[2]);
        }
    }

    // ------------------------------------------------------------ server hints

    /** Consolidated favourites from the server (GET /api/v1/episodes/profile). Safe to ignore. */
    public synchronized void applyProfile(Map<String, Object> profile, long now) {
        Object fav = profile.get("favourites");
        if (!(fav instanceof Map)) {
            return;
        }
        favourites.clear();
        for (Map.Entry<String, Object> e : MiniJson.obj(fav).entrySet()) {
            favourites.put(e.getKey(), new ArrayList<String>(MiniJson.strings(e.getValue())));
        }
        List<String> scenes = favourites.get("scene");
        if (scenes != null && !scenes.isEmpty()) {
            memory.put("favourite_scene", scenes.get(0), 4, 24L * 3_600_000L, now);
        }
    }

    public synchronized List<String> favourites(String group) {
        List<String> l = favourites.get(group);
        return l == null ? new ArrayList<String>() : new ArrayList<String>(l);
    }

    /** "What do I like?" answered from the server's consolidation, or honestly when there is none yet. */
    public synchronized String favouritesLine() {
        List<String> parts = new ArrayList<String>();
        List<String> poses = favourites("pose");
        List<String> scenes = favourites("scene");
        List<String> objects = favourites("object");
        if (!poses.isEmpty()) {
            parts.add("you often ask me for " + join(poses));
        }
        if (!scenes.isEmpty()) {
            parts.add("your favourite scene is " + scenes.get(0));
        }
        if (!objects.isEmpty()) {
            parts.add("you like seeing " + join(objects));
        }
        if (parts.isEmpty()) {
            return "I don't know your favourites yet. Once my memories are backed up to the server, I'll learn them.";
        }
        String s = String.join(", and ", parts) + ".";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------ self-monitoring

    /** The embodied state model: one snapshot every part of the system can read. */
    public Map<String, Object> state(long now) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("persona", persona);
        Map<String, Object> channels = new LinkedHashMap<String, Object>();
        for (Command.Channel ch : Command.Channel.values()) {
            Command c = supervisor.active(ch);
            channels.put(ch.name().toLowerCase(Locale.US), c == null ? null : c.toString());
        }
        s.put("active", channels);
        s.put("mood", affect.label());
        s.put("workingMemory", memory.snapshot(now));
        s.put("episodesOnPhone", episodes.size());
        return s;
    }

    /** "What are you doing?": what is running, the last problem, and the mood, from real state only. */
    public String selfReport(long now) {
        Command body = supervisor.active(Command.Channel.BODY);
        Command draw = supervisor.active(Command.Channel.DRAW);
        StringBuilder sb = new StringBuilder();
        sb.append("I'm here on the pod, listening for you. ");
        if (draw != null) {
            sb.append("Above the pod: ").append(draw.value).append(". ");
        }
        if (body != null) {
            sb.append("The body I designed is ").append(describe(body)).append(". ");
        }
        Command.Result fail = null;
        for (Command.Result r : supervisor.history()) {
            if (r.status == Command.Status.REJECTED || r.status == Command.Status.EXPIRED) {
                fail = r;
            }
        }
        if (fail != null) {
            sb.append("Last thing I couldn't do: ").append(fail.command).append(", because ").append(fail.reason).append(". ");
        }
        sb.append("I feel ").append(affect.label()).append(".");
        return sb.toString();
    }

    @Override
    public void onResult(Command.Result r) {
        if (r.status == Command.Status.REJECTED || r.status == Command.Status.EXPIRED) {
            memory.put("last_failure", r.command + ": " + r.reason, 4, 300_000L, r.at);
            if (r.command.priority >= Command.PRIORITY_USER) {
                episodes.record("FAILURE", "fail:" + r.command.action.replace('.', '-'),
                        r.command.action + " " + r.status.name().toLowerCase(Locale.US), 0.5f, r.at);
            }
        }
    }

    private Command.Status submit(Command c) {
        return supervisor.submit(c, c.createdAt);
    }

    private static String describe(Command c) {
        switch (c.action) {
            case "body.pose":
                return "stand".equals(c.value) ? "standing" : "doing " + c.value;
            case "body.scene":
                return "acting " + (c.value == null ? "a scene" : c.value);
            case "body.turn":
                return "turning around";
            default:
                return c.toString();
        }
    }

    private static String join(List<String> l) {
        if (l.size() == 1) {
            return l.get(0);
        }
        return String.join(", ", l.subList(0, l.size() - 1)) + " and " + l.get(l.size() - 1);
    }
}
