package io.github.pronablua.deviceidchanger

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RootHelper {
    private val commandLock = Mutex()

    suspend fun run(action: String, packageName: String? = null, identifier: String? = null): HelperReply = commandLock.withLock {
        val command = HelperCommand.build(action, packageName, identifier)
        val timeout = when (action) {
            "apply" -> 260L
            "restore" -> 150L
            "repair" -> 210L
            else -> 60L
        }
        val started = System.nanoTime()
        var reply: HelperReply? = null
        var failureCode = "client_failure"
        if (BuildConfig.DEBUG) Log.d("DeviceIdChangerUI", HelperDiagnostics.started(action, packageName))
        try {
            val result = runInterruptible(Dispatchers.IO) {
                CommandProcess.run(listOf("su", "-c", command), timeout * 1000)
            }
            reply = when {
                result.timedOut -> HelperReply(-1, mapOf("status" to "error", "error_code" to "client_timeout"), "The root request timed out. Check root permission, then refresh before retrying.")
                result.truncated -> HelperReply(-1, mapOf("status" to "error", "error_code" to "client_output_limit"), "The root response was too large to validate. Refresh before retrying.")
                else -> HelperReply.parse(result.exitCode, result.output)
            }
            reply
        } catch (failure: IOException) {
            reply = HelperReply(-1, mapOf("status" to "error", "error_code" to "root_connection_failed"), "The root connection is unavailable. Check root permission and refresh to check whether a change was saved.")
            reply
        } catch (failure: CancellationException) {
            failureCode = "client_cancelled"
            throw failure
        } finally {
            if (BuildConfig.DEBUG) {
                val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                val outcome = reply ?: HelperReply(-1, mapOf("error_code" to failureCode))
                Log.d("DeviceIdChangerUI", HelperDiagnostics.finished(action, packageName, duration, outcome))
            }
        }
    }
}
