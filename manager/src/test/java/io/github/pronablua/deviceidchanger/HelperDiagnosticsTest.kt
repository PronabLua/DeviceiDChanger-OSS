package io.github.pronablua.deviceidchanger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperDiagnosticsTest {
    @Test
    fun completionLogsOnlyApprovedFields() {
        val reply = HelperReply.parse(1, "STATUS=error\nERROR_CODE=busy\nSAVED=false\nSTOPPED=false\nRESTARTED=false\nROLLED_BACK=true\nRESTORED_LAUNCH=false\nCONFIG_REVISION=0123456789abcdef\nCONFIGURED_ID=1111222233334444\nOBSERVED_ID=5555666677778888\nMESSAGE=Private output 1111222233334444\n")
        val log = HelperDiagnostics.finished("apply", "com.example.target", 125, reply)
        assertTrue(log.contains("action=apply package=com.example.target duration_ms=125 exit_code=1 error_code=busy"))
        assertTrue(log.contains("saved=false stopped=false restarted=false rolled_back=true restored_launch=false revision=0123456789abcdef"))
        assertFalse(log.contains("1111222233334444"))
        assertFalse(log.contains("5555666677778888"))
        assertFalse(log.contains("Private output"))
        assertFalse(log.contains("CONFIGURED_ID"))
    }

    @Test
    fun diagnosticsRejectMalformedValuesAndDoNotInventOutcome() {
        val reply = HelperReply(-1, mapOf("error_code" to "bad\nsecret=value", "config_revision" to "not a revision", "saved" to "secret=value"), "raw secret output")
        val log = HelperDiagnostics.finished("bad\naction", "bad;package", 4, reply)
        assertTrue(log.contains("action=unknown package=none"))
        assertTrue(log.contains("saved=unknown stopped=unknown restarted=unknown rolled_back=unknown restored_launch=unknown revision=unknown"))
        assertFalse(log.contains("secret"))
        assertFalse(log.contains("\n"))
        assertTrue(HelperDiagnostics.started("get", "com.example.target") == "helper_start action=get package=com.example.target")
    }
}
