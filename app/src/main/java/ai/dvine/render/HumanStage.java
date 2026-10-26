package ai.dvine.render;

import java.io.InputStream;
import java.util.List;

import ai.dvine.face.FaceRig;
import ai.dvine.face.Look;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.Regions;
import ai.dvine.human.build.AssetBuilder;
import ai.dvine.human.face.FaceDrive;
import ai.dvine.human.inspect.Inspector;
import ai.dvine.human.rig.Skeleton;
import ai.dvine.human.touch.BodyTouch;

/**
 * Glue between the stage and the 3D humans: loads the approved assets (or
 * rebuilds one whose checksum fails), drives each persona's rig and face
 * every frame, hands the result to the {@link WireRenderer}, picks touched
 * body parts and plays the body's reaction, shows both personas in the
 * demo, and runs self-inspection. Android-free apart from the asset streams
 * it is given.
 */
public final class HumanStage {
    public interface AssetSource {
        InputStream open(String name) throws java.io.IOException;
    }

    public final WireRenderer renderer;
    public final Camera camera = new Camera();
    private final HumanInstance[] inst = new HumanInstance[2];
    private final FaceDrive[] face = {new FaceDrive(), new FaceDrive()};
    private final Inspector[] inspector = new Inspector[2];
    private final float[][] shifted = new float[2][];
    private String pose = "stand";
    private boolean demo;
    private float time;
    // gaze toward a touched part for a moment
    private final float[] touchAt = new float[3];
    private float touchUntil = -1f;

    public HumanStage(WireRenderer renderer) {
        this.renderer = renderer;
    }

    /** Loads both personas on a background thread; {@code done} runs (on that thread) per installed persona. */
    public void load(final AssetSource source, final Runnable done) {
        Thread t = new Thread(() -> {
            for (int slot = 0; slot < 2; slot++) {
                HumanAsset a = null;
                try (InputStream in = source.open(slot == 0 ? "human/obi.dvhm" : "human/ivie.dvhm")) {
                    a = HumanAsset.read(in);
                } catch (java.io.IOException | RuntimeException damaged) {
                    // the shipped asset is missing or damaged: rebuild it from the recipe and validate it
                    try {
                        a = AssetBuilder.build(slot == 1);
                    } catch (RuntimeException failed) {
                        a = null;
                    }
                }
                if (a != null) {
                    install(slot, a, true);
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }, "dvine-human");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /**
     * Installs an asset for a slot (0 Obi, 1 Ivie). {@code approve} makes it the
     * reference for later inspections (the shipped asset, or a look the user asked for).
     */
    public synchronized void install(int slot, HumanAsset a, boolean approve) {
        HumanInstance h = new HumanInstance(a);
        h.rig.setPose(pose);
        h.update(time);
        inst[slot] = h;
        if (approve || inspector[slot] == null) {
            inspector[slot] = new Inspector(a);
        }
        shifted[slot] = new float[h.pos.length];
        renderer.setAsset(slot, a);
    }

    public synchronized boolean ready(boolean ivie) {
        return inst[ivie ? 1 : 0] != null;
    }

    // ------------------------------------------------------------ requests

    public synchronized void setPose(String p) {
        pose = p;
        for (HumanInstance h : inst) {
            if (h != null) {
                h.rig.setPose(p);
            }
        }
    }

    public synchronized void turnAround() {
        for (HumanInstance h : inst) {
            if (h != null) {
                h.rig.turnAround();
            }
        }
    }

    public synchronized void setExpression(boolean ivie, String name) {
        face[ivie ? 1 : 0].setExpression(name, 3.5f, time);
    }

    public synchronized void speechWord(boolean ivie, String word) {
        face[ivie ? 1 : 0].speech.word(word, time);
    }

    public synchronized void setDemo(boolean on) {
        demo = on;
    }

    public synchronized boolean demo() {
        return demo;
    }

    // ------------------------------------------------------------ frame

    /**
     * Updates the visible persona (or both in the demo) for {@code t} seconds
     * and submits it to the renderer. The camera must be set first.
     */
    public synchronized void frame(float t, boolean ivie, FaceRig rig, boolean bodyView, boolean talking, float alpha) {
        time = t;
        for (int slot = 0; slot < 2; slot++) {
            HumanInstance h = inst[slot];
            if (h == null) {
                continue;
            }
            boolean visible = demo || (slot == 1) == ivie;
            if (!visible) {
                renderer.submit(slot, h.pos, h.nor, 0f, 0f, 0f, 0f, 1f, false);
                continue;
            }
            h.rig.setTurntable(bodyView);
            h.rig.setTalking(talking && (slot == 1) == ivie);
            // look at the user (and follow the face rig's glances), or at a part that was just touched
            float headY = h.rig.joint[ai.dvine.face.nano.DotBody.HEAD * 3 + 1] + 0.3f;
            if (t < touchUntil && (slot == 1) == ivie) {
                h.rig.lookAt(touchAt[0], touchAt[1], touchAt[2] + 0.4f, 1f);
            } else {
                h.rig.lookAt(rig.lookX.value * 2.5f, headY + rig.lookY.value * 1.5f, 12f, 1f);
            }
            face[slot].apply(h, rig, t);
            h.update(t);
            float[] p = h.pos;
            if (demo) {
                float dx = slot == 0 ? -1.5f : 1.5f;
                float[] s = shifted[slot];
                for (int i = 0; i < p.length; i += 3) {
                    s[i] = p[i] + dx;
                    s[i + 1] = p[i + 1];
                    s[i + 2] = p[i + 2];
                }
                p = s;
            }
            float r = slot == 0 ? 0f : 1f, g = slot == 0 ? 0.9f : 0.70f, b = slot == 0 ? 1f : 0f;
            float lod = bodyView ? 0.6f : 1f;
            renderer.submit(slot, p, h.nor, r, g, b, demo ? 1f : alpha, lod, true);
        }
    }

    // ------------------------------------------------------------ touch

    /** The body part under a screen point (ray cast on the current mesh), or null. */
    public synchronized BodyTouch pick(float sx, float sy, boolean ivie, BodyTouch.Kind kind, int count, long now,
                                       long duration) {
        HumanInstance h = inst[ivie ? 1 : 0];
        if (h == null || demo) {
            return null;
        }
        float[] ray = camera.ray(sx, sy);
        HumanInstance.Hit hit = h.pick(ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
        if (hit == null) {
            return null;
        }
        int bone = h.asset.mesh.bone0[h.asset.mesh.tri[hit.triangle * 3]] & 0xff;
        return new BodyTouch(hit.region, kind, count, now, duration, hit.point, bone, h.toBoneLocal(bone, hit.point));
    }

    /** The body reacts to a touch: it looks at the spot, and a hand comes to a touched shoulder or arm. */
    public synchronized void react(BodyTouch touch, boolean ivie) {
        HumanInstance h = inst[ivie ? 1 : 0];
        if (h == null) {
            return;
        }
        System.arraycopy(touch.world, 0, touchAt, 0, 3);
        touchUntil = time + 2.2f;
        int r = touch.region;
        boolean leftSide = Regions.key(r) != null && Regions.key(r).endsWith(".left");
        if (r == Regions.SHOULDER_L || r == Regions.SHOULDER_R || r == Regions.UPPER_ARM_L || r == Regions.UPPER_ARM_R
                || r == Regions.FOREARM_L || r == Regions.FOREARM_R || r == Regions.CHEST || r == Regions.BELLY) {
            // the opposite hand comes to the spot (a hand on the chest uses the right hand)
            int side = leftSide ? -1 : 1;
            h.rig.reachTo(side, touch.world[0], touch.world[1], touch.world[2] + 0.08f, 1.8f);
        }
        if (touch.kind == BodyTouch.Kind.REPEAT && touch.count >= 3) {
            face[ivie ? 1 : 0].setExpression("surprise", 2f, time);
        } else if (touch.kind == BodyTouch.Kind.HOLD) {
            face[ivie ? 1 : 0].setExpression("smile", 2.5f, time);
        }
    }

    // ------------------------------------------------------------ inspection

    /** Runs the self-inspection on the visible persona, repairing locally when needed. */
    public synchronized String inspect(boolean ivie) {
        HumanInstance h = inst[ivie ? 1 : 0];
        Inspector in = inspector[ivie ? 1 : 0];
        if (h == null || in == null) {
            return "My 3D body is still loading.";
        }
        in.renderErrors = renderer.glErrors;
        List<String> problems = in.inspect(h);
        if (problems.isEmpty()) {
            return "All my geometry checks passed: " + h.asset.mesh.triangleCount() + " triangles, "
                    + Skeleton.COUNT + " bones, every body part mapped.";
        }
        List<String> left = in.repair(h, time);
        return left.isEmpty() ? "I found " + problems.get(0) + " and repaired it." : "I found a problem I couldn't repair: " + left.get(0) + ".";
    }

    /** Rebuilds one persona from a new look the user approved; runs on its own thread, installs when valid. */
    public void rebuild(final boolean ivie, final Look look) {
        final Inspector in;
        synchronized (this) {
            in = inspector[ivie ? 1 : 0];
        }
        if (in == null) {
            return;
        }
        Thread t = new Thread(() -> {
            HumanAsset fresh = in.reconstruct(look, new AssetBuilder.Budget(), true);
            if (fresh != null) {
                install(ivie ? 1 : 0, fresh, true);
            }
        }, "dvine-rebuild");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public synchronized HumanInstance instance(boolean ivie) {
        return inst[ivie ? 1 : 0];
    }
}
