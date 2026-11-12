package ai.ibiko.llm;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the device's model evolves: its 4-bit weights stay as they were downloaded (they can't be trained on a phone),
 * and a small trained adapter (LoRA, a few to a few dozen megabytes) is laid over them when the model loads. The
 * adapter is trained, off the device, on what Ibiko itself knows to be right: the answers the pilot confirmed and the
 * answers the models gave that were kept ({@link #trainingSet}). A new adapter replaces the old one, which is kept
 * once so it can be put back ({@link #install}, {@link #rollBack}); one that doesn't fit the model is set aside when
 * it is loaded, and the model runs as it was.
 * <ul>
 * <li>The adapter for {@code qwen3-1.7b-q4_0.gguf} is {@code qwen3-1.7b-q4_0.adapter.gguf}, beside it.</li>
 * <li>It is trained for the model it sits beside (the same weights, any 4-bit layout of them).</li>
 * <li>How to train one: {@code docs/TRAINING.md}.</li>
 * </ul>
 * Pure Java.
 */
public final class Adapters {
    private Adapters() {
    }

    /** How strongly the adapter is laid over the weights: as it was trained. */
    public static final float STRENGTH = 1.0f;

    private static String base(File model) {
        String n = model.getName();
        return n.endsWith(".gguf") ? n.substring(0, n.length() - 5) : n;
    }

    /** The adapter for a model file. */
    public static File of(File model) {
        return new File(model.getParentFile(), base(model) + ".adapter.gguf");
    }

    /** The adapter it replaced, kept once. */
    public static File previous(File model) {
        return new File(model.getParentFile(), base(model) + ".adapter.prev.gguf");
    }

    /**
     * Puts a newly trained adapter in place for a model (used from the next load); the one in use is kept as the
     * previous. False when the file isn't a GGUF.
     */
    public static boolean install(File model, File trained) throws IOException {
        if (!isGguf(trained)) {
            return false;
        }
        File now = of(model);
        if (now.isFile()) {
            java.nio.file.Files.move(now.toPath(), previous(model).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        java.nio.file.Files.copy(trained.toPath(), now.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Puts the previous adapter back (or none, when there was none). True when something changed. */
    public static boolean rollBack(File model) throws IOException {
        File now = of(model), prev = previous(model);
        if (prev.isFile()) {
            java.nio.file.Files.move(prev.toPath(), now.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
        return now.isFile() && now.delete();
    }

    static boolean isGguf(File f) {
        byte[] h = new byte[4];
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            return in.read(h) == 4 && h[0] == 'G' && h[1] == 'G' && h[2] == 'U' && h[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * What Ibiko knows to be right, as training examples in the chat format training tools read (one
     * {"messages": [...]} a line): every answer the pilot confirmed, then every model answer the learning log kept.
     * Returns how many were written.
     */
    public static int trainingSet(File filesDir, ai.ibiko.learn.LearningLog log, OutputStream out) throws IOException {
        int n = 0;
        ai.ibiko.ibiko1.Knowledge k = ai.ibiko.ibiko1.Store.knowledge(new File(filesDir, "brain"));
        for (ai.ibiko.ibiko1.Knowledge.Fact f : k.confirmed()) {
            List<Object> msgs = new ArrayList<Object>();
            msgs.add(turn("system", "You are Ibiko. Answer as the pilot confirmed."));
            msgs.add(turn("user", f.question.replace('\n', ' ')));
            msgs.add(turn("assistant", f.answer));
            Map<String, Object> example = new LinkedHashMap<String, Object>();
            example.put("messages", msgs);
            out.write((ai.ibiko.brain.MiniJson.write(example) + "\n").getBytes(StandardCharsets.UTF_8));
            n++;
        }
        if (log != null) {
            n += log.exportForTraining(out);
        }
        return n;
    }

    private static Map<String, Object> turn(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
