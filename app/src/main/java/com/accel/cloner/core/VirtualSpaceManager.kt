package com.accel.cloner.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VirtualSpaceManager(private val context: Context) {
    private val TAG = "VirtualSpaceManager"
    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("virtual_space", Context.MODE_PRIVATE)

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

    suspend fun getClonedApps(): List<ClonedApp> = withContext(Dispatchers.IO) {
        prefs.all.keys
            .filter { it.startsWith("clone_") }
            .mapNotNull { key ->
                prefs.getString(key, null) ?: return@mapNotNull null
                val parts = key.removePrefix("clone_").split("_")
                val packageName = parts.dropLast(1).joinToString("_")
                val index = parts.last().toIntOrNull() ?: 0
                val vPath = VirtualConfig.virtualDataPath(context, packageName, index)
                try {
                    val pi = pm.getPackageInfo(packageName, 0)
                    ClonedApp(
                        packageName = packageName,
                        appName = pi.applicationInfo?.loadLabel(pm)?.toString() ?: packageName,
                        icon = pi.applicationInfo?.loadIcon(pm),
                        cloneIndex = index,
                        virtualDataPath = vPath,
                        sizeBytes = VirtualFileSystem.getVirtualSize(context, packageName, index)
                    )
                } catch (e: Exception) {
                    ClonedApp(packageName, packageName, null, index, vPath, 0L)
                }
            }
    }

    suspend fun cloneApp(pkg: String, cloneIndex: Int = 1): VirtualSpaceResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Cloning $pkg index=$cloneIndex")
            try {
                val ok = VirtualFileSystem.initVirtualSpace(context, pkg, cloneIndex)
                if (!ok) return@withContext VirtualSpaceResult.Failure("Failed to create virtual dirs")

                val sourceApk = pm.getApplicationInfo(pkg, 0).sourceDir
                val destApk = File(VirtualConfig.pluginApkPath(context, pkg, cloneIndex))
                if (!destApk.exists()) {
                    VirtualFileSystem.copyFile(File(sourceApk), destApk)
                }

                VirtualRootSim.exec("chmod -R 0771 \"${VirtualConfig.virtualDataPath(context, pkg, cloneIndex)}\"")
                prefs.edit().putString("clone_${pkg}_$cloneIndex", pkg).apply()

                val path = VirtualConfig.virtualDataPath(context, pkg, cloneIndex)
                Log.d(TAG, "Clone success: $pkg -> $path")
                VirtualSpaceResult.Success(pkg, cloneIndex, path)
            } catch (e: Exception) {
                Log.e(TAG, "Clone failed: ${e.message}")
                VirtualSpaceResult.Failure(e.message ?: "Unknown error")
            }
        }

    suspend fun removeClone(pkg: String, cloneIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val cleared = VirtualFileSystem.clearVirtualSpace(context, pkg, cloneIndex)
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
