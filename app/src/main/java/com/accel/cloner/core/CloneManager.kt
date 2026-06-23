package com.accel.cloner.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.accel.cloner.root.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CloneManager(private val context: Context) {

    private val TAG = "CloneManager"
    private val pm: PackageManager = context.packageManager

    // ── List all user-installed apps ─────────────────────────────────────────

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val clonedPkgs = getClonedPackages()
        pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.packageName != CloneConstants.CLONER_PKG }
            .map { pkg ->
                AppInfo(
                    packageName = pkg.packageName,
                    appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName,
                    icon = pkg.applicationInfo?.loadIcon(pm),
                    versionName = pkg.versionName ?: "unknown",
                    versionCode = pkg.longVersionCode,
                    isSystemApp = (pkg.applicationInfo?.flags
                        ?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0,
                    isCloned = clonedPkgs.contains(pkg.packageName)
                )
            }
            .filter { !it.isSystemApp }
            .sortedBy { it.appName }
    }

    // ── Clone an app ──────────────────────────────────────────────────────────

    suspend fun cloneApp(packageName: String, cloneIndex: Int = 1): CloneResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Cloning $packageName index=$cloneIndex")

            if (!RootUtils.isRooted()) {
                return@withContext CloneResult.Failure("Root access required for cloning")
            }

            val clonePath = getClonePath(packageName, cloneIndex)
            val cloneDir = File(clonePath)

            // Create clone data directory with proper permissions
            if (!cloneDir.exists()) {
                val created = RootUtils.mkdirWithPerms(
                    path = clonePath,
                    mode = CloneConstants.DIR_PERMISSION,
                    uid = 1000,
                    gid = 1000
                )
                if (!created) return@withContext CloneResult.Failure("Failed to create clone directory")
            }

            // Create internal subdirs (files, databases, shared_prefs, cache)
            val subdirs = listOf("files", "databases", "shared_prefs", "cache", "lib")
            for (sub in subdirs) {
                RootUtils.mkdirWithPerms(
                    path = "$clonePath/$sub",
                    mode = CloneConstants.DIR_PERMISSION
                )
            }

            // Copy original app data if it exists
            val originalData = "${CloneConstants.ANDROID_DATA}/$packageName"
            if (File(originalData).exists()) {
                RootUtils.copyDir(originalData, clonePath)
                Log.d(TAG, "Copied original data from $originalData to $clonePath")
            }

            // Apply chmod recursively
            val chmodOk = RootUtils.chmod(clonePath, CloneConstants.DIR_PERMISSION, recursive = true)
            if (!chmodOk) Log.w(TAG, "chmod failed on $clonePath")

            // Get and apply original app UID/GID for proper permission
            val originalUid = RootUtils.getPackageUid(packageName)
            if (originalUid != null) {
                RootUtils.chown(clonePath, originalUid, originalUid, recursive = true)
                Log.d(TAG, "Applied UID $originalUid to clone dir")
            }

            // Save clone record
            saveCloneRecord(packageName, cloneIndex, clonePath)

            Log.d(TAG, "Clone success: $packageName -> $clonePath")
            CloneResult.Success(packageName, cloneIndex, clonePath)
        }

    // ── Remove a clone ────────────────────────────────────────────────────────

    suspend fun removeClone(packageName: String, cloneIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val clonePath = getClonePath(packageName, cloneIndex)
            val (code, _) = RootUtils.exec("rm -rf \"$clonePath\"")
            if (code == 0) removeCloneRecord(packageName, cloneIndex)
            code == 0
        }

    // ── Data path utilities ───────────────────────────────────────────────────

    fun getClonePath(packageName: String, cloneIndex: Int): String =
        "${CloneConstants.CLONE_DATA_BASE}/${packageName}_clone$cloneIndex"

    fun getCloneDataPath(packageName: String, cloneIndex: Int): String =
        "${getClonePath(packageName, cloneIndex)}/files"

    // ── Clone records (stored in prefs) ───────────────────────────────────────

    private fun getPrefs() = context.getSharedPreferences("clones", Context.MODE_PRIVATE)

    private fun saveCloneRecord(pkg: String, index: Int, path: String) {
        getPrefs().edit().putString("clone_${pkg}_$index", path).apply()
    }

    private fun removeCloneRecord(pkg: String, index: Int) {
        getPrefs().edit().remove("clone_${pkg}_$index").apply()
    }

    fun getClonedPackages(): Set<String> {
        val prefs = getPrefs()
        return prefs.all.keys
            .filter { it.startsWith("clone_") }
            .map { it.removePrefix("clone_").substringBeforeLast("_") }
            .toSet()
    }

    fun getCloneRecords(): Map<String, String> =
        getPrefs().all
            .filter { it.key.startsWith("clone_") }
            .mapValues { it.value.toString() }
}

sealed class CloneResult {
    data class Success(val packageName: String, val cloneIndex: Int, val clonePath: String) : CloneResult()
    data class Failure(val reason: String) : CloneResult()
}
