package com.darrenai.omniscient.domain

import java.util.concurrent.atomic.AtomicInteger

/**
 * The butler. Every acknowledgment addresses the user respectfully ("boss"),
 * Jarvis-style. Rotation is deterministic (round-robin) so replies never repeat
 * twice in a row and never depend on randomness.
 */
object Persona {

    private val cursor = AtomicInteger(0)

    private fun <T> rotate(pool: List<T>): T {
        val i = Math.floorMod(cursor.getAndIncrement(), pool.size)
        return pool[i]
    }

    val ACKS = listOf(
        "Right away, boss.",
        "On it, boss.",
        "Consider it done, boss.",
        "At once, boss.",
        "As you wish, boss.",
        "Leave it to me, boss.",
        "Yes, boss — handling it now.",
        "Righto, boss — on it."
    )

    val WORKING = listOf(
        "Working on it, boss…",
        "One moment, boss…",
        "Putting it together, boss…"
    )

    val APOLOGIES = listOf(
        "My apologies, boss — ",
        "Forgive me, boss — ",
        "That one slipped past me, boss — "
    )

    val GREETINGS = listOf(
        "At your service, boss.",
        "Omniscient online, boss. What are your orders?",
        "Systems at full power, boss. How may I serve?"
    )

    val CONFIRM_NOTES = listOf(
        "Shall I proceed, boss?",
        "Just say the word, boss."
    )

    fun ack(): String = rotate(ACKS)
    fun working(): String = rotate(WORKING)
    fun apology(): String = rotate(APOLOGIES)
    fun greeting(): String = rotate(GREETINGS)
    fun confirmNote(): String = rotate(CONFIRM_NOTES)

    /** Prefix a completed action with an acknowledgment. */
    fun announce(result: String): String = "${ack()} ${result.trim()}"

    /** Prefix a failure with an apology. */
    fun excuse(error: String): String = "${apology()}${error.trim()}"
}
