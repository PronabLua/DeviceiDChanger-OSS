package io.github.pronablua.deviceidchanger

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class CommandOutput(val exitCode: Int, val output: String, val timedOut: Boolean, val truncated: Boolean)

object CommandProcess {
    private const val maximumOutput = 65536
    private const val drainBudget = 16384

    fun run(command: List<String>, timeoutMillis: Long): CommandOutput {
        require(command.isNotEmpty() && timeoutMillis > 0)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val started = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        var truncated = false

        fun drain(limit: Int) {
            var remaining = limit
            while (remaining > 0) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val available = process.inputStream.available()
                if (available <= 0) return
                val count = process.inputStream.read(buffer, 0, minOf(buffer.size, available, remaining))
                if (count <= 0) return
                val keep = minOf(count, maximumOutput - output.size())
                if (keep > 0) output.write(buffer, 0, keep)
                if (keep < count) truncated = true
                remaining -= count
            }
        }

        fun result(exitCode: Int, timedOut: Boolean) = CommandOutput(exitCode, output.toString(StandardCharsets.UTF_8.name()), timedOut, truncated)

        try {
            while (true) {
                val remaining = timeoutNanos - (System.nanoTime() - started)
                if (remaining <= 0) return result(-1, true)
                drain(drainBudget)
                if (process.waitFor(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS)) {
                    drain(maximumOutput)
                    return result(process.exitValue(), false)
                }
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }
}
