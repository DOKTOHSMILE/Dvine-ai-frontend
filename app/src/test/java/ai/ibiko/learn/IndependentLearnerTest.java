package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.Facts;
import ai.ibiko.ibiko1.formulas.Confidence;
import ai.ibiko.ibiko1.formulas.Grammar;
import ai.ibiko.ibiko1.formulas.PartsOfSpeech;
import ai.ibiko.ibiko1.formulas.Score;
import ai.ibiko.ibiko1.formulas.Shapes;
import ai.ibiko.ibiko1.learner.Learner;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** PLAN.md T7: the independent learner and its formulas. */
public class IndependentLearnerTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void accuracyIsSmoothed() {
        assertEquals(0.5, Score.accuracy(0, 0), 1e-9);
        assertEquals(2.0 / 3, Score.accuracy(1, 1), 1e-9);
        assertEquals(1.0 / 3, Score.accuracy(0, 1), 1e-9);
        assertEquals(23, Score.of(0, 0, 0, 0, 0, 0)); // 100 * 0.45 * 0.5, rounded
        assertTrue(Score.of(10, 10, 500, 20, 90, 100) > Score.of(0, 10, 0, 0, 0, 100));
        assertTrue(Score.said(3, 4, 1, 1, 1, 1).contains("3 of 4 answers right"));
    }

    @Test
    public void strengthRulesAreTheConfidenceFormula() {
        assertEquals(1, Confidence.first(1));
        assertEquals(2, Confidence.first(2));
        assertEquals(5, Confidence.up(5));
        assertEquals(0, Confidence.down(1));
        assertEquals(3, Confidence.decayed(4, 0, 31 * DAY));
        assertEquals(4, Confidence.decayed(4, 0, 29 * DAY));
        assertTrue(Confidence.tie(3, 2));
        assertFalse(Confidence.tie(4, 2));
    }

    @Test
    public void aFactSeenOnceDoesNotAnswerAndARepeatDoes() {
        Facts f = new Facts(null);
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        assertNull("seen once: not sure enough", f.answer("What is Abuja?"));
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        assertEquals("Abuja is the capital of Nigeria.", f.answer("What is Abuja?"));
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        f.learn("Abuja is the capital of Nigeria.", null, 1, 0);
        assertEquals("capped at 5", 5, f.strength("Abuja", "capital of", "Nigeria"));
    }

    @Test
    public void aContradictionWeakensAndDropsTheOldFact() {
        Facts f = new Facts(null);
        f.learn("Chinua Achebe was born in Ogidi.", null, 2, 0);
        assertEquals(2, f.strength("Chinua Achebe", "born in", "Ogidi"));
        f.learn("Chinua Achebe was born in Enugu.", null, 1, 0);
        assertEquals("2 - 2 = 0: dropped", 0, f.strength("Chinua Achebe", "born in", "Ogidi"));
        assertEquals(1, f.strength("Chinua Achebe", "born in", "Enugu"));
    }

    @Test
    public void twoEquallyStrongAnswersAskWhichIsRight() {
        Facts f = new Facts(null);
        f.see(new Grammar.Triple("telephone", "invent by", "Bell", "invented"), 3, 0);
        f.see(new Grammar.Triple("telephone", "invent by", "Meucci", "invented"), 5, 0); // Bell falls 3 → 1
        assertEquals("Meucci invented the telephone.", f.answer("Who invented the telephone?"));
        f.see(new Grammar.Triple("telephone", "invent by", "Bell", "invented"), 1, 0); // Meucci 5 → 3, Bell 1 → 2
        String a = f.answer("Who invented the telephone?");
        assertTrue(a, a.endsWith("Which is right?") && a.contains("Bell") && a.contains("Meucci"));
    }

    @Test
    public void reviewDecaysOldFacts() {
        Facts f = new Facts(null);
        f.learn("Abuja is the capital of Nigeria.", null, 2, 0);
        assertEquals(1, f.review(31 * DAY));
        assertEquals(1, f.strength("Abuja", "capital of", "Nigeria"));
        assertEquals("its clock restarted", 0, f.review(32 * DAY));
        f.review(62 * DAY);
        assertEquals(0, f.size());
    }

    @Test
    public void olderFactFilesLoadAsStrengthOne() throws Exception {
        File file = tmp.newFile("facts.tsv");
        Files.write(file.toPath(), ("Abuja\tcapital of\tNigeria\tis\n" + "quasar\tis\ta galactic nucleus\n").getBytes(StandardCharsets.UTF_8));
        Facts f = new Facts(file);
        assertEquals(2, f.size());
        assertEquals(1, f.strength("Abuja", "capital of", "Nigeria"));
        assertEquals(1, f.strength("quasar", "is", "a galactic nucleus"));
        f.learn("Abuja is the capital of Nigeria.", null, 1, 5);
        // the brain's files are kept in codes now (plain when no dictionary is open): read through the code reader
        List<String> lines = ai.ibiko.learn.CodeFile.lines(file);
        assertTrue(lines.toString(), lines.get(0).endsWith("\t2\t5"));
        assertEquals(2, new Facts(file).strength("Abuja", "capital of", "Nigeria"));
    }

    private Learner learner(Facts f) throws Exception {
        return new Learner(tmp.newFolder(), f, null);
    }

    @Test
    public void aCorrectionWithinTwoMessagesThatNamesTheAnswerIsAMiss() throws Exception {
        Facts f = new Facts(null);
        f.learn("Abuja is the capital of Nigeria.", null, 2, 0);
        Learner l = learner(f);
        assertNotNull(f.answer("What is Abuja?"));
        l.answered("What is Abuja?", "Abuja is the capital of Nigeria.", 1);
        assertEquals(Learner.Verdict.NONE, l.heard("And what about Lagos?", 2));
        assertEquals(Learner.Verdict.NONE, l.heard("hmm", 3));
        // the window has closed: a late "wrong" is not a correction
        assertEquals(Learner.Verdict.NONE, l.heard("no, Abuja is wrong", 4));
        assertEquals(0, l.judgedAnswers());

        f.answer("What is Abuja?");
        l.answered("What is Abuja?", "Abuja is the capital of Nigeria.", 5);
        assertEquals("a correction must name the answer", Learner.Verdict.NONE, l.heard("no, tell me a joke", 6));
        assertEquals(Learner.Verdict.CORRECTED, l.heard("No, Nigeria's capital is not that", 7));
        assertEquals(1, l.judgedAnswers());
        assertEquals(0, l.correct());
        assertEquals("corrected: 2 - 2", 0, f.strength("Abuja", "capital of", "Nigeria"));
        assertTrue(l.answers().get(l.answers().size() - 1).contains("\"miss\""));
    }

    @Test
    public void anAcceptanceIsAHit() throws Exception {
        Facts f = new Facts(null);
        f.learn("Abuja is the capital of Nigeria.", null, 2, 0);
        Learner l = learner(f);
        f.answer("What is Abuja?");
        l.answered("What is Abuja?", "Abuja is the capital of Nigeria.", 1);
        assertEquals(Learner.Verdict.ACCEPTED, l.heard("Yes, that's right", 2));
        assertEquals(1, l.correct());
        assertEquals(3, f.strength("Abuja", "capital of", "Nigeria"));
    }

    @Test
    public void openQuestionsFillAndClear() throws Exception {
        Facts f = new Facts(null);
        Learner l = learner(f);
        l.unanswered("What is Abuja?", 1);
        l.unanswered("Who invented radar?", 1);
        assertEquals(2, l.openQuestions().size());
        f.learn("Abuja is the capital of Nigeria.", null, 2, 0);
        l.review(2, 2000);
        assertEquals("a strong fact answers it now", 1, l.openQuestions().size());
        l.unanswered("Who invented radar?", 3);
        l.unanswered("Who invented radar?", 4);
        assertTrue("asked 3 times: unknown", l.openQuestions().isEmpty());
        assertEquals(1, l.unknown());
        l.clear();
        assertEquals(0, l.unknown());
        assertTrue(l.answers().isEmpty());
    }

    @Test
    public void personalDetailsAreMaskedFirst() {
        String m = Shapes.mask("Mail me at ada@example.com or call +234 803 123 4567, see https://x.com/ada, card 4111111111111111");
        assertFalse(m, m.contains("ada@") || m.contains("803") || m.contains("x.com") || m.contains("4111"));
        assertTrue(m, m.contains("{EMAIL}") && m.contains("{PHONE}") && m.contains("{URL}") && m.contains("{NUMBER}"));
    }

    @Test
    public void shapesAreCutAroundAKnownValue() {
        Shapes.Rule r = Shapes.shape("Chinua Achebe was born in Ogidi , a town in Nigeria.", "Ogidi", "born in", 0);
        assertNotNull(r);
        assertEquals("born in {V} , a", r.pattern());
        assertEquals("Lagos", r.read("Wole Soyinka was born in Lagos , a city."));
        assertNull("never from a question", Shapes.shape("Was Achebe born in Ogidi?", "Ogidi", "born in", 0));
        assertNull("never from a negation", Shapes.shape("Achebe was not born in Ogidi.", "Ogidi", "born in", 0));
        assertNull("too few real words", Shapes.shape("Ogidi", "Ogidi", "born in", 0));
    }

    @Test
    public void shapesArePromotedAndPruned() {
        Shapes.Rule r = new Shapes.Rule("born in", "born in", ",", 0);
        r.seen = 6;
        r.hit = 3;
        r.rejected = 2;
        assertTrue(r.promoted());
        r.rejected = 3;
        assertFalse("rejected 50%", r.promoted());
        r.seen = 20;
        r.hit = 4;
        assertTrue(r.prune(1));
        Shapes.Rule idle = new Shapes.Rule("born in", "born in", ",", 0);
        assertFalse(idle.prune(59 * DAY));
        assertTrue(idle.prune(61 * DAY));
    }

    @Test
    public void aLearnerGrowsShapesFromWhatItReads() throws Exception {
        Facts f = new Facts(null);
        f.learn("Chinua Achebe was born in Ogidi.", null, 2, 0);
        f.learn("Wole Soyinka was born in Abeokuta.", null, 2, 0);
        Learner l = learner(f);
        l.read("Chinua Achebe was born in Ogidi , a town in Anambra.", Learner.Source.RESEARCH, 1);
        assertEquals(1, l.rules().size());
        for (int i = 0; i < 6; i++) {
            l.read("Wole Soyinka was born in Abeokuta , a city in Ogun.", Learner.Source.RESEARCH, 2 + i);
        }
        Shapes.Rule r = l.rules().get(0);
        assertTrue(r.seen + "/" + r.hit, r.promoted());
        // a proven shape reads a new fact, seen once
        l.read("Ngozi Okonjo-Iweala was born in Ogwashi-Ukwu , a town in Delta.", Learner.Source.RESEARCH, 10);
        assertEquals(1, f.strength("Ngozi Okonjo-Iweala", "born in", "Ogwashi-Ukwu"));
        // people's words teach language only
        l.read("Bola Tinubu was born in Ilorin , a city.", Learner.Source.SOCIAL, 11);
        assertEquals(0, f.strength("Bola Tinubu", "born in", "Ilorin"));
    }

    @Test
    public void partsOfSpeech() {
        String e = PartsOfSpeech.explain("The quick brown fox quickly jumped over the lazy dog.", null);
        assertTrue(e, e.contains("The/determiner") && e.contains("fox/noun") && e.contains("quickly/adverb")
                && e.contains("over/preposition") && e.contains("lazy/adjective") && e.contains("dog/noun"));
        e = PartsOfSpeech.explain("She will visit Lagos and they are happy", null);
        assertTrue(e, e.contains("She/pronoun") && e.contains("will/auxiliary") && e.contains("visit/verb") && e.contains("Lagos/proper_noun")
                && e.contains("and/conjunction") && e.contains("happy/adjective"));
        String[] c = PartsOfSpeech.clause(PartsOfSpeech.tag("The moon orbits the earth.", w -> "orbits".equals(w) ? PartsOfSpeech.Tag.VERB : null));
        assertEquals("The moon", c[0]);
        assertEquals("orbits", c[1]);
        assertEquals("the earth", c[2]);
    }

    @Test
    public void wordsAreLearnedFromUse() throws Exception {
        Learner l = learner(new Facts(null));
        assertNull(l.partOf("glorp"));
        for (int i = 0; i < 3; i++) {
            l.read("They glorp every morning.", Learner.Source.PILOT, i);
        }
        assertEquals(PartsOfSpeech.Tag.VERB, l.partOf("glorp"));
        assertTrue(l.wordsLearned() >= 1);
        assertTrue(l.scoreLine().contains("of words understood"));
    }
}
