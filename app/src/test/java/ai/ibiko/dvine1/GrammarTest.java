package ai.ibiko.dvine1;

import ai.ibiko.dvine1.formulas.Commands;
import ai.ibiko.dvine1.formulas.Grammar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;




/** DVINE 1's language formulas: reading intent and parts, reading facts from text, and saying answers as sentences. */
public class GrammarTest {
    @Test
    public void intentAndWhatAQuestionWants() {
        assertEquals(Grammar.Intent.GREETING, Grammar.parse("Good morning").intent);
        assertEquals(Grammar.Intent.THANKS, Grammar.parse("thank you so much").intent);
        assertEquals(Grammar.Intent.COMMAND, Grammar.parse("Tell me about volcanoes").intent);
        assertEquals("volcanoes", Grammar.parse("Tell me about volcanoes").subject);
        assertEquals(Grammar.Intent.STATEMENT, Grammar.parse("I like rice").intent);
        assertEquals(Grammar.Wants.PERSON, Grammar.parse("Who invented the telephone?").wants);
        assertEquals(Grammar.Wants.TIME, Grammar.parse("What year did the war end?").wants);
        assertEquals(Grammar.Wants.NUMBER, Grammar.parse("How many states does Nigeria have?").wants);
        assertEquals(Grammar.Wants.YESNO, Grammar.parse("Is Lagos in Nigeria?").wants);
    }

    @Test
    public void theFocusFormulas() {
        Grammar.Parse f3 = Grammar.parse("Who invented the telephone?");
        assertEquals("invent", f3.verb);
        assertEquals("telephone", f3.object);
        Grammar.Parse f4 = Grammar.parse("When was Microsoft founded?");
        assertEquals("Microsoft", f4.subject);
        assertEquals("found", f4.verb);
        assertTrue(f4.passive);
        Grammar.Parse f2 = Grammar.parse("When did Nigeria gain independence?");
        assertEquals("Nigeria", f2.subject);
        assertEquals("gain", f2.verb);
        assertEquals("independence", f2.object);
        Grammar.Parse f1 = Grammar.parse("What is a quasar?");
        assertEquals("quasar", f1.subject);
        assertEquals("be", f1.verb);
        assertEquals("found", Grammar.base("founded"));
        assertEquals("write", Grammar.base("wrote"));
        assertEquals("create", Grammar.base("created"));
        assertEquals("study", Grammar.base("studied"));
    }

    @Test
    public void factsAreReadFromText() {
        List<Grammar.Triple> t = Grammar.facts("Microsoft Corporation is an American technology company. It was founded by Bill Gates and"
                + " Paul Allen on April 4, 1975. Alexander Graham Bell invented the telephone in 1876. Abuja is the capital of Nigeria."
                + " Chinua Achebe was born on 16 November 1930 in Ogidi.", "Microsoft");
        String all = t.toString();
        assertTrue(all, all.contains("(Microsoft Corporation | is | an American technology company)"));
        assertTrue(all, all.contains("(Microsoft | found by | Bill Gates and Paul Allen)"));
        assertTrue(all, all.contains("(Microsoft | found in | April 4)") || all.contains("found in"));
        assertTrue(all, all.contains("(telephone | invent by | Alexander Graham Bell)"));
        assertTrue(all, all.contains("(Abuja | capital of | Nigeria)"));
        assertTrue(all, all.contains("(Chinua Achebe | born in | Ogidi)"));
    }

    @Test
    public void answersAreSaidAsSentences() {
        Facts f = new Facts(null);
        f.learn("Alexander Graham Bell invented the telephone in 1876. Microsoft was founded by Bill Gates and Paul Allen in 1975."
                + " Abuja is the capital of Nigeria. A quasar is an extremely luminous active galactic nucleus."
                + " Chinua Achebe was born on 16 November 1930 in Ogidi.", "", 2, 0L); // an encyclopedia: two sightings
        assertEquals("Alexander Graham Bell invented the telephone.", f.answer("Who invented the telephone?"));
        assertEquals("Microsoft was founded in 1975.", f.answer("When was Microsoft founded?"));
        assertEquals("Abuja is the capital of Nigeria.", f.answer("What is Abuja?"));
        assertEquals("A quasar is an extremely luminous active galactic nucleus.", f.answer("What is a quasar?"));
        assertEquals("Chinua Achebe was born in Ogidi.", f.answer("Where was Chinua Achebe born?"));
        assertEquals("Chinua Achebe was born on 16 November 1930.", f.answer("When was Chinua Achebe born?"));
        assertNull("nothing read answers it", f.answer("Who invented the radio?"));
    }

    @Test
    public void wordsAreLearned() {
        assertEquals("ubiquitous", Lexicon.asked("What does ubiquitous mean?"));
        assertEquals("serendipity", Lexicon.asked("define serendipity"));
        assertEquals("ephemeral", Lexicon.asked("What is the meaning of ephemeral?"));
        assertNull(Lexicon.asked("What is a pig?"));
        Lexicon l = new Lexicon(null);
        assertTrue(l.learn("ubiquitous", "{\"en\":[{\"partOfSpeech\":\"Adjective\",\"language\":\"English\",\"definitions\":"
                + "[{\"definition\":\"Being <a href=\\\"x\\\">everywhere</a> at once.\"}]}]}"));
        assertEquals("Ubiquitous (adjective): Being everywhere at once.", l.get("Ubiquitous").said());
    }
}
