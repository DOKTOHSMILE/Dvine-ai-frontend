package ai.dvine.create;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.brain.MiniJson;

/**
 * Saves designs so they can be reopened and edited later without starting
 * again: the spec (the source of truth: the geometry is rebuilt from it,
 * identically), its version, where its references came from, and what
 * validation said; plus an OBJ export of the geometry for other apps.
 */
public final class ObjectStore {
    private final File dir;

    public ObjectStore(File dir) {
        this.dir = dir;
    }

    public File save(ObjectModel m) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        Map<String, Object> j = new LinkedHashMap<String, Object>();
        j.put("format", 1);
        j.put("title", m.title);
        j.put("version", m.version);
        j.put("savedAt", System.currentTimeMillis());
        j.put("spec", MiniJson.parse(m.spec.toJson()));
        List<Object> prov = new ArrayList<Object>();
        for (Provenance p : m.provenance) {
            Map<String, Object> e = new LinkedHashMap<String, Object>();
            e.put("kind", p.kind);
            e.put("source", p.source);
            e.put("retrievedAt", p.retrievedAt);
            e.put("license", p.license);
            e.put("note", p.note);
            prov.add(e);
        }
        j.put("provenance", prov);
        j.put("problems", new ArrayList<Object>(m.problems));
        List<Object> hidden = new ArrayList<Object>();
        List<Object> deleted = new ArrayList<Object>();
        for (Component c : m.components) {
            if (!c.retained) {
                deleted.add(c.name);
            } else if (!c.visible) {
                hidden.add(c.name);
            }
        }
        j.put("hidden", hidden);
        j.put("deleted", deleted);
        File f = new File(dir, name(m.spec.category) + ".json");
        Files.write(f.toPath(), MiniJson.write(j).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** The latest saved design of a category ("house"), or of any kind ("design"), rebuilt; null when none. */
    public ObjectModel open(String what) throws IOException {
        File f = null;
        if (ProceduralModelingEngine.supports(what)) {
            f = new File(dir, name(what) + ".json");
        } else {
            File[] all = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (all != null) {
                for (File c : all) {
                    if (f == null || c.lastModified() > f.lastModified()) {
                        f = c;
                    }
                }
            }
        }
        if (f == null || !f.isFile()) {
            return null;
        }
        Map<String, Object> j = MiniJson.obj(MiniJson.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
        ObjectSpec spec = ObjectSpec.fromJson(MiniJson.write(j.get("spec")));
        int version = (int) MiniJson.num(j.get("version"), 1);
        List<Provenance> prov = new ArrayList<Provenance>();
        for (Object o : MiniJson.list(j.get("provenance"))) {
            Map<String, Object> e = MiniJson.obj(o);
            prov.add(new Provenance(MiniJson.str(e.get("kind"), "default"), MiniJson.str(e.get("source"), ""),
                    (long) MiniJson.num(e.get("retrievedAt"), 0), MiniJson.str(e.get("license"), "unknown"), MiniJson.str(e.get("note"), "")));
        }
        ObjectModel built = ProceduralModelingEngine.build(spec, version, prov);
        for (String name : MiniJson.strings(j.get("hidden"))) {
            Component c = named(built, name);
            if (c != null) {
                c.visible = false;
                c.retained = true;
            }
        }
        for (String name : MiniJson.strings(j.get("deleted"))) {
            Component c = named(built, name);
            if (c != null) {
                c.visible = false;
                c.retained = false;
            }
        }
        return built;
    }

    private static Component named(ObjectModel m, String name) {
        if (m == null) {
            return null;
        }
        for (Component c : m.components) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** Writes the visible geometry as Wavefront OBJ, one group per part. */
    public File exportObj(ObjectModel m) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        StringBuilder sb = new StringBuilder("# DVINE ").append(m.title).append(", version ").append(m.version)
                .append(" (concept model, not engineering-verified; metres, y up)\n");
        int base = 1;
        for (Component c : m.components) {
            if (!c.visible) {
                continue;
            }
            sb.append("g ").append(c.name.replace(' ', '_')).append('\n');
            for (int i = 0; i < c.mesh.pos.length; i += 3) {
                sb.append(String.format(Locale.US, "v %.4f %.4f %.4f%n", c.mesh.pos[i], c.mesh.pos[i + 1], c.mesh.pos[i + 2]));
            }
            for (int t = 0; t < c.mesh.tri.length; t += 3) {
                sb.append("f ").append(c.mesh.tri[t] + base).append(' ').append(c.mesh.tri[t + 1] + base).append(' ')
                        .append(c.mesh.tri[t + 2] + base).append('\n');
            }
            base += c.mesh.pos.length / 3;
        }
        File f = new File(dir, name(m.spec.category) + ".obj");
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static String name(String category) {
        return category.replaceAll("[^a-z0-9]", "_");
    }
}
