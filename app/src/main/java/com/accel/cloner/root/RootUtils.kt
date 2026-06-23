package com.accel.cloner.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {
    private const val TAG = "RootUtils"

    /**
     * Check if device has root access.
     */
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    /**
     * Execute a shell command as root.
     * Returns Pair(exitCode, output).
     */
    fun exec(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val code = process.waitFor()
            Log.d(TAG, "exec[$command] -> code=$code out=$output err=$error")
            Pair(code, output.ifBlank { error })
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}")
            Pair(-1, e.message ?: "unknown error")
        }
    }

    /**
     * chmod on a path (e.g. chmod 777 /path/to/dir)
     */
    fun chmod(path: String, mode: String, recursive: Boolean = false): Boolean {
        val flag = if (recursive) "-R " else ""
        val (code, out) = exec("chmod $flag$mode \"$path\"")
        Log.d(TAG, "chmod $flag$mode $path -> $code $out")
        return code == 0
    }

    /**
     * chown on a path (e.g. chown 1000:1000 /path)
     */
    fun chown(path: String, ownerUid: Int, groupGid: Int, recursive: Boolean = false): Boolean {
        val flag = if (recursive) "-R " else ""
        val (code, out) = exec("chown $flag$ownerUid:$groupGid \"$path\"")
        Log.d(TAG, "chown $flag$ownerUid:$groupGid $path -> $code $out")
        return code == 0
    }

    /**
     * Create directory and apply permissions + ownership.
     */
    fun mkdirWithPerms(path: String, mode: String = "0771", uid: Int = 1000, gid: Int = 1000): Boolean {
        val (mkCode, _) = exec("mkdir -p \"$path\"")
        if (mkCode != 0) return false
        return chmod(path, mode) && chown(path, uid, gid)
    }

    /**
     * Bind-mount src onto dst (requires root).
     * Used to redirect app data paths.
     */
    fun bindMount(src: String, dst: String): Boolean {
        val (code, out) = exec("mount --bind \"$src\" \"$dst\"")
        Log.d(TAG, "bindMount $src -> $dst : $code $out")
        return code == 0
    }

    /**
     * Unmount a previously bind-mounted path.
     */
    fun umount(path: String): Boolean {
        val (code, out) = exec("umount \"$path\"")
        Log.d(TAG, "umount $path : $code $out")
        return code == 0
    }

    /**
     * Symlink src to dst.
     */
    fun symlink(src: String, dst: String): Boolean {
        exec("rm -rf \"$dst\"")
        val (code, out) = exec("ln -s \"$src\" \"$dst\"")
        Log.d(TAG, "symlink $src -> $dst : $code $out")
        return code == 0
    }

    /**
     * Copy a directory tree preserving permissions (cp -a).
     */
    fun copyDir(src: String, dst: String): Boolean {
        val (code, out) = exec("cp -a \"$src\" \"$dst\"")
        Log.d(TAG, "copyDir $src -> $dst : $code $out")
        return code == 0
    }

    /**
     * Get the UID of an installed package via `dumpsys package`.
     */
    fun getPackageUid(packageName: String): Int? {
        val (_, out) = exec("dumpsys package $packageName | grep userId")
        val match = Regex("userId=(\\d+)").find(out)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
