package ai.dvine.dvine1;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * DVINE 1's learning as a training file: one chat a line (JSONL, the format language-model trainers read), from the
 * answers the pilot confirmed, the facts it read (each asked by the focus formulas and answered by the sentence
 * formula) and its dictionary. A small model trained on it can then run on the device through the app's llama.cpp
 * engine, the fastest way to run a language model on a phone.
 */
public final class Training {
    private Training() {
    }

    /** Writes the training file; how many examples it holds. */
    public static int write(OutputStream out, Knowledge knowledge, Facts facts, Lexicon lexicon) throws IOException {
        int n = 0;
        for (Knowledge.Fact f : knowledge.confirmed()) {
            n += line(out, f.question, f.answer);
        }
        for (Grammar.Triple t : facts.all()) {
            String q = Facts.question(t);
            String a = q == null ? null : facts.answerFrom(q, t);
            if (a != null) {
                n += line(out, q, a);
            }
        }
        for (Lexicon.Word w : lexicon.words()) {
            n += line(out, "What does " + w.word + " mean?", w.said());
        }
        return n;
    }

    private static int line(OutputStream out, String user, String assistant) throws IOException {
        String l = "{\"messages\":[{\"role\":\"user\",\"content\":" + quote(user) + "},{\"role\":\"assistant\",\"content\":"
                + quote(assistant) + "}]}\n";
        out.write(l.getBytes(StandardCharsets.UTF_8));
        return 1;
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
