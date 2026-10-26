package ai.dvine.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Action selection and the digital spinal cord in one small class. One owner
 * per channel; a request either starts, waits its turn, preempts something
 * it outranks, or is rejected with a reason. Stale requests expire. A stop
 * cancels everything. Every outcome is reported, so the engine knows what
 * really happened instead of assuming success.
 */
public final class Supervisor {
    public interface Listener {
        void onResult(Command.Result result);
    }

    static final int QUEUE = 3;

    private final BodyPort port;
    private final Listener listener;
    private final Map<Command.Channel, Command> active = new EnumMap<Command.Channel, Command>(Command.Channel.class);
    private final Map<Command.Channel, Long> endsAt = new EnumMap<Command.Channel, Long>(Command.Channel.class);
    private final Map<Command.Channel, ArrayDeque<Command>> queued = new EnumMap<Command.Channel, ArrayDeque<Command>>(Command.Channel.class);
    private final ArrayDeque<Command.Result> history = new ArrayDeque<Command.Result>();
    private boolean bodyView;

    public Supervisor(BodyPort port, Listener listener) {
        this.port = port;
        this.listener = listener;
        for (Command.Channel c : Command.Channel.values()) {
            queued.put(c, new ArrayDeque<Command>());
        }
    }

    /** Submits a command. Returns its status right away (RUNNING, QUEUED, REJECTED or EXPIRED). */
    public Command.Status submit(Command c, long now) {
        String why = Skills.invalid(c);
        if (why != null) {
            return report(c, Command.Status.REJECTED, why, now);
        }
        if (now - c.createdAt > c.deadlineMs) {
            return report(c, Command.Status.EXPIRED, "stale", now);
        }
        if (c.action.equals("body.stop")) {
            stopAll(now);
            port.setPose("stand");
            return report(c, Command.Status.COMPLETED, null, now);
        }
        if (Skills.duration(c) == 0L) {
            execute(c); // a momentary action (switch view, redraw, turn round) overlays whatever is running
            return report(c, Command.Status.COMPLETED, null, now);
        }
        Command.Channel ch = Skills.channel(c.action);
        Command running = active.get(ch);
        // a request takes the channel when it outranks (or equals) what is running,
        // or when the user asks for something new over an interruptible action
        if (running == null || c.priority >= running.priority
                || Skills.interruptible(running) && c.priority >= Command.PRIORITY_USER) {
            if (running != null) {
                active.remove(ch);
                report(running, Command.Status.PREEMPTED, "by " + c, now);
            }
            start(ch, c, now);
            return Command.Status.RUNNING;
        }
        ArrayDeque<Command> q = queued.get(ch);
        if (q.size() >= QUEUE) {
            return report(c, Command.Status.REJECTED, "busy", now);
        }
        q.add(c);
        return Command.Status.QUEUED;
    }

    /** Finishes timed commands, starts queued ones and expires stale ones. Call at the behaviour rate (about 10 Hz). */
    public void tick(long now) {
        for (Command.Channel ch : Command.Channel.values()) {
            Command c = active.get(ch);
            Long end = endsAt.get(ch);
            if (c != null && end != null && end >= 0 && now >= end) {
                active.remove(ch);
                report(c, Command.Status.COMPLETED, null, now);
                if (ch == Command.Channel.BODY && c.action.equals("body.scene")) {
                    port.setPose("stand"); // the stage ends a scene standing; keep the engine's view in step
                }
            }
            ArrayDeque<Command> q = queued.get(ch);
            for (Iterator<Command> it = q.iterator(); it.hasNext(); ) {
                Command w = it.next();
                if (now - w.createdAt > w.deadlineMs) {
                    it.remove();
                    report(w, Command.Status.EXPIRED, "waited too long", now);
                }
            }
            if (active.get(ch) == null && !q.isEmpty()) {
                start(ch, q.poll(), now);
            }
        }
    }

    /** Cancels everything running or waiting, e.g. when the user says "stop". */
    public void stopAll(long now) {
        for (Command.Channel ch : Command.Channel.values()) {
            for (Command w : queued.get(ch)) {
                report(w, Command.Status.CANCELLED, "stop", now);
            }
            queued.get(ch).clear();
            Command c = active.remove(ch);
            if (c != null) {
                report(c, Command.Status.CANCELLED, "stop", now);
            }
        }
    }

    public Command active(Command.Channel ch) {
        return active.get(ch);
    }

    public boolean bodyView() {
        return bodyView;
    }

    /** The last outcomes, newest last (for the state snapshot and self-monitoring). */
    public List<Command.Result> history() {
        return new ArrayList<Command.Result>(history);
    }

    private void start(Command.Channel ch, Command c, long now) {
        execute(c);
        long d = Skills.duration(c);
        if (d == 0L) {
            report(c, Command.Status.COMPLETED, null, now);
            return;
        }
        active.put(ch, c);
        endsAt.put(ch, d < 0 ? -1L : now + d);
        report(c, Command.Status.RUNNING, null, now);
    }

    private void execute(Command c) {
        switch (c.action) {
            case "view.body":
                bodyView = true;
                port.setBodyMode(true);
                break;
            case "view.face":
                bodyView = false;
                Command body = active.remove(Command.Channel.BODY);
                if (body != null) {
                    report(body, Command.Status.CANCELLED, "face view", c.createdAt);
                }
                port.setBodyMode(false);
                break;
            case "body.pose":
                bodyView = true;
                port.setPose(c.value);
                break;
            case "body.scene":
                bodyView = true;
                port.playScene(c.poses, c.seconds);
                break;
            case "body.turn":
                bodyView = true;
                port.turnAround();
                break;
            case "body.redraw":
                port.redraw();
                break;
            case "face.gesture":
                port.gesture(c.value);
                break;
            case "draw.sketch":
                port.showSketch((ai.dvine.face.Sketch) c.payload);
                break;
            case "draw.object":
                port.showObject(c.value);
                break;
            case "draw.clear":
                port.clearSketch();
                break;
            default:
                break;
        }
    }

    private Command.Status report(Command c, Command.Status s, String reason, long now) {
        Command.Result r = new Command.Result(c, s, reason, now);
        history.add(r);
        while (history.size() > 32) {
            history.poll();
        }
        if (listener != null) {
            listener.onResult(r);
        }
        return s;
    }
}
