package com.ipodclassic.app

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun oauthStateRejectsMissingAndMismatchedValues() {
        assertFalse(Pkce.validState(null, null))
        assertFalse(Pkce.validState("", ""))
        assertFalse(Pkce.validState("expected", "different"))
        assertTrue(Pkce.validState("expected", "expected"))
    }
    @Test fun verifierIsUrlSafeAndLongEnough() {
        val verifier = Pkce.random()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(verifier, Pkce.random())
    }
    @Test fun wheelCrossesAngleBoundaryWithoutJumping() {
        assertEquals(2f, angularDelta(179f, -179f), .001f)
        assertEquals(-2f, angularDelta(-179f, 179f), .001f)
        assertEquals(20f, angularDelta(-10f, 10f), .001f)
        assertEquals(-20f, angularDelta(10f, -10f), .001f)
    }
    @Test fun timeFormattingHandlesNegativeAndLongTracks() {
        assertEquals("0:00", formatTime(-1))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("120:00", formatTime(7_200_000))
    }
}
