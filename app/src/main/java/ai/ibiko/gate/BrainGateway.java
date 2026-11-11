package ai.ibiko.gate;

import java.util.ArrayList;
import java.util.List;

/**
 * The one way a message is handled: every message the user sends (typed, spoken, read from a picture) goes through
 * here, and the routes are asked in a fixed order, each a named part of Ibiko (a command for the model, a post, a
 * file to make, a social search, Ivie's 3D canvas, Ibiko's coding agent, the three personas together, the model's
 * answer…). The first route that takes the message handles it; which one, and why, is written down, so the user (and
 * the tests) can see how each message was understood instead of several parts each deciding on their own. A route
 * that takes a message must finish it. Pure Java; not thread-safe (the screen thread uses it).
 */
public final class BrainGateway {
    /** One part of Ibiko: whether it takes the message, and if so it handles it. */
    public interface Route {
        boolean take(String text);
    }

    /** How a message was handled: the stage ("message" or "brain"), the route that took it, and the words. */
    public static final class Decision {
        public final String stage, route, text;
        public final long at;

        Decision(String stage, String route, String text, long at) {
            this.stage = stage;
            this.route = route;
            this.text = text;
            this.at = at;
        }

        @Override
        public String toString() {
            return stage + " → " + route;
        }
    }

    /** Told of each decision (for the steps the user sees, or a log). */
    public interface Listener {
        void decided(Decision d);
    }

    static final int KEEP = 40;

    private final String stage;
    private final List<String> names = new ArrayList<String>();
    private final List<Route> routes = new ArrayList<Route>();
    private final List<Decision> recent;
    private Listener listener;

    public BrainGateway(String stage) {
        this(stage, new ArrayList<Decision>());
    }

    /** A second stage sharing the first's record, so one list shows a message's whole way through. */
    public BrainGateway(String stage, BrainGateway sharing) {
        this(stage, sharing.recent);
        this.listener = sharing.listener;
    }

    private BrainGateway(String stage, List<Decision> recent) {
        this.stage = stage;
        this.recent = recent;
    }

    /** Adds a route after those already added (the order is the order they are asked in). */
    public BrainGateway route(String name, Route r) {
        if (names.contains(name)) {
            throw new IllegalArgumentException("two routes called " + name);
        }
        names.add(name);
        routes.add(r);
        return this;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** The routes in the order they are asked. */
    public List<String> order() {
        return new ArrayList<String>(names);
    }

    /** Handles the message: the name of the route that took it, or null when none did. */
    public String handle(String text, long now) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).take(text)) {
                Decision d = new Decision(stage, names.get(i), text, now);
                recent.add(d);
                while (recent.size() > KEEP) {
                    recent.remove(0);
                }
                Listener l = listener;
                if (l != null) {
                    l.decided(d);
                }
                return d.route;
            }
        }
        return null;
    }

    /** The latest decisions, oldest first (both stages when they share a record). */
    public List<Decision> recent() {
        return new ArrayList<Decision>(recent);
    }

    public Decision last() {
        return recent.isEmpty() ? null : recent.get(recent.size() - 1);
    }
}
