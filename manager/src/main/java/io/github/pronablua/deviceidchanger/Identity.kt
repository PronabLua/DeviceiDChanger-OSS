package io.github.pronablua.deviceidchanger

import java.security.SecureRandom
import java.util.Locale

object Identity {
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val idPattern = Regex("[0-9a-fA-F]{16}")

    fun validPackage(packageName: String): Boolean = packageName.length <= 255 && packagePattern.matches(packageName)

    fun validId(identifier: String): Boolean = idPattern.matches(identifier)

    fun cleanId(identifier: String): String = identifier.trim().lowercase(Locale.ROOT)

    fun randomId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

data class HelperReply(val exitCode: Int, val fields: Map<String, String>, val fallback: String = "") {
    val successful: Boolean get() = exitCode == 0 && fields["status"] == "ok"
    val saved: Boolean get() = fields["saved"] == "true"
    val restarted: Boolean get() = fields["restarted"] == "true"
    val rolledBack: Boolean get() = fields["rolled_back"] == "true"
    val restoredLaunch: Boolean get() = fields["restored_launch"] == "true"
    val message: String get() = fields["message"].orEmpty().ifBlank { fallback }

    companion object {
        fun parse(exitCode: Int, output: String): HelperReply {
            val fields = linkedMapOf<String, String>()
            output.lineSequence().forEach { line ->
                val separator = line.indexOf('=')
                if (separator > 0) {
                    val key = line.substring(0, separator).trim().lowercase(Locale.ROOT)
                    if (key.matches(Regex("[a-z_]+"))) {
                        fields[key] = line.substring(separator + 1).trim()
                    }
                }
            }
            val fallback = if (fields["status"] == "ok" || fields["status"] == "error") "" else output.trim().take(300)
            return HelperReply(exitCode, fields, fallback)
        }
    }
}

object HelperCommand {
    private const val helper = "/system/bin/sh /data/adb/modules/deviceidchanger_native/control.sh"

    fun build(action: String, packageName: String? = null, identifier: String? = null): String {
        require(action in setOf("status", "get", "apply", "restore", "repair"))
        if (action in setOf("status", "repair")) {
            require(packageName == null && identifier == null)
            return guard("$helper $action", action)
        }
        require(packageName != null && Identity.validPackage(packageName))
        if (action == "apply") {
            require(identifier != null && Identity.validId(identifier))
            return guard("$helper apply $packageName ${Identity.cleanId(identifier)}", action)
        }
        require(identifier == null)
        return guard("$helper $action $packageName", action)
    }

    private fun guard(invocation: String, action: String): String {
        val pendingStatus = if (action == "status") "ok" else "error"
        val pendingExit = if (action == "status") 0 else 1
        val pending = "printf '%s\\n' 'STATUS=$pendingStatus' 'ERROR_CODE=module_pending_reboot' 'PROTOCOL_VERSION=1' 'MODULE_STATE=pending_reboot' 'RUNTIME=pending_reboot' 'USER_ID=0' 'SAVED=false' 'STOPPED=false' 'RESTARTED=false' 'MESSAGE=The module is staged for installation. Restart your phone once to finish setup.'; exit $pendingExit"
        val missing = "printf '%s\\n' 'STATUS=error' 'ERROR_CODE=module_missing' 'PROTOCOL_VERSION=1' 'MODULE_STATE=missing' 'RUNTIME=unavailable' 'USER_ID=0' 'SAVED=false' 'STOPPED=false' 'RESTARTED=false' 'MESSAGE=Install the Device ID Changer module ZIP in your root manager, then restart your phone to finish setup.'; exit 1"
        return "if [ -f /data/adb/modules_update/deviceidchanger_native/control.sh ]; then $pending; elif [ -f /data/adb/modules/deviceidchanger_native/control.sh ]; then exec $invocation; else $missing; fi"
    }
}

class SelectionRequests {
    private var revision = 0L

    fun next(): Long = ++revision

    fun isCurrent(request: Long): Boolean = request == revision
}
