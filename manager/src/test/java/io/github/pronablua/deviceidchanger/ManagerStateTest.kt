package io.github.pronablua.deviceidchanger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerStateTest {
    private val app = AppEntry("com.example.target", "Example", 10123, AppAbi.Arm64, null)
    private val ready = HelperReply.parse(0, "STATUS=ok\nPROTOCOL_VERSION=1\nMODULE_STATE=enabled\nRUNTIME=unverified\n")
    private val invalidProfile = HelperReply.parse(1, "STATUS=error\nERROR_CODE=profile_invalid\n")

    private fun state(reply: HelperReply) = ManagerState(ownerUser = true, selected = app, status = ready, selectionReply = reply)

    @Test
    fun malformedProfileCanBeRestoredButCannotBeApplied() {
        val current = state(invalidProfile)
        assertTrue(current.canRestore)
        assertFalse(current.canEdit)
    }

    @Test
    fun restoreKeepsEverySafetyGuardForMalformedProfiles() {
        val current = state(invalidProfile)
        listOf(
            current.copy(ownerUser = false),
            current.copy(busy = true),
            current.copy(selectionLoading = true),
            current.copy(selected = null),
            current.copy(selected = app.copy(abi = AppAbi.Unsupported32Bit)),
            current.copy(status = null),
            current.copy(status = HelperReply.parse(0, "STATUS=ok\nPROTOCOL_VERSION=2\nMODULE_STATE=enabled\n")),
            current.copy(status = HelperReply.parse(0, "STATUS=ok\nPROTOCOL_VERSION=1\nMODULE_STATE=disabled\n"))
        ).forEach { assertFalse(it.toString(), it.canRestore) }
    }

    @Test
    fun unrelatedHelperErrorsCannotEnableRestore() {
        listOf("root_required", "package_missing", "invalid_package", "shared_uid", "system_app", "package_manager_failed").forEach {
            assertFalse(it, state(HelperReply.parse(1, "STATUS=error\nERROR_CODE=$it\n")).canRestore)
        }
        assertFalse(state(HelperReply.parse(0, "STATUS=ok\nENABLED=false\n")).canRestore)
        assertTrue(state(HelperReply.parse(0, "STATUS=ok\nENABLED=true\n")).canRestore)
    }

    @Test
    fun stagedOrMissingModulesCannotChangeProfiles() {
        val configured = state(HelperReply.parse(0, "STATUS=ok\nENABLED=true\n"))
        listOf(
            HelperReply.parse(0, "STATUS=ok\nERROR_CODE=module_pending_reboot\nPROTOCOL_VERSION=1\nMODULE_STATE=pending_reboot\nRUNTIME=pending_reboot\n"),
            HelperReply.parse(1, "STATUS=error\nERROR_CODE=module_missing\nPROTOCOL_VERSION=1\nMODULE_STATE=missing\nRUNTIME=unavailable\n")
        ).forEach {
            val current = configured.copy(status = it)
            assertFalse(current.moduleReady)
            assertFalse(current.canEdit)
            assertFalse(current.canRestore)
        }
    }
}
