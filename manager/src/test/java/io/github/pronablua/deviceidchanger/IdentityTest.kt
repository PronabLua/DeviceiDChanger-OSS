package io.github.pronablua.deviceidchanger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {
    @Test
    fun onlyExactHexIdsAreAccepted() {
        assertTrue(Identity.validId("0123456789abcdef"))
        assertTrue(Identity.validId("FEDCBA9876543210"))
        listOf("", "0123", "0123456789abcdef0", "0123456789abcdeg", "0123456789abcd;f", "0123456789abcde\n").forEach {
            assertFalse(it, Identity.validId(it))
        }
    }

    @Test
    fun shellMetacharactersCannotEnterCommands() {
        listOf("com.example;id", "com.example\nreboot", "com.\$(id)", "com.example app", "com.example'", "../com.example").forEach {
            assertFalse(it, Identity.validPackage(it))
            assertTrue(runCatching { HelperCommand.build("get", it) }.isFailure)
        }
        assertTrue(runCatching { HelperCommand.build("reboot") }.isFailure)
        assertTrue(runCatching { HelperCommand.build("status", "com.example") }.isFailure)
        assertTrue(runCatching { HelperCommand.build("apply", "com.example", "0000000000000000;id") }.isFailure)
    }

    @Test
    fun randomIdsAreValidAndDifferent() {
        val identifiers = List(32) { Identity.randomId() }
        assertTrue(identifiers.all(Identity::validId))
        assertEquals(32, identifiers.toSet().size)
    }

    @Test
    fun savedStateSurvivesRestartFailure() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=restart_failed\nSAVED=true\nRESTARTED=false\nMESSAGE=Saved, but the app could not reopen.\n")
        assertFalse(reply.successful)
        assertTrue(reply.saved)
        assertFalse(reply.restarted)
        assertEquals("Saved, but the app could not reopen.", reply.message)
    }

    @Test
    fun malformedOrIncompleteRepliesNeverClaimSuccess() {
        assertFalse(HelperReply.parse(0, "success").successful)
        assertFalse(HelperReply.parse(1, "STATUS=ok").successful)
        val reply = HelperReply.parse(0, "STATUS=ok\nCONFIGURED_ID=0123456789abcdef\nOBSERVED_ID=\nMESSAGE=a=b\n")
        assertTrue(reply.successful)
        assertEquals("", reply.fields["observed_id"])
        assertEquals("a=b", reply.message)
    }

    @Test
    fun recognizedProtocolWithoutMessageDoesNotEchoPayload() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=start_failed\nCONFIGURED_ID=1111222233334444\n")
        assertEquals("", reply.message)
    }

    @Test
    fun onlyNewestSelectionRequestCanUpdateTheDraft() {
        val requests = SelectionRequests()
        val firstAppA = requests.next()
        val appB = requests.next()
        val secondAppA = requests.next()
        assertFalse(requests.isCurrent(firstAppA))
        assertFalse(requests.isCurrent(appB))
        assertTrue(requests.isCurrent(secondAppA))
        requests.next()
        assertFalse(requests.isCurrent(secondAppA))
    }
}
