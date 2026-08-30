package fr.ardoise.tasks.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fingerprint has to be pasted into Google Cloud Console verbatim, so the
 * exact shape matters: uppercase hexadecimal, colon-separated, twenty bytes.
 * Any other rendering silently fails to match and sends the user hunting for a
 * problem in the wrong place.
 */
class SigningIdentityTest {

    @Test
    fun `the fingerprint matches the format the Cloud Console expects`() {
        // SHA-1 of the empty input, a fixed and independently checkable value.
        val printed = SigningIdentity.fingerprint(ByteArray(0))

        assertEquals(
            "DA:39:A3:EE:5E:6B:4B:0D:32:55:BF:EF:95:60:18:90:AF:D8:07:09",
            printed,
        )
    }

    @Test
    fun `every byte is two uppercase hex digits`() {
        val printed = SigningIdentity.fingerprint(byteArrayOf(0, 1, 15, 16, -1))

        assertTrue(printed.matches(Regex("([0-9A-F]{2}:){19}[0-9A-F]{2}")))
    }

    @Test
    fun `the fingerprint is stable for the same input`() {
        val input = "ardoise".toByteArray()

        assertEquals(SigningIdentity.fingerprint(input), SigningIdentity.fingerprint(input))
    }
}
