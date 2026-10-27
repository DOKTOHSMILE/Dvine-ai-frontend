package ai.dvine.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;

import org.junit.Test;

/**
 * The real bridge end to end (Java, JNI, llama.cpp, the model) on a desktop build of the same native code. Runs only
 * when DVINE_NATIVE_LIB (the desktop libdvine_llm.so) and DVINE_MODEL (the GGUF file) are set.
 */
public class NativeBridgeTest {
    @Test
    public void answersAsAPersonaAndStreams() throws Exception {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        // a spec matching the file on disk, so LocalModel treats it as installed
        ModelSpec spec = new ModelSpec("test", f.getName(), "", f.length(), "", "Apache 2.0");
        LocalModel m = new LocalModel(f.getParentFile(), spec, true);
        assertTrue(m.load(4));
        StringBuilder streamed = new StringBuilder();
        int[] pieces = {0};
        String answer = m.reply(PersonaPrompt.system(false, "Val"), "In one sentence, what is a helicopter?", 120, text -> {
            pieces[0]++;
            streamed.append(text);
            return true;
        });
        System.out.println("NATIVE answer: " + answer + " (" + pieces[0] + " pieces)");
        assertTrue(answer != null && answer.toLowerCase().contains("helicopter") && !answer.contains("<think>"));
        assertTrue(pieces[0] > 3 && streamed.toString().trim().equals(answer));
        // the follow-up sees the conversation
        String follow = m.reply(PersonaPrompt.system(false, "Val"), "And what makes it fly?", 120, null);
        System.out.println("NATIVE follow-up: " + follow);
        assertTrue(follow != null && follow.length() > 10);
        // a non-ASCII reply survives the trip (whole UTF-8 characters only)
        String naija = m.reply(PersonaPrompt.system(true, null), "Reply with only this word: café", 20, null);
        System.out.println("NATIVE utf8: " + naija);
        assertTrue(naija != null && naija.toLowerCase().contains("café"));
        // stopping from the sink ends the reply early
        String stopped = m.reply(PersonaPrompt.system(true, null), "Tell me a long story about a river.", 200, text -> false);
        assertTrue(stopped != null && stopped.length() < 80);
    }

    @Test
    public void drawsAPictureInShapes() throws Exception {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "Apache 2.0"), true);
        assertTrue(m.load(4));
        String out = System.getenv("DVINE_DRAW_OUT");
        for (String subject : new String[]{"a helicopter", "a lighthouse", "a cat", "a car", "a rocket", "a sailing boat"}) {
            long t0 = System.nanoTime();
            String shapes = m.task(DrawPrompt.system(), DrawPrompt.user(subject), 360, null);
            System.out.println("NATIVE draw took " + (System.nanoTime() - t0) / 1000000 + " ms " + m.problem());
            ai.dvine.render.field.SolidSpec d = DrawPrompt.read(shapes);
            System.out.println("NATIVE draw " + subject + ": " + d.parts.size() + " parts\n" + shapes);
            assertTrue(subject, d.parts.size() >= 3);
            if (out != null) {
                java.nio.file.Files.write(new File(out, subject.replace(' ', '_') + ".txt").toPath(), shapes.getBytes("UTF-8"));
            }
        }
        // drawing doesn't enter the conversation
        assertTrue(m.historySize() == 0);
    }

    @Test
    public void trustsWhatTheUserTaughtOverItsTraining() throws Exception {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "Apache 2.0"), true);
        assertTrue(m.load(4));
        String sys = PersonaPrompt.system(false, "Val", "Tuesday 27 October 2026");
        String before = m.reply(sys, "Who is the president of the United States? Just the name.", 60, null);
        System.out.println("NATIVE before: " + before);
        m.forget();
        String context = PersonaPrompt.context(java.util.Arrays.asList("the president of the united states is Donald Trump, since January 2025"), null);
        String after = m.reply(sys, "Who is the president of the United States? Just the name.", context, 60, null);
        System.out.println("NATIVE after: " + after);
        assertTrue(after, after.contains("Trump"));
        String year = m.reply(sys, "What year is it?", null, 40, null);
        System.out.println("NATIVE year: " + year);
        assertTrue(year, year.contains("2026"));
        assertTrue(m.historySize() == 4); // the context isn't remembered, the questions are
    }
}
