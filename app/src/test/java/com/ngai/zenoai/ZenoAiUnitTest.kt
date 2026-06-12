package com.ngai.zenoai

import org.junit.Test
import org.junit.Assert.*
import com.ngai.zenoai.utils.NetworkUtils
import com.ngai.zenoai.utils.Constants

class ZenoAiUnitTest {

    @Test
    fun baseUrl_isCorrect() {
        assertEquals("https://zenoai-bot.vercel.app/", Constants.BASE_URL)
    }

    @Test
    fun secureUrl_httpsReturnsTrue() {
        assertTrue(NetworkUtils.isSecureUrl("https://zenoai-bot.vercel.app/"))
    }

    @Test
    fun secureUrl_httpReturnsFalse() {
        assertFalse(NetworkUtils.isSecureUrl("http://example.com"))
    }

    @Test
    fun maliciousScheme_javascriptBlocked() {
        assertTrue(NetworkUtils.isMaliciousScheme("javascript:alert(1)"))
    }

    @Test
    fun maliciousScheme_httpsAllowed() {
        assertFalse(NetworkUtils.isMaliciousScheme("https://zenoai-bot.vercel.app/"))
    }

    @Test
    fun maliciousScheme_fileBlocked() {
        assertTrue(NetworkUtils.isMaliciousScheme("file:///etc/passwd"))
    }
}
