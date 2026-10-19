package io.github.pronablua.deviceidchanger

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandProcessTest {
    private fun command(mode: String): List<String> {
        val executable = if (requireNotNull(System.getProperty("os.name")).startsWith("Windows")) "java.exe" else "java"
        val java = File(File(requireNotNull(System.getProperty("java.home")), "bin"), executable).absolutePath
        val classes = File(requireNotNull(CommandProcessFixture::class.java.protectionDomain?.codeSource?.location).toURI()).absolutePath
        val kotlin = File(requireNotNull(Unit::class.java.protectionDomain?.codeSource?.location).toURI()).absolutePath
        return listOf(java, "-cp", classes + File.pathSeparator + kotlin, CommandProcessFixture::class.java.name, mode)
    }

    @Test
    fun inheritedOutputDoesNotKeepCompletedParentOpen() {
        val started = System.nanoTime()
        val output = CommandProcess.run(command("spawn"), 8000)
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertFalse(output.timedOut)
        assertEquals(0, output.exitCode)
        assertTrue(output.output.contains("STATUS=ok"))
        assertTrue("A child holding stdout delayed completion for ${elapsed}ms", elapsed < 3000)
    }

    @Test
    fun silentProcessCannotOutliveItsDeadline() {
        val started = System.nanoTime()
        val output = CommandProcess.run(command("sleep"), 300)
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue(output.timedOut)
        assertTrue("Process timeout took ${elapsed}ms", elapsed < 2500)
    }

    @Test
    fun excessOutputIsDrainedButNeverAcceptedAsComplete() {
        val output = CommandProcess.run(command("flood"), 5000)
        assertFalse(output.timedOut)
        assertEquals(0, output.exitCode)
        assertTrue(output.truncated)
        assertEquals(65536, output.output.length)
    }
}

object CommandProcessFixture {
    @JvmStatic
    fun main(arguments: Array<String>) {
        when (arguments.single()) {
            "spawn" -> {
                val executable = if (requireNotNull(System.getProperty("os.name")).startsWith("Windows")) "java.exe" else "java"
                val java = File(File(requireNotNull(System.getProperty("java.home")), "bin"), executable).absolutePath
                ProcessBuilder(java, "-cp", requireNotNull(System.getProperty("java.class.path")), CommandProcessFixture::class.java.name, "child").inheritIO().start()
                println("STATUS=ok")
            }
            "child" -> {
                Thread.sleep(4000)
                println("CHILD=finished")
            }
            "sleep" -> Thread.sleep(10000)
            "flood" -> print("x".repeat(100000))
            else -> error("Unknown fixture")
        }
    }
}
