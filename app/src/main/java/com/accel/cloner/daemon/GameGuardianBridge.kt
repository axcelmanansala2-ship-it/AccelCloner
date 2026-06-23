package com.accel.cloner.daemon

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.accel.cloner.core.CloneConstants
import com.accel.cloner.root.RootUtils

object GameGuardianBridge {

    private const val TAG = "GGBridge"

    // Known Game Guardian package names
    private val GG_PACKAGES = listOf(
        "com.cih.gamecih2",
        "com.cih.gamecih",
        "catch_.me_.if_.you_.can_"
    )

    fun isGGInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return GG_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Set up daemon socket and memory access permissions so that
     * Game Guardian can attach to a cloned app's process.
     *
     * GG reads /proc/<pid>/mem — we ensure the clone process dir
     * has the right permissions and that the GG daemon socket is open.
     */
    fun attach(targetPkg: String): Boolean {
        Log.d(TAG, "Attaching GG to $targetPkg")

        if (!RootUtils.isRooted()) {
            Log.w(TAG, "Root required for GG attach")
            return false
        }

        // Ensure /proc access is open for GG (chmod on clone data dir)
        val cloneDataPath = "${CloneConstants.CLONE_DATA_BASE}/$targetPkg"
        val chmodOk = RootUtils.chmod(cloneDataPath, "0777", recursive = true)
        Log.d(TAG, "chmod 0777 on $cloneDataPath: $chmodOk")

        // Set SELinux to permissive so GG can read process memory
        val (seCode, _) = RootUtils.exec("setenforce 0")
        Log.d(TAG, "setenforce 0 -> $seCode")

        // Create a named pipe for GG IPC
        val pipePath = "${CloneConstants.CLONE_DATA_BASE}/gg_bridge_$targetPkg"
        RootUtils.exec("mkfifo \"$pipePath\"")
        RootUtils.chmod(pipePath, "0666")

        Log.d(TAG, "GG attach complete for $targetPkg")
        return chmodOk
    }

    /**
     * Detach GG from a target package (restore permissions).
     */
    fun detach(targetPkg: String) {
        Log.d(TAG, "Detaching GG from $targetPkg")
        val cloneDataPath = "${CloneConstants.CLONE_DATA_BASE}/$targetPkg"
        RootUtils.chmod(cloneDataPath, "0771", recursive = true)
        RootUtils.exec("setenforce 1")
    }
}
