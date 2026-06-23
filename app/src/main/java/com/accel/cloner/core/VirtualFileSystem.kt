package com.accel.cloner.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Virtual File System — redirects app data paths into the cloner's
 * own virtual space WITHOUT requiring device root.
 *
 * Strategy:
 *  - Each cloned app gets an isolated dir under the app's own externalFilesDir:
 *      <externalFilesDir>/virtual_space/<pkg>_<index>/
 *  - No bind-mounts or actual root needed — app owns this path.
 */
object VirtualFileSystem {
    private const val TAG = "VirtualFileSystem"

    fun initVirtualSpace(context: Context, pkg: String, cloneIndex: Int): Boolean {
        val base = VirtualConfig.virtualDataPath(context, pkg, cloneIndex)
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

    fun clearVirtualSpace(context: Context, pkg: String, cloneIndex: Int): Boolean {
        val base = File(VirtualConfig.virtualDataPath(context, pkg, cloneIndex))
        return if (base.exists()) base.deleteRecursively() else true
    }

    fun getVirtualSize(context: Context, pkg: String, cloneIndex: Int): Long {
        val base = File(VirtualConfig.virtualDataPath(context, pkg, cloneIndex))
        return if (base.exists()) base.walkTopDown().sumOf { it.length() } else 0L
    }

    fun exists(context: Context, pkg: String, cloneIndex: Int): Boolean =
        File(VirtualConfig.virtualDataPath(context, pkg, cloneIndex)).exists()

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
