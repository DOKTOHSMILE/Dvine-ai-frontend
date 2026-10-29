package ai.dvine.ops.pipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.dvine.llm.ModelSpec;

/** What this build can actually do. Unavailable tools are listed as unavailable. */
public final class CapabilityRegistry {
    public static final class Item {
        public final String id;
        public final boolean available;
        public final String honest;

        public Item(String id, boolean available, String honest) {
            this.id = id;
            this.available = available;
            this.honest = honest;
        }
    }

    public List<Item> offline() {
        List<Item> all = new ArrayList<Item>();
        all.add(new Item("clock", true, "I can tell you the date and the time."));
        all.add(new Item("notes", true, "I can answer from my notes."));
        all.add(new Item("qwen", false, "I can use " + ModelSpec.WEIGHTS + " when that file is on this phone."));
        all.add(new Item("picker", true, "I can open a file you choose."));
        all.add(new Item("photograph", true, "I can show a photograph as particles in its own colours."));
        all.add(new Item("task", true, "I can keep a task going while the microphone is open."));
        all.add(new Item("approximation", true, "A 3D shape I build from one picture is an approximation, not a measurement."));
        all.add(new Item("ocr", false, "I cannot read words out of a picture."));
        all.add(new Item("photogrammetry", false, "I cannot build a measured 3D model from one photo, and several photos still are not a reconstruction."));
        all.add(new Item("news", false, "I do not make up the news."));
        all.add(new Item("image-generation", false, "I do not have an image-generation model in this build."));
        all.add(new Item("rig", false, "I do not have a rigged body."));
        return Collections.unmodifiableList(all);
    }

    public Item get(String id) {
        for (Item item : offline()) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    public String speak() {
        return "Offline, I can tell you the date and the time, answer from my notes, and use "
                + ai.dvine.llm.ModelSpec.WEIGHTS + " when that file is on this phone. I can open a file you choose,"
                + " and show a photograph as particles in its own colours. I can keep a task going while the microphone is open."
                + " I cannot read words out of a picture, I cannot build a measured 3D model from one photo, and I do not make up the news.";
    }
}
