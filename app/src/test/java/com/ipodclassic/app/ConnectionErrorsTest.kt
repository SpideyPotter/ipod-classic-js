package com.ipodclassic.app

import org.junit.Assert.*
import org.junit.Test

class ConnectionErrorsTest {
    private class NotLoggedInException : Exception("sensitive-provider-payload")
    private class UserNotAuthorizedException : Exception()

    @Test fun wrappedLoginFailureUsesActionableCause() {
        val message = playbackConnectionError(IllegalStateException("private", NotLoggedInException()))
        assertTrue(message.contains("Open Spotify and sign in"))
        assertTrue(message.contains("NotLoggedInException"))
        assertFalse(message.contains("sensitive-provider-payload"))
        assertFalse(message.contains("private"))
    }
    @Test fun authorizationFailureDistinguishesLibraryAndPlayback() {
        val message = playbackConnectionError(UserNotAuthorizedException())
        assertTrue(message.contains("Browser library sign-in is separate"))
        assertTrue(message.contains("UserNotAuthorizedException"))
    }
    @Test fun unknownErrorsStillHaveADiagnosticTypeWithoutPayload() {
        val message = playbackConnectionError(IllegalArgumentException("access_token=secret"))
        assertTrue(message.contains("IllegalArgumentException"))
        assertFalse(message.contains("secret"))
    }
}
