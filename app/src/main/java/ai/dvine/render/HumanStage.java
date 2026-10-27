package ai.dvine.render;

import java.io.InputStream;
import java.util.List;

import ai.dvine.face.FaceRig;
import ai.dvine.face.Look;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.Regions;
import ai.dvine.human.bake.AssetBuilder;
import ai.dvine.human.face.FaceDrive;
import ai.dvine.human.inspect.Inspector;
import ai.dvine.human.rig.HumanRig;
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

        /** A picture from the assets as {width, height, ARGB pixels…}, or null (no decoder here). */
        default int[] picture(String name) {
            return null;
        }
    }

    // each persona's character sheet and its calibration: the body is painted from it once it stands
    private final ai.dvine.human.wrap.SheetWrap[] sheets = new ai.dvine.human.wrap.SheetWrap[2];
    private final int[][] sheetPixels = new int[2][];

    /** Whether the bodies are painted from their character sheets (the default look). */
    public volatile boolean sheetLook = true;

    public final WireRenderer renderer;
    public final Camera camera = new Camera();
    private final HumanInstance[] inst = new HumanInstance[2];
    private final FaceDrive[] face = {new FaceDrive(), new FaceDrive()};
    private final Inspector[] inspector = new Inspector[2];
    private final float[][] shifted = new float[2][], shiftedNor = new float[2][];
    /** Where each persona stands in the shared world (one on stage, or both). */
    public final ai.dvine.human.scene.DualScene scene = new ai.dvine.human.scene.DualScene();
    private final boolean[] walkShown = new boolean[2];
    private float lastFrame = -1f;
    /** ANRE: each persona's particle skin, and the state machine that moves those particles. */
    private final ai.dvine.anre.ParticleSet[] particles = new ai.dvine.anre.ParticleSet[2];
    private final ai.dvine.anre.TransformationController[] morph = new ai.dvine.anre.TransformationController[2];
    /** The object the particles build (shared by both personas), and the download bar. */
    public final ai.dvine.create.ObjectSession objects = new ai.dvine.create.ObjectSession();
    /** Everything shown besides the bodies (the download bar, drawings, thoughts, captions), made of particles. */
    public final ai.dvine.render.field.ParticleDisplay display = new ai.dvine.render.field.ParticleDisplay();
    private final float[] headNow = {0f, 2.6f, 0f, 1.4f};
    private final ai.dvine.create.ObjectSession bar = new ai.dvine.create.ObjectSession();
    private boolean showingBar;
    private float barShown = -1f;
    private int objectSlot = -1;
    /** Mode B per persona: on, which layers, which parts (null = every part of the shown layers). */
    private final boolean[] xray = new boolean[2];
    private final boolean[][] xrayLayers = {new boolean[4], new boolean[4]};
    private final boolean[][] xrayParts = new boolean[2][];
    private String pose = "stand";
    private float time;
    // gaze toward a touched part for a moment
    private final float[] touchAt = new float[3];
    private float touchUntil = -1f;

    /** The floor the body stands on (body units), where objects stand too. */
    public static final float FLOOR = -4.2f;

    public HumanStage(WireRenderer renderer) {
        this.renderer = renderer;
    }

    /** Draw the bodies as cartoons (Ghibli style): a bigger head and bigger eyes on the same rig. */
    public volatile boolean cartoon;
    private final float[][] cartoonPos = new float[2][], headWeight = new float[2][];

    /**
     * Cartoon proportions for this frame: the head (by its skinning weight, so the neck blends) grows about the neck
     * joint, the eyes grow about their own centres. Positions only; a uniform scale keeps the normals.
     */
    private float[] cartoon(int slot, HumanInstance h) {
        ai.dvine.human.mesh.TriMesh m = h.asset.mesh;
        int n = m.vertexCount();
        if (headWeight[slot] == null || headWeight[slot].length != n) {
            float[] w = new float[n];
            int head = ai.dvine.face.nano.DotBody.HEAD;
            for (int v = 0; v < n; v++) {
                w[v] = ((m.bone0[v] & 0xff) == head ? m.weight0[v] : 0f) + ((m.bone1[v] & 0xff) == head ? 1f - m.weight0[v] : 0f);
            }
            headWeight[slot] = w;
            cartoonPos[slot] = new float[h.pos.length];
        }
        float[] w = headWeight[slot], out = cartoonPos[slot], p = h.pos;
        int neck = ai.dvine.face.nano.DotBody.NECK * 3;
        float nx = h.rig.joint[neck], ny = h.rig.joint[neck + 1], nz = h.rig.joint[neck + 2];
        final float headGrow = 0.2f, eyeGrow = 0.1f;
        for (int v = 0; v < n && v * 3 + 2 < p.length; v++) {
            float k = 1f + headGrow * w[v];
            out[v * 3] = nx + (p[v * 3] - nx) * k;
            out[v * 3 + 1] = ny + (p[v * 3 + 1] - ny) * k;
            out[v * 3 + 2] = nz + (p[v * 3 + 2] - nz) * k;
        }
        System.arraycopy(p, n * 3, out, n * 3, p.length - n * 3);
        int[] er = h.asset.eyeRange;
        for (int e = 0; e < 2; e++) {
            int from = er[e * 2], count = er[e * 2 + 1];
            float cx = 0f, cy = 0f, cz = 0f;
            for (int v = from; v < from + count; v++) {
                cx += out[v * 3];
                cy += out[v * 3 + 1];
                cz += out[v * 3 + 2];
            }
            if (count == 0) {
                continue;
            }
            cx /= count;
            cy /= count;
            cz /= count;
            for (int v = from; v < from + count; v++) {
                out[v * 3] = cx + (out[v * 3] - cx) * (1f + eyeGrow);
                out[v * 3 + 1] = cy + (out[v * 3 + 1] - cy) * (1f + eyeGrow);
                out[v * 3 + 2] = cz + (out[v * 3 + 2] - cz) * (1f + eyeGrow);
            }
        }
        return out;
    }

    /** Why a persona's 3D body could not be loaded (null while nothing failed). */
    public volatile String loadFault;

    /** Loads both personas on a background thread; {@code done} runs (on that thread) per installed persona. */
    public void load(final AssetSource source, final Runnable done) {
        Thread t = new Thread(() -> {
            for (int slot = 0; slot < 2; slot++) {
                loadSheet(source, slot);
                HumanAsset a = null;
                try (InputStream in = source.open(slot == 0 ? "human/obi.dvhm" : "human/ivie.dvhm")) {
                    a = HumanAsset.read(in);
                } catch (java.io.IOException | RuntimeException damaged) {
                    // the shipped asset is missing or damaged: rebuild it from the recipe and validate it
                    try {
                        a = AssetBuilder.build(slot == 1);
                    } catch (RuntimeException failed) {
                        a = null;
                        loadFault = (slot == 0 ? "Obi's" : "Ivie's") + " 3D body failed to load (" + damaged + "; rebuilding it failed: " + failed + ")";
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
        wrap(slot, h);
        if (approve || inspector[slot] == null) {
            inspector[slot] = new Inspector(a);
        }
        shifted[slot] = new float[h.pos.length];
        shiftedNor[slot] = new float[h.nor.length];
        particles[slot] = ai.dvine.anre.HumanParticles.build(a, ai.dvine.anre.HumanParticles.BUDGET);
        final int sl = slot;
        morph[slot] = new ai.dvine.anre.TransformationController(() -> bodyIntact(sl));
        renderer.setAsset(slot, a, particles[slot], ai.dvine.anre.InnerParticles.build(a, ai.dvine.anre.InnerParticles.BUDGET));
        // Mode C: the body assembles from a cloud of its own particles, head first
        morph[slot].assemble(time);
    }

    /** Reads both personas' sheets (for a caller that installs the bodies itself). */
    public void loadSheets(AssetSource source) {
        loadSheet(source, 0);
        loadSheet(source, 1);
    }

    /** Reads a persona's sheet and its calibration (assets/looks/obi.json …); a missing one leaves the body as it was. */
    private void loadSheet(AssetSource source, int slot) {
        String who = slot == 0 ? "obi" : "ivie";
        try (InputStream in = source.open("looks/" + who + ".json")) {
            ai.dvine.human.wrap.SheetWrap w = ai.dvine.human.wrap.SheetWrap.parse(readAll(in));
            int[] px = w.sheet == null ? null : source.picture(w.sheet);
            synchronized (this) {
                sheets[slot] = px == null ? null : w;
                sheetPixels[slot] = px;
            }
        } catch (java.io.IOException | RuntimeException none) {
            // no sheet: the body keeps its own painted look
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Paints a body from its sheet, from how it stands now (just installed, in its rest stance). */
    private void wrap(int slot, HumanInstance h) {
        ai.dvine.human.wrap.SheetWrap w = sheets[slot];
        int[] px = sheetPixels[slot];
        if (w == null || px == null || !sheetLook) {
            renderer.setWrap(slot, null, null, 0, 0, null);
            return;
        }
        ai.dvine.human.wrap.SheetWrap.Result r = w.compute(h);
        renderer.setWrap(slot, r.uv, r.weight, px[0], px[1], java.util.Arrays.copyOfRange(px, 2, px.length));
    }

    /** Paints both bodies from their sheets (true) or from their own look (false). */
    public synchronized void setSheetLook(boolean on) {
        if (on == sheetLook) {
            return;
        }
        sheetLook = on;
        for (int slot = 0; slot < 2; slot++) {
            if (inst[slot] != null) {
                if (on) {
                    // the wrap is computed in the rest stance: a fresh instance of the same body stands in it
                    HumanInstance rest = new HumanInstance(inst[slot].asset);
                    rest.rig.setPose(pose);
                    rest.update(time);
                    wrap(slot, rest);
                } else {
                    renderer.setWrap(slot, null, null, 0, 0, null);
                }
            }
        }
    }

    /** The body checks out: geometry, rig, and every particle on its surface (the gate for BODY_RESTORED). */
    private boolean bodyIntact(int slot) {
        HumanInstance h = inst[slot];
        Inspector in = inspector[slot];
        if (h == null || in == null || particles[slot] == null) {
            return false;
        }
        boolean particlesOk = ai.dvine.anre.ParticleSurfaceMapper.validate(particles[slot], h.asset.mesh,
                ai.dvine.anre.HumanParticles.parts(h.asset)).isEmpty();
        if (!particlesOk) {
            // a damaged layout is re-made exactly from the validated mesh
            particles[slot] = ai.dvine.anre.HumanParticles.build(h.asset, ai.dvine.anre.HumanParticles.BUDGET);
            renderer.setAsset(slot, h.asset, particles[slot]);
        }
        return in.inspect(h).isEmpty();
    }

    // ------------------------------------------------------------ Mode B: the anatomical view

    /**
     * Shows the inside: "skeleton", "muscles", "organs", "vessels", "all", or
     * one structure or region ("heart", "brain", "lungs", "chest", "head",
     * "belly", "arms", "legs"); "off" covers it again. Returns the part names
     * shown (empty when off or unknown).
     */
    public synchronized java.util.List<String> anatomy(boolean ivie, String what) {
        int slot = ivie ? 1 : 0;
        java.util.List<String> shown = new java.util.ArrayList<String>();
        HumanInstance h = inst[slot];
        if (h == null || h.asset.inner == null || what == null || what.equals("off")) {
            xray[slot] = false;
            return shown;
        }
        HumanAsset a = h.asset;
        boolean[] layers = new boolean[4];
        boolean[] parts = null;
        switch (what) {
            case "skeleton": layers[0] = true; break;
            case "muscles": layers[1] = true; break;
            case "organs": layers[2] = true; layers[3] = true; break;
            case "vessels": layers[3] = true; break;
            case "all": java.util.Arrays.fill(layers, true); break;
            default: {
                java.util.Arrays.fill(layers, true);
                parts = new boolean[a.innerNames.length];
                String[] keys = focus(what);
                for (int i = 0; i < a.innerNames.length; i++) {
                    for (String k : keys) {
                        if (a.innerNames[i].startsWith(k)) {
                            parts[i] = true;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < a.innerNames.length; i++) {
            if (layers[a.innerLayer[i]] && (parts == null || parts[i])) {
                shown.add(a.innerNames[i]);
            }
        }
        if (shown.isEmpty()) {
            xray[slot] = false;
            return shown;
        }
        xray[slot] = true;
        xrayLayers[slot] = layers;
        xrayParts[slot] = parts;
        return shown;
    }

    private static String[] focus(String what) {
        switch (what) {
            case "chest": return new String[]{"ribs", "sternum", "lung", "heart", "aorta", "vena cava", "clavicle", "spine"};
            case "head": return new String[]{"skull", "jaw", "brain"};
            case "belly": return new String[]{"liver", "stomach", "kidney", "intestines", "abdominals", "spine"};
            case "lungs": return new String[]{"lung"};
            case "kidneys": return new String[]{"kidney"};
            case "arms": return new String[]{"humerus", "radius", "ulna", "hand bones", "finger bones", "biceps", "triceps", "deltoid", "forearm"};
            case "legs": return new String[]{"femur", "patella", "tibia", "fibula", "foot bones", "quadriceps", "hamstrings", "calf", "femoral"};
            default: return new String[]{what};
        }
    }

    public synchronized boolean anatomyShown(boolean ivie) {
        return xray[ivie ? 1 : 0];
    }

    // ------------------------------------------------------------ transformation (ANRE)

    /**
     * The visible persona's particles leave the body and build this object (a
     * validated model only). If the particles already are an object, they
     * re-form into the new one. Returns false when it can't start.
     */
    public synchronized boolean becomeObject(boolean ivie, ai.dvine.create.ObjectModel m) {
        int slot = ivie ? 1 : 0;
        if (inst[slot] == null || m == null || !m.valid()) {
            return false;
        }
        upload(slot, m);
        objectSlot = slot;
        ai.dvine.anre.TransformationController tc = morph[slot];
        ai.dvine.anre.TransformationController.State st = tc.state();
        if (st == ai.dvine.anre.TransformationController.State.OBJECT_COMPLETE
                || st == ai.dvine.anre.TransformationController.State.OBJECT_MODIFYING) {
            return tc.modify(true, time);
        }
        return tc.begin(true, time);
    }

    /** The object (or its view) changed: the particles morph from the old version to the new one. */
    public synchronized boolean objectChanged(boolean ivie) {
        int slot = ivie ? 1 : 0;
        ai.dvine.create.ObjectModel m = (showingBar ? bar : objects).model();
        if (m == null || objectSlot != slot) {
            return false;
        }
        upload(slot, m);
        return morph[slot].modify(true, time);
    }

    private void upload(int slot, ai.dvine.create.ObjectModel m) {
        java.util.List<Integer> triPart = new java.util.ArrayList<Integer>();
        ai.dvine.human.mesh.TriMesh merged = m.merged(triPart);
        renderer.setObject(slot, merged.pos, merged.nor, merged.tri, ai.dvine.create.ObjectParticles.targets(m, particles[slot]));
    }

    /** "Return to your normal form": the object dissolves and the body rebuilds, head first. */
    public synchronized boolean restoreBody(boolean ivie) {
        int slot = ivie ? 1 : 0;
        return morph[slot] != null && morph[slot].restore(time);
    }

    public synchronized ai.dvine.anre.TransformationController.State morphState(boolean ivie) {
        ai.dvine.anre.TransformationController tc = morph[ivie ? 1 : 0];
        return tc == null ? null : tc.state();
    }

    /** Whether the particles are (becoming) an object, so the stage shows the whole scene. */
    public synchronized boolean objectMode(boolean ivie) {
        ai.dvine.anre.TransformationController tc = morph[ivie ? 1 : 0];
        return tc != null && tc.objectMode();
    }

    /**
     * A progress bar made of the body's particles (a download, an install):
     * {@code fraction} 0..1 fills it; null or a negative value ends it and the
     * body comes back.
     */
    public synchronized void progress(boolean ivie, float fraction) {
        int slot = ivie ? 1 : 0;
        if (inst[slot] == null) {
            return;
        }
        if (fraction < 0f) {
            if (showingBar) {
                showingBar = false;
                barShown = -1f;
                morph[slot].restore(time);
            }
            return;
        }
        float f = Math.max(0f, Math.min(1f, fraction));
        if (showingBar && Math.abs(f - barShown) < 0.03f && f < 1f) {
            return; // re-form only every few percent
        }
        ai.dvine.create.ObjectSpec spec = ai.dvine.create.ObjectSpec.of("progress").with("progress", f, true);
        ai.dvine.create.ObjectModel m = showingBar ? bar.modify(spec) : bar.create(spec, null);
        if (m == null) {
            return;
        }
        bar.spin = false;
        bar.yaw = 0f;
        bar.pitch = 0f;
        barShown = f;
        if (!showingBar) {
            if (morph[slot].objectMode()) {
                return; // already showing a design: don't take it over
            }
            showingBar = true;
            upload(slot, m);
            objectSlot = slot;
            morph[slot].begin(true, time);
        } else {
            upload(slot, m);
            morph[slot].modify(true, time);
        }
    }

    /**
     * Height of the persona's head centre (half-way from chin to crown, as posed now) in
     * camera units, for framing the face close-up; the dot head's height until loaded.
     */
    public synchronized float headCameraY(boolean ivie) {
        HumanInstance h = inst[ivie ? 1 : 0];
        int crown = h == null ? -1 : h.asset.landmark("crown"), chin = h == null ? -1 : h.asset.landmark("chin");
        if (crown < 0 || chin < 0) {
            return ai.dvine.face.nano.DotBody.FACE_Y;
        }
        float y = (h.pos[crown * 3 + 1] + h.pos[chin * 3 + 1]) * 0.5f;
        return (y - HumanInstance.CENTRE) * HumanInstance.OUT;
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

    /**
     * A hand gesture (by its emoji key, see {@link ai.dvine.face.Gestures}) made with the persona's
     * own 3D hands: fingers shaped one by one, the hand brought up beside the face, moving as the
     * gesture does. False when the key is unknown or the body isn't loaded.
     */
    public synchronized boolean gesture(boolean ivie, String key, float seconds) {
        HumanInstance h = inst[ivie ? 1 : 0];
        ai.dvine.face.Gestures.Gesture g = key == null ? null : ai.dvine.face.Gestures.get(key);
        if (h == null || g == null) {
            return false;
        }
        int move;
        switch (g.motion) {
            case WAVE:
                move = HumanRig.MOVE_WAVE;
                break;
            case SHAKE:
                move = HumanRig.MOVE_SHAKE;
                break;
            case TAP:
                move = HumanRig.MOVE_TAP;
                break;
            case CLAP:
                move = HumanRig.MOVE_CLAP;
                break;
            case SCRIBBLE:
                move = HumanRig.MOVE_SCRIBBLE;
                break;
            default:
                move = HumanRig.MOVE_NONE;
                break;
        }
        h.rig.gesture(g.pose.curl, g.twoHands, g.x, g.y, move, aimOf(g.key), seconds);
        if (g.key.startsWith("👍") || g.key.startsWith("👏") || g.key.startsWith("🙌")) {
            face[ivie ? 1 : 0].setExpression("smile", seconds, time);
        }
        return true;
    }

    /** How each gesture holds the hand (see {@link HumanRig#AIM_UP}). */
    static int aimOf(String key) {
        String k = key == null ? "" : key;
        for (String f : new String[]{"🫵", "👊", "🫳", "🤝", "🤛", "🤜", "👈", "👉", "🫱", "🫲", "🫸", "🫷"}) {
            if (k.startsWith(f)) {
                return HumanRig.AIM_FORWARD;
            }
        }
        for (String f : new String[]{"🫴", "🤲"}) {
            if (k.startsWith(f)) {
                return HumanRig.AIM_PALM_UP;
            }
        }
        if (k.startsWith("👍")) {
            return HumanRig.AIM_THUMB;
        }
        if (k.startsWith("👎")) {
            return HumanRig.AIM_THUMB_DOWN;
        }
        for (String f : new String[]{"🙏", "👏", "🫶"}) {
            if (k.startsWith(f)) {
                return HumanRig.AIM_TOGETHER;
            }
        }
        return HumanRig.AIM_UP;
    }

    public synchronized void setExpression(boolean ivie, String name) {
        face[ivie ? 1 : 0].setExpression(name, 3.5f, time);
    }

    public synchronized void speechWord(boolean ivie, String word) {
        face[ivie ? 1 : 0].speech.word(word, time);
    }

    /** Both personas side by side (the old demo), or back to the one on screen. */
    public synchronized void setDemo(boolean on) {
        scene.arrange(on ? ai.dvine.human.scene.DualScene.SIDE : ai.dvine.human.scene.DualScene.SINGLE);
    }

    /** Arranges the shared scene: single, side, meet or walk (see {@link ai.dvine.human.scene.DualScene}). */
    public synchronized boolean arrange(String name) {
        return scene.arrange(name);
    }

    /** True while both personas share the stage. */
    public synchronized boolean demo() {
        return scene.both();
    }

    /** Middle and half width (body units) of where the personas on stage are going, for framing. */
    public synchronized float[] sceneSpan() {
        return scene.span();
    }

    // ------------------------------------------------------------ frame

    /**
     * Updates the visible persona (or both in the demo) for {@code t} seconds
     * and submits it to the renderer. The camera must be set first.
     */
    public synchronized void frame(float t, boolean ivie, FaceRig rig, boolean bodyView, boolean talking, float alpha) {
        time = t;
        float dt = lastFrame < 0f ? 0f : Math.min(0.05f, t - lastFrame);
        scene.setFocus(ivie);
        scene.step(lastFrame < 0f ? 0f : t - lastFrame);
        lastFrame = t;
        boolean demo = scene.both();
        for (int slot = 0; slot < 2; slot++) {
            HumanInstance h = inst[slot];
            if (h == null) {
                continue;
            }
            ai.dvine.human.scene.DualScene.Actor actor = scene.actor[slot];
            boolean visible = demo ? actor.present : (slot == 1) == ivie;
            if (!visible) {
                renderer.submit(slot, h.pos, h.nor, 0f, 0f, 0f, 0f, 1f, false);
                continue;
            }
            h.rig.setTurntable(bodyView && !demo);
            h.rig.setTalking(talking && (slot == 1) == ivie && !actor.walking);
            // walking in the shared scene plays the walk cycle, then returns to the asked-for pose
            if (actor.walking != walkShown[slot]) {
                walkShown[slot] = actor.walking;
                h.rig.setPose(actor.walking ? "walk" : pose);
            }
            // look at the user (and follow the face rig's glances), at a part that was just touched,
            // or, facing each other, at the other persona's face
            float headY = h.rig.joint[ai.dvine.face.nano.DotBody.HEAD * 3 + 1] + 0.3f;
            HumanInstance other = inst[1 - slot];
            if (t < touchUntil && (slot == 1) == ivie) {
                // a glance down at the spot: mostly the eyes and a small nod, so the face stays in view
                float[] l = h.toRig(touchAt[0], touchAt[1], touchAt[2]);
                float hy = h.rig.joint[ai.dvine.face.nano.DotBody.HEAD * 3 + 1];
                h.rig.lookAt(l[0], hy + (l[1] - hy) * 0.45f, l[2] + 1.2f, 0.5f);
            } else if (demo && other != null && scene.actor[1 - slot].present && !actor.walking
                    && (ai.dvine.human.scene.DualScene.MEET.equals(scene.arrangement()) || (slot == 1) != ivie)) {
                // face to face they look at each other; side by side the listener turns to the one speaking
                ai.dvine.human.scene.DualScene.Actor oa = scene.actor[1 - slot];
                float oy = other.rig.joint[ai.dvine.face.nano.DotBody.HEAD * 3 + 1] + 0.3f;
                float ok = ai.dvine.human.scene.DualScene.HEIGHT[1 - slot];
                float[] l = toLocal(actor, slot, oa.x, FLOOR + (oy - FLOOR) * ok, oa.z + 0.4f);
                h.rig.lookAt(l[0], l[1], l[2], 1f);
            } else if (actor.walking) {
                float[] l = toLocal(actor, slot, actor.tx, headY, actor.tz + 3f);
                h.rig.lookAt(l[0], headY, Math.max(2f, l[2]), 0.6f);
            } else {
                float[] l = demo ? toLocal(actor, slot, rig.lookX.value * 2.5f, headY + rig.lookY.value * 1.5f, 12f)
                        : new float[]{rig.lookX.value * 2.5f, headY + rig.lookY.value * 1.5f, 12f};
                h.rig.lookAt(l[0], l[1], l[2], 1f);
            }
            face[slot].apply(h, rig, t);
            h.update(t);
            float[] p = h.pos, nor = h.nor;
            if (cartoon) {
                p = cartoon(slot, h);
            }
            if (demo) {
                // the persona's own place in the shared world: its height, turned by its facing, moved to its root
                double yr = Math.toRadians(actor.yaw);
                float k = ai.dvine.human.scene.DualScene.HEIGHT[slot];
                float c = (float) Math.cos(yr), sn = (float) Math.sin(yr);
                float[] s = shifted[slot], sn3 = shiftedNor[slot], nr = h.nor;
                for (int i = 0; i < p.length; i += 3) {
                    float x = p[i] * k, z = p[i + 2] * k;
                    s[i] = c * x + sn * z + actor.x;
                    s[i + 1] = FLOOR + (p[i + 1] - FLOOR) * k;
                    s[i + 2] = -sn * x + c * z + actor.z;
                    float nx = nr[i], nz = nr[i + 2];
                    sn3[i] = c * nx + sn * nz;
                    sn3[i + 1] = nr[i + 1];
                    sn3[i + 2] = -sn * nx + c * nz;
                }
                p = s;
                nor = sn3;
            }
            float r = slot == 0 ? 0f : 0.996f, g = slot == 0 ? 0.9f : 0.70f, b = slot == 0 ? 1f : 0f;
            float lod = bodyView ? 0.7f : 1.35f; // particle size: bigger in the close-up
            ai.dvine.anre.TransformationController.View v = morph[slot] == null ? null : morph[slot].view(t);
            if (v != null) {
                ai.dvine.create.ObjectSession os = showingBar && slot == objectSlot ? bar : objects;
                float[] om = os.model() != null && slot == objectSlot ? os.matrix(FLOOR, t) : null;
                renderer.transition(slot, v.from, v.to, v.progress, v.byObject, v.reverse, om,
                        os == objects ? objects.selected() : -1f, v.bodyFill, new float[]{0f, 0.2f, 0f, 0.9f});
            }
            renderer.submit(slot, p, nor, r, g, b, demo ? 1f : alpha, lod, true);
            boolean showInside = xray[slot] && !demo && (v == null || !morph[slot].objectMode());
            if (showInside) {
                renderer.submitInner(slot, h.innerPos, h.innerNor);
            }
            renderer.anatomy(slot, showInside, xrayLayers[slot], xrayParts[slot]);
            if ((slot == 1) == ivie) {
                head(h, p);
            }
        }
        display.frame(dt, renderer, headNow, FLOOR, bodyView, renderer.aspect());
    }

    /** The shown persona's crown (x, y, z) and chin height, as drawn this frame, for placing the display. */
    private void head(HumanInstance h, float[] p) {
        int crown = h.asset.landmark("crown"), chin = h.asset.landmark("chin");
        if (crown < 0 || chin < 0 || crown * 3 + 2 >= p.length) {
            return;
        }
        headNow[0] = p[crown * 3];
        headNow[1] = p[crown * 3 + 1];
        headNow[2] = p[crown * 3 + 2];
        headNow[3] = p[chin * 3 + 1];
    }

    // ------------------------------------------------------------ touch

    /** The body part under a screen point (ray cast on the current mesh), or null. */
    public synchronized BodyTouch pick(float sx, float sy, boolean ivie, BodyTouch.Kind kind, int count, long now,
                                       long duration) {
        HumanInstance h = inst[ivie ? 1 : 0];
        if (h == null || objectMode(ivie)) {
            return null;
        }
        float[] ray = camera.ray(sx, sy);
        if (scene.both()) {
            // into the persona's own frame (its place and facing in the shared world)
            int sl = ivie ? 1 : 0;
            ai.dvine.human.scene.DualScene.Actor a = scene.actor[sl];
            float[] o = toLocal(a, sl, ray[0], ray[1], ray[2]), d = turnLocal(a, ray[3], ray[5]);
            ray = new float[]{o[0], o[1], o[2], d[0], ray[4], d[1]}; // a uniform scale keeps directions
        }
        HumanInstance.Hit hit = h.pick(ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
        if (hit == null) {
            return null;
        }
        int bone = h.asset.mesh.bone0[h.asset.mesh.tri[hit.triangle * 3]] & 0xff;
        return new BodyTouch(hit.region, kind, count, now, duration, hit.point, bone, h.toBoneLocal(bone, hit.point));
    }

    /** A world point in a persona's own frame (undoes its root position, facing and height). */
    private static float[] toLocal(ai.dvine.human.scene.DualScene.Actor a, int slot, float x, float y, float z) {
        float k = ai.dvine.human.scene.DualScene.HEIGHT[slot];
        float[] d = turnLocal(a, x - a.x, z - a.z);
        return new float[]{d[0] / k, FLOOR + (y - FLOOR) / k, d[1] / k};
    }

    /** A world direction (x, z) in a persona's own frame. */
    private static float[] turnLocal(ai.dvine.human.scene.DualScene.Actor a, float x, float z) {
        double yr = Math.toRadians(a.yaw);
        float c = (float) Math.cos(yr), s = (float) Math.sin(yr);
        return new float[]{c * x - s * z, s * x + c * z};
    }

    /** The part of the object under a screen point (selected and returned), or null. */
    public synchronized ai.dvine.create.Component pickObject(float sx, float sy, boolean ivie) {
        if (!objectMode(ivie) || objects.model() == null || showingBar) {
            return null;
        }
        float[] ray = camera.ray(sx, sy);
        int id = objects.pick(objects.matrix(FLOOR, time), ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
        objects.select(id);
        return id < 0 ? null : objects.model().component(id);
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
            // the reach places the wrist: just below and in front of the spot, so the palm (not the fingertips) covers it
            float[] at = h.toRig(touch.world[0], touch.world[1], touch.world[2]);
            boolean front = r == Regions.CHEST || r == Regions.BELLY;
            float sx = Math.signum(at[0]);
            float[] n = front ? new float[]{0f, 0f, 1f} : new float[]{0.8f * sx, 0f, 0.6f}; // out of the skin
            h.rig.reachTo(side, at[0] + n[0] * 0.12f, at[1] - 0.32f + n[1] * 0.12f, at[2] + n[2] * 0.12f + 0.06f, 1.8f);
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
            return loadFault != null ? loadFault + "." : "My 3D body is still loading.";
        }
        if (renderer.fault != null) {
            return "My 3D body is hidden: " + renderer.fault + " (" + renderer.glErrors + " errors).";
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
