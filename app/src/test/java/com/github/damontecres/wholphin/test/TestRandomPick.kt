package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.util.pickRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TestRandomPick {
    @Test
    fun emptyListReturnsNull() {
        assertNull(emptyList<String>().pickRandom())
    }

    @Test
    fun singleItemListReturnsThatItem() {
        assertEquals("a", listOf("a").pickRandom())
    }

    @Test
    fun alwaysReturnsAnActualMember() {
        val items = listOf("a", "b", "c", "d")
        repeat(50) { seed ->
            val pick = items.pickRandom(Random(seed))
            assertTrue(pick in items)
        }
    }

    @Test
    fun sameSeedIsDeterministic() {
        val items = listOf("a", "b", "c", "d")
        assertEquals(items.pickRandom(Random(42)), items.pickRandom(Random(42)))
    }
}
