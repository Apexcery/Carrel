package uk.co.zenithal.carrel.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordsTest {
    @Test
    fun `too short`() = assertEquals("Use at least 8 characters.", passwordProblem("1234567"))

    @Test
    fun `long enough`() = assertNull(passwordProblem("12345678"))

    @Test
    fun `72 bytes is the most bcrypt uses`() {
        assertNull(passwordProblem("a".repeat(72)))
        assertEquals("Use 72 characters or fewer.", passwordProblem("a".repeat(73)))
    }

    @Test
    fun `counts bytes, not characters`() =
        // 25 three-byte characters: 25 characters but 75 bytes.
        assertEquals("Use 72 characters or fewer.", passwordProblem("€".repeat(25)))
}
