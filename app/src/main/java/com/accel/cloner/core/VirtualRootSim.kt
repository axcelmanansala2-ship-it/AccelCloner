package com.accel.cloner.core

import android.util.Log

/**
 * Virtual Root Simulator
 *
 * Simulates root/su access WITHIN the virtual space without requiring
 * actual device root. When an app running inside our virtual space
 * calls `su`, we intercept it and return uid=0 responses.
 *
 * How it works:
 * 1. We provide a fake `su` binary inside the virtual space
 * 2. PATH is manipulated so our su is found first
 * 3. Our su accepts any command and executes it with our app's permissions
 * 4. chmod/chown responses are simulated (we manage our own virtual fs)
 * 5. For GG: /proc/<pid>/mem access is handled via our daemon bridge
 */
object VirtualRootSim {
    private const val TAG = "VirtualRootSim"

    data class SuResult(val exitCode: Int, val stdout: String, val stderr: String = "")

    /**
     * Execute a command in the virtual root context.
     * We have full access to our own virtual space dirs,
     * so most operations succeed without device root.
     */
    fun exec(command: String): SuResult {
        return try {
            // Intercept virtual-root-specific commands
            when {
                command.startsWith("id") ->
                    SuResult(0, "uid=0(root) gid=0(root) groups=0(root)")

                command.startsWith("chmod") -> {
                    handleVirtualChmod(command)
                }

                command.startsWith("chown") -> {
                    SuResult(0, "") // Virtual: we own our own dirs
                }

                command.startsWith("setenforce") ->
                    SuResult(0, "") // Simulated: no-op in virtual space

                command.startsWith("mkdir") -> {
                    handleVirtualMkdir(command)
                }

                command.startsWith("rm ") || command.startsWith("rm -") -> {
                    handleVirtualRm(command)
                }

                else -> {
                    // Pass to shell for actual execution (no-root commands work fine)
                    val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val out = proc.inputStream.bufferedReader().readText()
                    val err = proc.errorStream.bufferedReader().readText()
                    val code = proc.waitFor()
                    SuResult(code, out, err)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "exec[$command]: ${e.message}")
            SuResult(-1, "", e.message ?: "error")
        }
    }

    private fun handleVirtualChmod(command: String): SuResult {
        // chmod [flags] mode path — within our virtual space we can set perms
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val code = proc.waitFor()
            SuResult(code, "")
        } catch (e: Exception) {
            SuResult(0, "") // Simulate success even if chmod fails (virtual space)
        }
    }

    private fun handleVirtualMkdir(command: String): SuResult {
        val path = command.replace(Regex("mkdir\\s+-p\\s+"), "").trim().removeSurrounding("\"")
        return try {
            java.io.File(path).mkdirs()
            SuResult(0, "")
        } catch (e: Exception) {
            SuResult(0, "") // Simulate success
        }
    }

    private fun handleVirtualRm(command: String): SuResult {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val code = proc.waitFor()
            SuResult(code, "")
        } catch (e: Exception) {
            SuResult(1, "", e.message ?: "")
        }
    }

    /** Check if virtual root is available (always true — we are our own root) */
    fun isVirtualRootAvailable(): Boolean = true

    /** Used by GG bridge to report uid=0 inside the virtual space */
    fun getVirtualUid(): Int = 0
}
