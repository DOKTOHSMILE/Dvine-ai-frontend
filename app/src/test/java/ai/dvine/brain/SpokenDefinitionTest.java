package ai.dvine.brain;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

/** Definitions said the way a person says them, without the part-of-speech label; examples only on request. */
public class SpokenDefinitionTest {
    private static KnowledgePack.Word w(String word, String meaning, String pos, String example) {
        return new KnowledgePack.Word(word, meaning, pos, Collections.<String>emptyList(), example);
    }

    @Test
    public void readsLikeSpeech() {
        KnowledgePack.Word school = w("school", "An educational institution.", "noun", "the school was founded in 1900");
        assertEquals("A school is an educational institution.", OfflineBrain.spokenDefinition(school, false));
        assertEquals("A school is an educational institution. For example: the school was founded in 1900.",
                OfflineBrain.spokenDefinition(school, true));
        assertEquals("An apple is a round fruit with red or green skin.",
                OfflineBrain.spokenDefinition(w("apple", "a round fruit with red or green skin", "noun", ""), false));
        assertEquals("To run means to move fast on foot.", OfflineBrain.spokenDefinition(w("run", "To move fast on foot.", "verb", ""), false));
        assertEquals("Happy means feeling or showing pleasure.",
                OfflineBrain.spokenDefinition(w("happy", "feeling or showing pleasure", "adjective", ""), false));
    }
}
