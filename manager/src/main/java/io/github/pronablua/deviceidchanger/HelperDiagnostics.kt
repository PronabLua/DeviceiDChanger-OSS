package io.github.pronablua.deviceidchanger

object HelperDiagnostics {
    private val token = Regex("[a-z][a-z0-9_]{0,63}")

    fun started(action: String, packageName: String?): String = "helper_start action=${safeAction(action)} package=${safePackage(packageName)}"

    fun finished(action: String, packageName: String?, durationMillis: Long, reply: HelperReply): String {
        val errorCode = reply.fields["error_code"]?.takeIf(token::matches) ?: "none"
        val revision = reply.fields["config_revision"]?.takeIf(Identity::validId) ?: "unknown"
        return listOf(
            "helper_end",
            "action=${safeAction(action)}",
            "package=${safePackage(packageName)}",
            "duration_ms=${durationMillis.coerceAtLeast(0)}",
            "exit_code=${reply.exitCode}",
            "error_code=$errorCode",
            "saved=${flag(reply, "saved")}",
            "stopped=${flag(reply, "stopped")}",
            "restarted=${flag(reply, "restarted")}",
            "rolled_back=${flag(reply, "rolled_back")}",
            "restored_launch=${flag(reply, "restored_launch")}",
            "revision=$revision"
        ).joinToString(" ")
    }

    private fun safeAction(action: String): String = action.takeIf { it in setOf("status", "get", "apply", "restore", "repair") } ?: "unknown"

    private fun safePackage(packageName: String?): String = packageName?.takeIf(Identity::validPackage) ?: "none"

    private fun flag(reply: HelperReply, name: String): String = reply.fields[name]?.takeIf { it == "true" || it == "false" } ?: "unknown"
}
