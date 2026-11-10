package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class InterpreterTest {
    @Test
    public void englishGoesStraightThrough() {
        String[] english = {"What is the capital of Ghana?", "Please add a comment to this code", "Make a plan for my shop",
                "go and fit the new door, then come back", "I am done", "hello", "Write a poem about the sea",
                "explain the la la land film", "We go to church on Sunday", "I no longer need it", "How far is Lagos from Abuja?", "e.g. the pour over coffee", "```\nje suis\n``` what does this print?"};
        for (String e : english) {
            assertNull(e, Interpreter.language(e));
        }
    }

    @Test
    public void pidginIsRecognised() {
        String[] pidgin = {"Wetin be the capital of Ghana?", "Abeg help me write letter to my landlord",
                "How far, wetin dey happen?", "I no sabi how to cook jollof, abeg teach me", "Una don chop?",
                "Wahala dey o", "E don do, make we go"};
        for (String p : pidgin) {
            assertEquals(p, Interpreter.Language.PIDGIN, Interpreter.language(p));
        }
    }

    @Test
    public void yorubaAndFrenchAreRecognised() {
        assertEquals(Interpreter.Language.YORUBA, Interpreter.language("Bawo ni, ṣe dáadáa ni?"));
        assertEquals(Interpreter.Language.YORUBA, Interpreter.language("Kini oruko re?"));
        assertEquals(Interpreter.Language.YORUBA, Interpreter.language("Ẹ káàrọ̀, mo fẹ́ jẹun"));
        assertEquals(Interpreter.Language.FRENCH, Interpreter.language("Bonjour, comment ça va ?"));
        assertEquals(Interpreter.Language.FRENCH, Interpreter.language("Je veux apprendre à coder une application"));
        assertEquals(Interpreter.Language.FRENCH, Interpreter.language("Pourquoi le ciel est bleu ?"));
    }

    @Test
    public void pidginGlossaryHelpsTheModel() {
        String g = Interpreter.glossary("Wetin dey happen? Abeg no vex");
        assertTrue(g, g.contains("wetin = what"));
        assertTrue(g, g.contains("abeg = please"));
        assertTrue(Interpreter.toEnglishPrompt(Interpreter.Language.PIDGIN, "abeg").contains("abeg = please"));
        assertEquals("", Interpreter.glossary("plain English here"));
    }

    @Test
    public void translatesInAndBackKeepingCode() {
        final List<String> systems = new ArrayList<String>();
        Interpreter.Brain brain = (system, text, max) -> {
            systems.add(system);
            if (system.contains("into clear, natural English")) {
                return "<think>hmm</think>Translation: \"What is the capital of Ghana?\"";
            }
            // the model returns the placeholder untouched, as told
            return text.replace("Run", "Run am").replace("The capital is Accra.", "Na Accra be the capital.");
        };
        assertEquals("What is the capital of Ghana?",
                Interpreter.toEnglish(brain, Interpreter.Language.PIDGIN, "Wetin be the capital of Ghana?"));
        String reply = "The capital is Accra. Run ```\nls -la\n``` and see https://example.com/a.";
        String back = Interpreter.fromEnglish(brain, Interpreter.Language.PIDGIN, reply);
        assertEquals("Na Accra be the capital. Run am ```\nls -la\n``` and see https://example.com/a.", back);
        assertTrue(systems.get(1).contains("Nigerian Pidgin"));
    }

    @Test
    public void aBadTranslationFallsBack() {
        // answered instead of translating: far too long
        Interpreter.Brain chatty = (s, t, m) -> "Sure! " + "This is a long answer. ".repeat(40);
        assertNull(Interpreter.toEnglish(chatty, Interpreter.Language.FRENCH, "Bonjour"));
        // lost the code block
        Interpreter.Brain lossy = (s, t, m) -> "Oya run the command";
        assertNull(Interpreter.fromEnglish(lossy, Interpreter.Language.PIDGIN, "Run `ls` now"));
        // gave the English back
        assertNull(Interpreter.fromEnglish((s, t, m) -> t + ".", Interpreter.Language.PIDGIN, "The food is ready"));
        // failed
        assertNull(Interpreter.fromEnglish((s, t, m) -> null, Interpreter.Language.YORUBA, "Hello"));
    }

    @Test
    public void protectAndRestore() {
        List<String> kept = new ArrayList<String>();
        String p = Interpreter.protect("a `x` b https://e.com c", kept);
        assertEquals("a [[K1]] b [[K2]] c", p);
        assertEquals("a `x` b https://e.com c", Interpreter.restore(p, kept));
    }
}
