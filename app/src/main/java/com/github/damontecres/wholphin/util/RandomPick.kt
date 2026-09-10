package com.github.damontecres.wholphin.util

import kotlin.random.Random

/**
 * Picks a uniformly random element, or null if the receiver is empty.
 * Used to skip a ranked-choice vote and just pick from the candidates.
 */
fun <T> List<T>.pickRandom(random: Random = Random.Default): T? = randomOrNull(random)
