package com.ibiko.frontend.test

import org.junit.Assert.*
import org.junit.Test
import com.ibiko.frontend.memory.Memory

/**
 * The grammar is the boundary of what a sentence may teach. Every form here is a promise:
 * NEGATION for `is not`, RULE for `every …` (an universal regularity) and CAUSE for
 * `X because Y` (a law of cause and effect).
 */
class GrammarTest {

    private fun classify(t: String) = Memory(t).classify().intent

    private fun recall(t: String, answer: Boolean): Boolean =
        Memory(t).recall(answer)?.intent != "NEGATION"

    @Test
    fun `subject predicate sentence parses to recall`() {
        assertEquals("RULE", classify("the moon orbits the earth"))
        assertTrue("recall of a fact", recall("the moon orbits the earth", true))
    }

    @Test
    fun `sentences that are not subject predicate do not parse`() {
        assertEquals("NONE", classify("moon orbits earth"))
        assertEquals("NONE", classify("the moon orbits"))
        assertEquals("NONE", classify("orbits the earth"))
        assertEquals("NONE", classify("the moon orbits of earth"))
        assertEquals("NONE", classify("fish live in water"))
    }

    @Test
    fun `two sentences do not become one predicate`() {
        assertEquals("NONE", classify("the moon orbits the earth and the sun"))
        assertEquals("NONE", classify("the moon orbits the earth in because"))
        assertEquals("NONE", classify("the moon orbits because of"))
        assertEquals("NONE", classify("the moon orbits the earth and"))
        assertEquals("NONE", classify("the moon orbits and because"))
    }

    @Test
    fun `negation parses`() {
        assertEquals("NEGATION", classify("the moon is not made of green cheese"))
        assertTrue("a negated fact can still be read", recall("the moon is not made of green cheese", true))
        assertEquals("NONE", classify("the moon is because"))
    }

    @Test
    fun `every statement parses to a universal rule`() {
        assertEquals("RULE", classify("every planet orbits a star"))
        assertEquals("NONE", classify("every planet"))
    }

    @Test
    fun `because parses to cause and effect`() {
        assertEquals("CAUSE", classify("the ground is wet because it rained"))
        assertEquals("NONE", classify("the ground is because"))
    }

    @Test
    fun `the rule of every sentence is read from its body`() {
        val m = Memory("every planet orbits a star")
        m.learnSentence(true)
        assertEquals("FACT", m.recall("orbits a star", true)?.intent)
    }

    @Test
    fun `the law of a because sentence is read from its cause`() {
        val m = Memory("the ground is wet because it rained")
        m.learnSentence(true)
        assertEquals("NEGATION", m.recall("it rained", true)?.intent)
    }
}