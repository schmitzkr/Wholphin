package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.util.RankedBallotTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestRankedBallotTally {
    @Test
    fun singleCandidateWinsImmediately() {
        val result = RankedBallotTally.tally(listOf(listOf("a"), listOf("a")))
        assertEquals("a", result.winner)
        assertEquals(emptySet<String>(), result.tiedWinners)
    }

    @Test
    fun unanimousFirstChoiceWins() {
        val ballots =
            listOf(
                listOf("a", "b", "c"),
                listOf("a", "c", "b"),
                listOf("a", "b", "c"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertEquals("a", result.winner)
        assertEquals(RankedBallotTally.Method.CONDORCET, result.method)
    }

    @Test
    fun compromiseCandidateBeatsBothFactions() {
        // The case instant-runoff gets wrong: "c" is nobody's favorite, so
        // IRV eliminates it in the first round and elects "a". But "c" beats
        // "a" head-to-head 3-2 and beats "b" head-to-head 3-2, making it the
        // Condorcet winner and the film the whole room can live with.
        val ballots =
            listOf(
                listOf("a", "c", "b"),
                listOf("a", "c", "b"),
                listOf("b", "c", "a"),
                listOf("b", "c", "a"),
                listOf("c", "a", "b"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertEquals("c", result.winner)
        assertEquals(RankedBallotTally.Method.CONDORCET, result.method)
    }

    @Test
    fun condorcetWinnerBeatsHigherBordaScore() {
        // Borda would elect "b" here (7 points vs "a"'s 6), but "a" wins both
        // of its head-to-head matchups 3-2, so the Condorcet half takes
        // precedence. This is what makes it Black's method rather than a
        // plain Borda count.
        val ballots =
            listOf(
                listOf("a", "b", "c"),
                listOf("a", "b", "c"),
                listOf("a", "b", "c"),
                listOf("b", "c", "a"),
                listOf("b", "c", "a"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertEquals("a", result.winner)
        assertEquals(RankedBallotTally.Method.CONDORCET, result.method)
    }

    @Test
    fun bordaResolvesACondorcetCycle() {
        // "a" beats "b" and "b" beats "c", but "a" vs "c" splits 2-2, so no
        // candidate wins every matchup and the Borda count decides instead.
        val ballots =
            listOf(
                listOf("a", "b", "c"),
                listOf("b", "c", "a"),
                listOf("c", "a", "b"),
                listOf("a", "b", "c"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertEquals("a", result.winner)
        assertEquals(RankedBallotTally.Method.BORDA, result.method)
    }

    @Test
    fun perfectCycleTiesOnBordaToo() {
        // The textbook rock-paper-scissors cycle. Every candidate wins one
        // matchup and loses one, and all three end on equal Borda scores, so
        // there is genuinely nothing to separate them.
        val ballots =
            listOf(
                listOf("a", "b", "c"),
                listOf("b", "c", "a"),
                listOf("c", "a", "b"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertNull(result.winner)
        assertEquals(setOf("a", "b", "c"), result.tiedWinners)
        assertEquals(RankedBallotTally.Method.BORDA, result.method)
    }

    @Test
    fun partialBallotsRankUnlistedCandidatesLast() {
        // Voters who named only one film still express a preference for it
        // over everything they left off, so "a" takes both matchups.
        val ballots =
            listOf(
                listOf("a"),
                listOf("a"),
                listOf("b"),
                listOf("c"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertEquals("a", result.winner)
    }

    @Test
    fun twoCandidateExactTieReturnsNoWinner() {
        val ballots =
            listOf(
                listOf("a"),
                listOf("b"),
            )
        val result = RankedBallotTally.tally(ballots)
        assertNull(result.winner)
        assertEquals(setOf("a", "b"), result.tiedWinners)
    }

    @Test
    fun noBallotsReturnsNoWinner() {
        val result = RankedBallotTally.tally(emptyList<List<String>>())
        assertNull(result.winner)
        assertEquals(emptySet<String>(), result.tiedWinners)
        assertNull(result.method)
    }
}
