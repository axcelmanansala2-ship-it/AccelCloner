package com.accel.cloner.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestrates the full "clone + install" pipeline:
 *  1. Build a unique clone package name
 *  2. Repackage the source APK (new package name in binary manifest)
 *  3. V1-sign the repackaged APK
 *  4. Install via PackageInstaller → system shows the "Install" dialog
 *
 * [reinstall] can be called from the UI at any time to re-show the install dialog
 * using the already-built signed APK, or re-build it if missing.
 */
object CloneInstallManager {
    private const val TAG = "CloneInstallManager"

    // ── Full pipeline (called once during cloneApp) ───────────────────────────

    suspend fun install(
        context: Context,
        originalPkg: String,
        cloneIndex: Int,
        sourceApkPath: String,
        workDir: File
    ): CloneInstallResult = withContext(Dispatchers.IO) {
        val clonePkg = buildClonePkg(originalPkg, cloneIndex)
        Log.d(TAG, "Clone install: $originalPkg → $clonePkg")
        try {
            workDir.mkdirs()
            val repackaged = File(workDir, "repackaged.apk")
            val signed     = File(workDir, "signed.apk")

            Log.d(TAG, "Repackaging…")
            ApkRepackager.repackage(File(sourceApkPath), repackaged, originalPkg, clonePkg)

            Log.d(TAG, "Signing…")
            ApkV1Signer.sign(repackaged, signed)
            repackaged.delete()

            Log.d(TAG, "Installing (${signed.length() / 1024} KB)…")
            pushToInstaller(context, signed)

            CloneInstallResult.Success(clonePkg)
        } catch (e: Exception) {
            Log.e(TAG, "Clone install failed", e)
            CloneInstallResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Called from the "Install & Open" button.
     * Re-uses the already-signed APK if present; otherwise rebuilds from scratch.
     */
    suspend fun reinstall(
        context: Context,
        originalPkg: String,
        cloneIndex: Int,
        workDir: File
    ): CloneInstallResult = withContext(Dispatchers.IO) {
        val signed = File(workDir, "signed.apk")
        if (signed.exists() && signed.length() > 0) {
            Log.d(TAG, "Re-using existing signed APK (${signed.length() / 1024} KB)…")
            try {
                pushToInstaller(context, signed)
                CloneInstallResult.Success(buildClonePkg(originalPkg, cloneIndex))
            } catch (e: Exception) {
                Log.e(TAG, "Reinstall failed", e)
                CloneInstallResult.Failure(e.message ?: "Reinstall failed")
            }
        } else {
            // Signed APK gone — rebuild from original
            val sourceApk = try {
                context.packageManager.getApplicationInfo(originalPkg, 0).sourceDir
            } catch (e: Exception) {
                return@withContext CloneInstallResult.Failure("Original app not found on device")
            }
            install(context, originalPkg, cloneIndex, sourceApk, workDir)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stable, reproducible clone package name for a given original + index. */
    fun buildClonePkg(originalPkg: String, cloneIndex: Int): String {
        val safe = originalPkg.replace('-', '_')
        return "com.accel.clone$cloneIndex.$safe"
    }

    fun isInstalled(context: Context, clonePkg: String): Boolean = try {
        context.packageManager.getPackageInfo(clonePkg, 0); true
    } catch (_: Exception) { false }

    fun launch(context: Context, clonePkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(clonePkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun uninstall(context: Context, clonePkg: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$clonePkg"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ── PackageInstaller ──────────────────────────────────────────────────────

    private fun pushToInstaller(context: Context, apk: File) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { session ->
            apk.inputStream().buffered().use { src ->
                session.openWrite("base.apk", 0, apk.length()).use { dst ->
                    src.copyTo(dst)
                    session.fsync(dst)
                }
            }
            val callbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context, sessionId, callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
        Log.d(TAG, "PackageInstaller session $sessionId committed — Android install dialog should appear")
    }
}

sealed class CloneInstallResult {
    data class Success(val clonePkg: String) : CloneInstallResult()
    data class Failure(val reason: String) : CloneInstallResult()
}
