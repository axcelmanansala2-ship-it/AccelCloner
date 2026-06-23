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
                val clonePkg = prefs.getString("clonepkg_${packageName}_$index", "") ?: ""
                try {
                    val pi = pm.getPackageInfo(packageName, 0)
                    ClonedApp(
                        packageName = packageName,
                        appName = pi.applicationInfo?.loadLabel(pm)?.toString() ?: packageName,
                        icon = pi.applicationInfo?.loadIcon(pm),
                        cloneIndex = index,
                        virtualDataPath = vPath,
                        sizeBytes = VirtualFileSystem.getVirtualSize(context, packageName, index),
                        clonedPackageName = clonePkg
                    )
                } catch (e: Exception) {
                    ClonedApp(packageName, packageName, null, index, vPath, 0L, clonePkg)
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
                val workDir = File(VirtualConfig.virtualDataPath(context, pkg, cloneIndex), "install_work")

                val result = CloneInstallManager.install(context, pkg, cloneIndex, sourceApk, workDir)
                when (result) {
                    is CloneInstallResult.Success -> {
                        prefs.edit()
                            .putString("clone_${pkg}_$cloneIndex", pkg)
                            .putString("clonepkg_${pkg}_$cloneIndex", result.clonePkg)
                            .apply()
                        val path = VirtualConfig.virtualDataPath(context, pkg, cloneIndex)
                        Log.d(TAG, "Clone success: $pkg → ${result.clonePkg}")
                        VirtualSpaceResult.Success(pkg, cloneIndex, path, result.clonePkg)
                    }
                    is CloneInstallResult.Failure ->
                        VirtualSpaceResult.Failure(result.reason)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clone failed: ${e.message}")
                VirtualSpaceResult.Failure(e.message ?: "Unknown error")
            }
        }

    suspend fun removeClone(pkg: String, cloneIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val clonePkg = prefs.getString("clonepkg_${pkg}_$cloneIndex", null)
            if (!clonePkg.isNullOrEmpty()) {
                CloneInstallManager.uninstall(context, clonePkg)
            }
            val cleared = VirtualFileSystem.clearVirtualSpace(context, pkg, cloneIndex)
            if (cleared) {
                prefs.edit()
                    .remove("clone_${pkg}_$cloneIndex")
                    .remove("clonepkg_${pkg}_$cloneIndex")
                    .apply()
            }
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
    data class Success(
        val pkg: String,
        val cloneIndex: Int,
        val path: String,
        val clonePkg: String
    ) : VirtualSpaceResult()
    data class Failure(val reason: String) : VirtualSpaceResult()
}
