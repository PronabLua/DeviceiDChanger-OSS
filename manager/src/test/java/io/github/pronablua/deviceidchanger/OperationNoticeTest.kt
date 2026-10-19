package io.github.pronablua.deviceidchanger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationNoticeTest {
    private val enteredId = "ABCD1234ABCD1234"
    private val oldProfile = HelperReply.parse(0, "STATUS=ok\nENABLED=true\nCONFIGURED_ID=1111222233334444\n")
    private val emptyProfile = HelperReply.parse(0, "STATUS=ok\nENABLED=false\nCONFIGURED_ID=\n")

    @Test
    fun successfulRollbackPreservesDraftAndReportsPreviousLaunch() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=launch_unconfirmed\nSAVED=false\nROLLED_BACK=true\nRESTORED_LAUNCH=true\nRESTARTED=false\n")
        val notice = OperationNotice.fromReply("apply", reply)
        assertEquals("Change rolled back", notice.title)
        assertTrue(notice.message.contains("previous setting was restored"))
        assertTrue(notice.message.contains("confirmed reopening"))
        assertFalse(notice.successful)
        assertEquals(enteredId, draftAfterChange(enteredId, reply, oldProfile))
    }

    @Test
    fun rollbackWithoutConfirmedRelaunchDoesNotClaimTheAppOpened() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=start_failed\nSAVED=false\nROLLED_BACK=true\nRESTORED_LAUNCH=false\nRESTARTED=false\n")
        val notice = OperationNotice.fromReply("apply", reply)
        assertEquals("Change rolled back", notice.title)
        assertTrue(notice.message.contains("did not confirm reopening"))
        assertFalse(notice.successful)
        assertEquals(enteredId, draftAfterChange(enteredId, reply, emptyProfile))
    }

    @Test
    fun failedRollbackNeverClaimsThePreviousSettingWasRestored() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=rollback_failed\nSAVED=true\nROLLED_BACK=false\nRESTORED_LAUNCH=false\nRESTARTED=false\n")
        val notice = OperationNotice.fromReply("apply", reply)
        assertEquals("Rollback failed", notice.title)
        assertTrue(notice.message.contains("may still be saved"))
        assertFalse(notice.successful)
        assertEquals(enteredId, draftAfterChange(enteredId, reply, oldProfile))
    }

    @Test
    fun restoreLaunchFailureKeepsRemovalDistinctFromRollback() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=start_failed\nSAVED=true\nROLLED_BACK=false\nRESTORED_LAUNCH=false\nRESTARTED=false\n")
        val notice = OperationNotice.fromReply("restore", reply)
        assertEquals("Override removed · restart needs attention", notice.title)
        assertTrue(notice.message.contains("override was removed"))
        assertFalse(notice.successful)
        assertEquals(enteredId, draftAfterChange(enteredId, reply, emptyProfile))
    }

    @Test
    fun acceptedChangeCanRefreshDraftFromConfirmedConfiguration() {
        val reply = HelperReply.parse(0, "STATUS=ok\nSAVED=true\nRESTARTED=true\nROLLED_BACK=false\n")
        assertTrue(OperationNotice.fromReply("apply", reply).successful)
        assertEquals("1111222233334444", draftAfterChange(enteredId, reply, oldProfile))
        assertEquals("", draftAfterChange(enteredId, reply, emptyProfile))
    }

    @Test
    fun conflictingSuccessFlagsCannotTurnRollbackIntoSuccess() {
        val reply = HelperReply.parse(0, "STATUS=ok\nSAVED=true\nRESTARTED=true\nROLLED_BACK=true\nRESTORED_LAUNCH=true\n")
        assertFalse(OperationNotice.fromReply("apply", reply).successful)
        assertEquals(enteredId, draftAfterChange(enteredId, reply, oldProfile))
    }
}
