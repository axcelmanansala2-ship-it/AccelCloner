package com.accel.cloner.core

import android.util.Log
import java.io.File

/**
 * Virtual File System — redirects app data paths into the cloner's
 * own virtual space WITHOUT requiring device root.
 *
 * Strategy:
 *  - Each cloned app gets an isolated dir under:
 *      /sdcard/Android/data/com.accel.cloner/virtual_space/<pkg>_<index>/
 *  - DexClassLoader loads the app's DEX from this dir
 *  - SharedPreferences, databases, files all go here
 *  - No bind-mounts or actual root needed
 */
object VirtualFileSystem {
    private const val TAG = "VirtualFileSystem"

    fun initVirtualSpace(pkg: String, cloneIndex: Int): Boolean {
        val base = VirtualConfig.virtualDataPath(pkg, cloneIndex)
        val dirs = listOf(
            base,
            "$base/files",
            "$base/databases",
            "$base/shared_prefs",
            "$base/cache",
            "$base/lib",
            "$base/odex"
        )
        var ok = true
        for (path in dirs) {
            val dir = File(path)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create: $path")
                    ok = false
                } else {
                    Log.d(TAG, "Created: $path")
                }
            }
        }
        return ok
    }

    fun clearVirtualSpace(pkg: String, cloneIndex: Int): Boolean {
        val base = File(VirtualConfig.virtualDataPath(pkg, cloneIndex))
        return if (base.exists()) base.deleteRecursively() else true
    }

    fun getVirtualSize(pkg: String, cloneIndex: Int): Long {
        val base = File(VirtualConfig.virtualDataPath(pkg, cloneIndex))
        return if (base.exists()) base.walkTopDown().sumOf { it.length() } else 0L
    }

    fun exists(pkg: String, cloneIndex: Int): Boolean =
        File(VirtualConfig.virtualDataPath(pkg, cloneIndex)).exists()

    fun copyFile(src: File, dst: File): Boolean {
        return try {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyFile ${src.path} -> ${dst.path}: ${e.message}")
            false
        }
    }

    fun Long.formatSize(): String = when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        else -> String.format("%.1f MB", this / (1024.0 * 1024))
    }
}
