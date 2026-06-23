package com.accel.cloner.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.accel.cloner.core.VirtualFileSystem.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val sourceApk: String,
    val sizeBytes: Long = 0L
)

data class ClonedApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val cloneIndex: Int,
    val virtualDataPath: String,
    val sizeBytes: Long
)

class VirtualSpaceManager(private val context: Context) {
    private val TAG = "VirtualSpaceManager"
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("virtual_space", Context.MODE_PRIVATE)

    // ── Installed apps ────────────────────────────────────────────────────────

    suspend fun getInstallableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.packageName != VirtualConfig.CLONER_PKG }
            .filter { (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }
            .map { pkg ->
                AppInfo(
                    packageName = pkg.packageName,
                    appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName,
                    icon = pkg.applicationInfo?.loadIcon(pm),
                    versionName = pkg.versionName ?: "?",
                    versionCode = pkg.longVersionCode,
                    isSystemApp = false,
                    sourceApk = pkg.applicationInfo?.sourceDir ?: "",
                    sizeBytes = File(pkg.applicationInfo?.sourceDir ?: "").length()
                )
            }
            .sortedBy { it.appName }
    }

    // ── Cloned apps ───────────────────────────────────────────────────────────

    suspend fun getClonedApps(): List<ClonedApp> = withContext(Dispatchers.IO) {
        prefs.all.keys
            .filter { it.startsWith("clone_") }
            .mapNotNull { key ->
                val pkg = prefs.getString(key, null) ?: return@mapNotNull null
                val parts = key.removePrefix("clone_").split("_")
                val packageName = parts.dropLast(1).joinToString("_")
                val index = parts.last().toIntOrNull() ?: 0
                val vPath = VirtualConfig.virtualDataPath(packageName, index)
                try {
                    val pi = pm.getPackageInfo(packageName, 0)
                    ClonedApp(
                        packageName = packageName,
                        appName = pi.applicationInfo?.loadLabel(pm)?.toString() ?: packageName,
                        icon = pi.applicationInfo?.loadIcon(pm),
                        cloneIndex = index,
                        virtualDataPath = vPath,
                        sizeBytes = VirtualFileSystem.getVirtualSize(packageName, index)
                    )
                } catch (e: Exception) {
                    ClonedApp(packageName, packageName, null, index, vPath, 0L)
                }
            }
    }

    // ── Clone an app ──────────────────────────────────────────────────────────

    suspend fun cloneApp(pkg: String, cloneIndex: Int = 1): VirtualSpaceResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Cloning $pkg index=$cloneIndex")
            try {
                // 1. Init virtual file system dirs
                val ok = VirtualFileSystem.initVirtualSpace(pkg, cloneIndex)
                if (!ok) return@withContext VirtualSpaceResult.Failure("Failed to create virtual dirs")

                // 2. Copy APK into virtual space (for DexClassLoader later)
                val sourceApk = pm.getApplicationInfo(pkg, 0).sourceDir
                val destApk = File(VirtualConfig.pluginApkPath(pkg, cloneIndex))
                if (!destApk.exists()) {
                    VirtualFileSystem.copyFile(File(sourceApk), destApk)
                }

                // 3. Apply virtual chmod (no root needed — we own these dirs)
                VirtualRootSim.exec("chmod -R 0771 \"${VirtualConfig.virtualDataPath(pkg, cloneIndex)}\"")

                // 4. Save record
                prefs.edit().putString("clone_${pkg}_$cloneIndex", pkg).apply()

                val path = VirtualConfig.virtualDataPath(pkg, cloneIndex)
                Log.d(TAG, "Clone success: $pkg -> $path")
                VirtualSpaceResult.Success(pkg, cloneIndex, path)
            } catch (e: Exception) {
                Log.e(TAG, "Clone failed: ${e.message}")
                VirtualSpaceResult.Failure(e.message ?: "Unknown error")
            }
        }

    // ── Remove clone ──────────────────────────────────────────────────────────

    suspend fun removeClone(pkg: String, cloneIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val cleared = VirtualFileSystem.clearVirtualSpace(pkg, cloneIndex)
            if (cleared) prefs.edit().remove("clone_${pkg}_$cloneIndex").apply()
            cleared
        }

    fun isCloned(pkg: String): Boolean =
        prefs.all.keys.any { it.startsWith("clone_${pkg}_") }

    fun getNextCloneIndex(pkg: String): Int {
        for (i in 1..VirtualConfig.MAX_CLONES) {
            if (!prefs.contains("clone_${pkg}_$i")) return i
        }
        return -1
    }
}

sealed class VirtualSpaceResult {
    data class Success(val pkg: String, val cloneIndex: Int, val path: String) : VirtualSpaceResult()
    data class Failure(val reason: String) : VirtualSpaceResult()
}
