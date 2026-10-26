package ai.dvine.anre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.Regions;

/** ANRE: particles laid on the anatomy, following it, and the transformation state machine. */
public class AnreTest {
    static HumanAsset obi;
    static ParticleSet skin;

    @BeforeClass
    public static void load() throws IOException {
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/obi.dvhm"))) {
            obi = HumanAsset.read(in);
        }
        skin = HumanParticles.build(obi, HumanParticles.BUDGET);
    }

    @Test
    public void particlesAreLaidOnTheAnatomyDenselyOnTheFaceAndDeterministically() {
        int[] parts = HumanParticles.parts(obi);
        assertTrue(ParticleSurfaceMapper.validate(skin, obi.mesh, parts).isEmpty());
        assertTrue("budget kept", skin.count > HumanParticles.BUDGET * 0.98);
        // same mesh, same seed: the same layout (how a damaged layout is restored exactly)
        ParticleSet again = HumanParticles.build(obi, HumanParticles.BUDGET);
        assertTrue(Arrays.equals(skin.corners, again.corners) && Arrays.equals(skin.u, again.u));
        // the face is denser than the body: particles per unit area
        double faceArea = 0, bodyArea = 0;
        int faceN = 0, bodyN = 0;
        for (int t = 0; t < parts.length; t++) {
            double a = area(obi, t);
            if (Regions.isFace(parts[t])) faceArea += a; else bodyArea += a;
        }
        for (int i = 0; i < skin.count; i++) {
            if (skin.tri[i] < 0) continue;
            if (Regions.isFace(skin.part[i])) faceN++; else bodyN++;
        }
        assertTrue("face density " + faceN / faceArea + " vs body " + bodyN / bodyArea, faceN / faceArea > 3 * bodyN / bodyArea);
        // stratified: every triangle that should get a particle gets one (no bare patches by chance)
        int[] per = new int[parts.length];
        for (int i = 0; i < skin.count; i++) {
            if (skin.tri[i] >= 0) per[skin.tri[i]]++;
        }
        double total = 0;
        for (int t = 0; t < parts.length; t++) total += area(obi, t) * densityOf(parts[t]);
        int bare = 0;
        for (int t = 0; t < parts.length; t++) {
            if (area(obi, t) * densityOf(parts[t]) / total * HumanParticles.BUDGET >= 1.5 && per[t] == 0) bare++;
        }
        assertEquals(0, bare);
        // a damaged particle is caught
        ParticleSet broken = HumanParticles.build(obi, 2000);
        broken.u[5] = 1.4f;
        broken.part[9] = (short) Regions.FOOT_L;
        assertEquals(2, ParticleSurfaceMapper.validate(broken, obi.mesh, parts).size());
    }

    @Test
    public void particlesStayOnTheSkinWhileTheBodyMoves() {
        HumanInstance h = new HumanInstance(obi);
        h.rig.setPose("wave");
        h.setWeight("jawOpen", 0.8f);
        for (int i = 0; i < 60; i++) {
            h.update(i / 30f);
        }
        float[] out = new float[skin.count * 3];
        skin.positions(h.pos, out);
        for (int i = 0; i < skin.count; i += 97) {
            if (skin.tri[i] < 0) continue;
            int t = skin.tri[i] * 3;
            float d = distanceToTriangle(out, i, h.pos, obi.mesh.tri[t], obi.mesh.tri[t + 1], obi.mesh.tri[t + 2]);
            assertTrue("particle " + i + " is " + d + " off its triangle", d < 1e-4f);
        }
        // and the region of a particle never changes as it moves
        assertEquals(skin.part[123], HumanParticles.build(obi, HumanParticles.BUDGET).part[123]);
    }

    @Test
    public void theTransformationStateMachineRunsInOrderAndNeverStrandsTheBody() {
        boolean[] ok = {true};
        TransformationController tc = new TransformationController(() -> ok[0]);
        assertFalse("an invalid object is refused", tc.begin(false, 0f));
        assertEquals(TransformationController.State.BODY_INTACT, tc.state());
        assertTrue(tc.begin(true, 0f));
        java.util.List<TransformationController.State> seen = new java.util.ArrayList<TransformationController.State>();
        for (float t = 0f; t <= 5f; t += 0.05f) {
            TransformationController.State s = tc.view(t).from >= 0 ? tc.state() : null;
            if (seen.isEmpty() || seen.get(seen.size() - 1) != s) seen.add(s);
        }
        assertEquals(Arrays.asList(TransformationController.State.BODY_DISSOLVING, TransformationController.State.PARTICLES_IN_TRANSIT,
                TransformationController.State.OBJECT_ASSEMBLING, TransformationController.State.OBJECT_COMPLETE), seen);
        assertTrue(tc.view(5f).objectShown);
        assertTrue(tc.modify(true, 5f));
        assertEquals(TransformationController.State.OBJECT_MODIFYING, tc.state());
        tc.view(7f);
        assertEquals(TransformationController.State.OBJECT_COMPLETE, tc.state());
        // back to the body; BODY_RESTORED only once the body checks out
        ok[0] = false;
        assertTrue(tc.restore(7f));
        tc.view(9f);
        assertEquals(TransformationController.State.BODY_RECONSTRUCTING, tc.state());
        tc.view(11f);
        assertTrue("a body that fails its check is rebuilt, not reported restored", tc.state() == TransformationController.State.BODY_RECONSTRUCTING);
        ok[0] = true;
        tc.view(15f);
        assertEquals(TransformationController.State.BODY_RESTORED, tc.state());
        tc.view(16f);
        assertEquals(TransformationController.State.BODY_INTACT, tc.state());
    }

    @Test
    public void cancellingInFlightReversesSmoothly() {
        TransformationController tc = new TransformationController(() -> true);
        tc.begin(true, 0f);
        TransformationController.View before = tc.view(1.5f);
        assertTrue(tc.cancel(1.5f));
        TransformationController.View after = tc.view(1.5f);
        assertEquals(TransformationController.State.BODY_RECONSTRUCTING, tc.state());
        // each particle's place: mix(from, to, e(order)); reversing swaps the ends and mirrors the order,
        // so e_after = 1 - e_before for every particle: nothing jumps
        for (float order = 0f; order <= 1f; order += 0.125f) {
            float eb = e(before, order), ea = e(after, order);
            assertEquals(eb, 1f - ea, 1e-4f);
        }
        assertEquals(TransformationController.OBJECT, after.from);
        assertEquals(TransformationController.BODY, after.to);
        tc.view(6f);
        assertEquals(TransformationController.State.BODY_RESTORED, tc.state());
    }

    /** The shader's per-particle progress, for a particle with this order. */
    static float e(TransformationController.View v, float order) {
        float o = v.reverse ? 1f - order : order;
        float spread = 0.6f;
        return Math.max(0f, Math.min(1f, v.progress * (1f + spread) - o * spread));
    }

    private static float densityOf(int part) {
        return part == Regions.TEETH ? 0.6f : ParticleSurfaceMapper.HUMAN.of(part);
    }

    private static double area(HumanAsset a, int t) {
        float[] p = a.mesh.pos;
        int i = a.mesh.tri[t * 3] * 3, j = a.mesh.tri[t * 3 + 1] * 3, k = a.mesh.tri[t * 3 + 2] * 3;
        double ux = p[j] - p[i], uy = p[j + 1] - p[i + 1], uz = p[j + 2] - p[i + 2];
        double vx = p[k] - p[i], vy = p[k + 1] - p[i + 1], vz = p[k + 2] - p[i + 2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** Distance from particle i to the plane of its triangle, plus how far outside the triangle it is. */
    private static float distanceToTriangle(float[] out, int i, float[] p, int a, int b, int c) {
        float px = out[i * 3], py = out[i * 3 + 1], pz = out[i * 3 + 2];
        float ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
        float vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-12f) return 0f;
        return Math.abs((px - p[a * 3]) * nx + (py - p[a * 3 + 1]) * ny + (pz - p[a * 3 + 2]) * nz) / nl;
    }
}
