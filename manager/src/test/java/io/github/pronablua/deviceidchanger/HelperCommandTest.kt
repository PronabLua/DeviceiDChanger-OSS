package io.github.pronablua.deviceidchanger

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HelperCommandTest {
    private fun runGuard(action: String, staged: Boolean): HelperReply {
        val windows = requireNotNull(System.getProperty("os.name")).startsWith("Windows")
        val shell = if (windows) File(System.getenv("ProgramFiles") ?: "C:/Program Files", "Git/bin/bash.exe") else File("/bin/bash")
        assumeTrue("Bash is required for shell protocol tests", shell.isFile)
        val guardMock = if (staged) {
            "function [ { case \"\$2\" in /data/adb/modules_update/deviceidchanger_native/control.sh) return 0 ;; *) return 1 ;; esac; }; "
        } else {
            "function [ { return 1; }; "
        }
        val command = if (action == "status") HelperCommand.build(action) else HelperCommand.build(action, "com.example.target", "0123456789abcdef")
        val output = CommandProcess.run(listOf(shell.absolutePath, "--noprofile", "--norc", "-c", guardMock + command), 5000)
        assertFalse(output.timedOut)
        assertFalse(output.truncated)
        return HelperReply.parse(output.exitCode, output.output)
    }

    @Test
    fun standaloneManagerGetsCleanMissingModuleReply() {
        val reply = runGuard("status", false)
        assertFalse(reply.successful)
        assertEquals("module_missing", reply.fields["error_code"])
        assertEquals("missing", reply.fields["module_state"])
        assertTrue(reply.message.contains("Install the Device ID Changer module ZIP"))
        assertFalse(reply.message.contains("/data/adb"))
    }

    @Test
    fun stagedStatusIsReadableButDoesNotClaimActiveRuntime() {
        val reply = runGuard("status", true)
        assertTrue(reply.successful)
        assertEquals("module_pending_reboot", reply.fields["error_code"])
        assertEquals("pending_reboot", reply.fields["module_state"])
        assertEquals("pending_reboot", reply.fields["runtime"])
        assertFalse(reply.saved)
        assertFalse(reply.restarted)
    }

    @Test
    fun stagedModuleRejectsApplyWithoutDispatchingHelper() {
        val reply = runGuard("apply", true)
        assertFalse(reply.successful)
        assertEquals("module_pending_reboot", reply.fields["error_code"])
        assertFalse(reply.saved)
        assertFalse(reply.restarted)
    }

    @Test
    fun installedHelperInvocationHasOnlyValidatedArguments() {
        val command = HelperCommand.build("apply", "com.example.target", "ABCDEF0123456789")
        assertTrue(command.contains("then exec /system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh apply com.example.target abcdef0123456789; else"))
        assertTrue(command.indexOf("modules_update/deviceidchanger_native/control.sh") < command.indexOf("then exec"))
    }
}
