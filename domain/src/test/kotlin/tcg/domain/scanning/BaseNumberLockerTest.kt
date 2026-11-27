package tcg.domain.scanning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaseNumberLockerTest {
    /** Feeds scripted reads (Base Number to time in ms) and returns the locks in order. */
    private fun BaseNumberLocker.feed(vararg reads: Pair<String, Long>): List<String> =
        reads.mapNotNull { (number, at) -> read(number, at) }

    @Test
    fun `locks on the third read of the same number`() {
        val locker = BaseNumberLocker()

        assertNull(locker.read("OP01-001", 0))
        assertNull(locker.read("OP01-001", 100))
        assertEquals("OP01-001", locker.read("OP01-001", 200))
    }

    @Test
    fun `current votes show the count per number and clear on a lock`() {
        val locker = BaseNumberLocker()

        locker.feed("OP01-001" to 0, "OP01-081" to 100, "OP01-001" to 200)
        assertEquals(mapOf("OP01-001" to 2, "OP01-081" to 1), locker.currentVotes())

        locker.read("OP01-001", 300)
        assertEquals(emptyMap(), locker.currentVotes())
    }

    @Test
    fun `the vote count is the one configured constant by default`() {
        assertEquals(3, BaseNumberLocker.DEFAULT_VOTES)
        val locker = BaseNumberLocker(votesRequired = 2)

        assertEquals(listOf("OP01-001"), locker.feed("OP01-001" to 0, "OP01-001" to 100))
    }

    @Test
    fun `reads of other numbers between votes do not reset progress`() {
        val locker = BaseNumberLocker()

        val locks = locker.feed(
            "OP01-001" to 0, "OP01-081" to 100, "OP01-001" to 200, "OP01-091" to 300, "OP01-001" to 400,
        )

        assertEquals(listOf("OP01-001"), locks)
    }

    @Test
    fun `noise that never repeats enough does not lock`() {
        val locker = BaseNumberLocker()

        val locks = locker.feed("OP01-001" to 0, "OP01-081" to 100, "OP01-091" to 200, "OP02-013" to 300)

        assertEquals(emptyList(), locks)
    }

    @Test
    fun `votes older than the clear gap are dropped`() {
        val locker = BaseNumberLocker()

        val locks = locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 1_700)

        assertEquals(emptyList(), locks)
    }

    @Test
    fun `the same number is ignored after a lock while it stays in view`() {
        val locker = BaseNumberLocker()
        locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 200)

        val locks = locker.feed(*(300L..2_000L step 100).map { "OP01-001" to it }.toTypedArray())

        assertEquals(emptyList(), locks)
    }

    @Test
    fun `the same number locks again after no read for the clear gap`() {
        val locker = BaseNumberLocker()
        locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 200)

        val locks = locker.feed("OP01-001" to 1_800, "OP01-001" to 1_900, "OP01-001" to 2_000)

        assertEquals(listOf("OP01-001"), locks)
    }

    @Test
    fun `a different number locks straight away after a verdict`() {
        val locker = BaseNumberLocker()
        locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 200)
        locker.verdict(300)

        val locks = locker.feed("OP02-013" to 400, "OP02-013" to 500, "OP02-013" to 600)

        assertEquals(listOf("OP02-013"), locks)
    }

    @Test
    fun `the clear gap counts from the verdict, not from the last read before it`() {
        val locker = BaseNumberLocker()
        locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 200)
        locker.verdict(5_000)

        val locks = locker.feed("OP01-001" to 5_400, "OP01-001" to 5_500, "OP01-001" to 5_600)

        assertEquals(emptyList(), locks)
    }

    @Test
    fun `a read of a different number clears the cooldown for the previous one`() {
        val locker = BaseNumberLocker()
        locker.feed("OP01-001" to 0, "OP01-001" to 100, "OP01-001" to 200)
        locker.verdict(300)

        val locks = locker.feed("OP02-013" to 400, "OP01-001" to 500, "OP01-001" to 600, "OP01-001" to 700)

        assertEquals(listOf("OP01-001"), locks)
    }
}
