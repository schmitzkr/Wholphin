package com.github.damontecres.wholphin.util

/**
 * Tallies ranked ballots using Black's method: elect the Condorcet
 * winner (the candidate that wins every head-to-head matchup) if one
 * exists, otherwise fall back to a Borda count.
 *
 * Each ballot is an ordered list of candidate ids from most to least
 * preferred.
 *
 * Instant-runoff was the obvious alternative and is deliberately not
 * used: it eliminates on first preferences alone, so the compromise
 * film that everyone ranks second but nobody ranks first gets dropped
 * in round one. Both halves of Black's method keep it. See
 * `TestRankedBallotTally` for a worked example.
 *
 * Ties are never broken arbitrarily - if Borda can't separate the
 * leaders they come back via [Result.tiedWinners] for the caller to
 * resolve.
 */
object RankedBallotTally {
    /** Which half of Black's method actually decided the result. */
    enum class Method {
        CONDORCET,
        BORDA,
    }

    data class Result<T>(
        val winner: T?,
        val tiedWinners: Set<T>,
        /** Null only when there were no candidates at all to decide between. */
        val method: Method?,
    )

    fun <T> tally(ballots: List<List<T>>): Result<T> {
        val candidates = ballots.flatten().toSet()
        if (candidates.isEmpty()) {
            return Result(winner = null, tiedWinners = emptySet(), method = null)
        }
        if (candidates.size == 1) {
            // Vacuously beats every other candidate, of which there are none.
            return Result(winner = candidates.first(), tiedWinners = emptySet(), method = Method.CONDORCET)
        }

        val preferredOver = pairwisePreferences(ballots, candidates)

        // Must beat every other candidate outright - an equal split in any
        // one matchup disqualifies it, hence the strict comparison.
        val condorcetWinner =
            candidates.firstOrNull { candidate ->
                candidates
                    .filter { it != candidate }
                    .all { other ->
                        val wins = preferredOver[candidate to other] ?: 0
                        val losses = preferredOver[other to candidate] ?: 0
                        wins > losses
                    }
            }
        if (condorcetWinner != null) {
            return Result(winner = condorcetWinner, tiedWinners = emptySet(), method = Method.CONDORCET)
        }

        val scores = bordaScores(ballots, candidates)
        val topScore = scores.values.max()
        val leaders = scores.filterValues { it == topScore }.keys
        return if (leaders.size == 1) {
            Result(winner = leaders.first(), tiedWinners = emptySet(), method = Method.BORDA)
        } else {
            Result(winner = null, tiedWinners = leaders, method = Method.BORDA)
        }
    }

    /**
     * For every ordered pair, how many ballots rank the first ahead of the
     * second. Anything left off a ballot ranks below everything on it, and
     * two absent candidates count for neither side.
     */
    private fun <T> pairwisePreferences(
        ballots: List<List<T>>,
        candidates: Set<T>,
    ): Map<Pair<T, T>, Int> {
        val counts = mutableMapOf<Pair<T, T>, Int>()
        for (ballot in ballots) {
            val positions = ballot.withIndex().associate { (index, candidate) -> candidate to index }
            for (first in candidates) {
                for (second in candidates) {
                    if (first == second) continue
                    val firstPosition = positions[first] ?: Int.MAX_VALUE
                    val secondPosition = positions[second] ?: Int.MAX_VALUE
                    if (firstPosition < secondPosition) {
                        counts[first to second] = (counts[first to second] ?: 0) + 1
                    }
                }
            }
        }
        return counts
    }

    /**
     * Points by position: with n candidates the top pick scores n-1, the
     * next n-2, down to zero. Unranked candidates score nothing.
     */
    private fun <T> bordaScores(
        ballots: List<List<T>>,
        candidates: Set<T>,
    ): Map<T, Int> {
        val scores = candidates.associateWith { 0 }.toMutableMap()
        for (ballot in ballots) {
            ballot.forEachIndexed { index, candidate ->
                if (candidate in scores) {
                    scores[candidate] = (scores[candidate] ?: 0) + (candidates.size - 1 - index)
                }
            }
        }
        return scores
    }
}
