package com.example.mozcengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlin.text.Charsets

class JapaneseCandidatePriorTest {

    @Test
    fun parse_ignoresBlankLinesCommentsAndInvalidRows() {
        val prior = JapaneseCandidatePrior.parse(
            "# reading\tcandidate\tpriority\n" +
                "\n" +
                "わたし\t私\t3\n" +
                "not-a-valid-row\n" +
                "foo\tbar\n" +
                "foo\tbar\tnot-a-number\n" +
                "\t\t1\n" +
                "あい\t愛\t2\n",
        )

        assertEquals(3, prior.priorityOf("わたし", "私"))
        assertEquals(2, prior.priorityOf("あい", "愛"))
        assertEquals(0, prior.priorityOf("foo", "bar"))
        assertEquals(0, prior.priorityOf("わたし", "わたし"))
    }

    @Test
    fun parse_clampsPriorityToInclusiveRange() {
        val prior = JapaneseCandidatePrior.parse(
            "あい\t愛\t-5\nあい\t合い\t9\nあい\t藍\t0\nあい\t相\t3\n",
        )

        assertEquals(0, prior.priorityOf("あい", "愛"))
        assertEquals(3, prior.priorityOf("あい", "合い"))
        assertEquals(0, prior.priorityOf("あい", "藍"))
        assertEquals(3, prior.priorityOf("あい", "相"))
    }

    @Test
    fun parse_keepsMaximumPriorityForDuplicatePairs() {
        val prior = JapaneseCandidatePrior.parse(
            "あい\t藍\t1\nあい\t藍\t3\nあい\t藍\t2\n",
        )

        assertEquals(3, prior.priorityOf("あい", "藍"))
    }

    @Test
    fun parse_returnsEmptyTable_forCommentsOnly() {
        val prior = JapaneseCandidatePrior.parse(
            """
            # reading	candidate	priority
            # わたし	私	3
            """.trimIndent(),
        )

        assertTrue(prior.isEmpty())
        assertEquals(0, prior.priorityOf("わたし", "私"))
    }

    @Test
    fun parseLine_returnsNull_forBlankCommentAndMalformedRows() {
        assertNull(JapaneseCandidatePrior.parseLine(""))
        assertNull(JapaneseCandidatePrior.parseLine("   "))
        assertNull(JapaneseCandidatePrior.parseLine("# comment"))
        assertNull(JapaneseCandidatePrior.parseLine("  # indented comment"))
        assertNull(JapaneseCandidatePrior.parseLine("only-one-column"))
        assertNull(JapaneseCandidatePrior.parseLine("reading\tcandidate"))
        assertNull(JapaneseCandidatePrior.parseLine("reading\tcandidate\tx"))
        assertNull(JapaneseCandidatePrior.parseLine("\t私\t3"))
        assertNull(JapaneseCandidatePrior.parseLine("わたし\t\t3"))
    }

    @Test
    fun loadOrEmpty_returnsEmpty_whenStreamCannotBeOpened() {
        val prior = JapaneseCandidatePrior.loadOrEmpty {
            throw FileNotFoundException("missing japanese_candidate_prior.tsv")
        }

        assertTrue(prior.isEmpty())
        assertEquals(0, prior.priorityOf("わたし", "私"))
    }

    @Test
    fun loadOrEmpty_parsesUtf8TsvFromStream() {
        val tsv = "わたし\t私\t3\n# skip\nわたし\tわたし\t2\n"
        val prior = JapaneseCandidatePrior.loadOrEmpty {
            ByteArrayInputStream(tsv.toByteArray(Charsets.UTF_8))
        }

        assertEquals(3, prior.priorityOf("わたし", "私"))
        assertEquals(2, prior.priorityOf("わたし", "わたし"))
    }

    @Test
    fun loadOrEmpty_readsClasspathFixtureAndAppliesParserRules() {
        val prior = JapaneseCandidatePrior.loadOrEmpty {
            requireNotNull(
                javaClass.getResourceAsStream("/japanese_candidate_prior_fixture.tsv"),
            ) { "test fixture is missing" }
        }

        assertEquals(3, prior.priorityOf("わたし", "私"))
        assertEquals(2, prior.priorityOf("わたし", "わたし"))
        assertEquals(0, prior.priorityOf("あい", "愛"))
        assertEquals(3, prior.priorityOf("あい", "合い"))
        assertEquals(3, prior.priorityOf("あい", "藍"))
        assertEquals(0, prior.priorityOf("foo", "bar"))
    }
}
