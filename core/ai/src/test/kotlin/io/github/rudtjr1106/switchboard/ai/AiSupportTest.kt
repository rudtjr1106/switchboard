package io.github.rudtjr1106.switchboard.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSupportTest {

    @Test
    fun `prebuilt native platforms`() {
        assertTrue(AiSupport.platformSupported("Mac OS X", "aarch64"))
        assertTrue(AiSupport.platformSupported("Mac OS X", "x86_64"))
        assertTrue(AiSupport.platformSupported("Windows 11", "amd64"))
        assertTrue(AiSupport.platformSupported("Linux", "amd64"))
        assertTrue(AiSupport.platformSupported("Linux", "aarch64"))
        assertFalse(AiSupport.platformSupported("Windows 11", "x86"))
        assertFalse(AiSupport.platformSupported("FreeBSD", "amd64"))
        assertTrue(AiSupport.describePlatform().isNotBlank())
    }
}
