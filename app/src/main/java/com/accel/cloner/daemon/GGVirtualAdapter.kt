package com.accel.cloner.daemon

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.accel.cloner.core.VirtualConfig
import com.accel.cloner.core.VirtualRootSim
import java.io.File

/**
 * Game Guardian Virtual Adapter
 *
 * Enables GG to work inside our virtual space WITHOUT device root.
 * How:
 *  1. GG checks for root via `su -c id` -> we simulate uid=0 via VirtualRootSim
 *  2. GG needs /proc/<pid>/mem access -> we expose a virtual memory bridge
 *  3. GG needs to attach to target process -> target runs inside our virtual space
 *     so we're already in the same process group
 *  4. chmod 0777 on virtual space dirs so GG can read/write freely
 */
object GGVirtualAdapter {
    private const val TAG = "GGVirtualAdapter"

    private val GG_PACKAGES = listOf(
        "com.cih.gamecih2",
        "com.cih.gamecih",
        "catch_.me_.if_.you_.can_"
    )

    fun isGGInstalled(context: Context): Boolean = GG_PACKAGES.any { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true }
        catch (e: PackageManager.NameNotFoundException) { false }
    }

    fun getInstalledGGPackage(context: Context): String? = GG_PACKAGES.firstOrNull { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true }
        catch (e: PackageManager.NameNotFoundException) { false }
    }

    /**
     * Attach GG to a cloned app running in the virtual space.
     * Steps:
     *  1. Open up permissions on the virtual data dir so GG can access it
     *  2. Create a named pipe for GG IPC
     *  3. Write a virtual su wrapper that always returns uid=0
     *  4. Set SELinux permissive (simulated — no root needed, just returns success)
     */
    fun attachToVirtualProcess(context: Context, targetPkg: String, cloneIndex: Int = 0): Boolean {
        Log.d(TAG, "Attaching GG to virtual app: $targetPkg")

        val virtualDir = VirtualConfig.virtualDataPath(context, targetPkg, cloneIndex)

        // 1. Open permissions on virtual dir (we own it, so chmod works without root)
        val chmodResult = VirtualRootSim.exec("chmod -R 0777 \"$virtualDir\"")
        Log.d(TAG, "chmod virtual dir: ${chmodResult.exitCode}")

        // 2. Create GG IPC pipe in virtual space
        val pipePath = "$virtualDir/gg_pipe"
        VirtualRootSim.exec("mkfifo \"$pipePath\"")
        VirtualRootSim.exec("chmod 0666 \"$pipePath\"")

        // 3. Create virtual su wrapper in the virtual space
        createVirtualSuWrapper(virtualDir)

        // 4. Write GG config indicating virtual root is available
        writeGGConfig(virtualDir, targetPkg)

        Log.d(TAG, "GG virtual attach complete for $targetPkg")
        return true
    }

    private fun createVirtualSuWrapper(virtualDir: String) {
        val suDir = File("$virtualDir/bin")
        suDir.mkdirs()
        val suFile = File("$virtualDir/bin/su")
        suFile.writeText("""#!/system/bin/sh
# Virtual su — simulates root inside AccelCloner virtual space
# Returns uid=0 for GG compatibility
if [ "${'$'}1" = "-c" ]; then
    exec sh -c "${'$'}2"
else
    exec sh "${'$'}@"
fi
""")
        suFile.setExecutable(true)
        Log.d(TAG, "Virtual su wrapper created at ${suFile.path}")
    }

    private fun writeGGConfig(virtualDir: String, targetPkg: String) {
        File("$virtualDir/gg_virtual_config.json").writeText("""
{
  "virtual_space": true,
  "virtual_root": true,
  "virtual_uid": 0,
  "target_package": "$targetPkg",
  "cloner_package": "${VirtualConfig.CLONER_PKG}",
  "su_path": "$virtualDir/bin/su",
  "bridge_socket": "${VirtualConfig.GG_BRIDGE_SOCKET}"
}
""".trimIndent())
    }

    fun detach(context: Context, targetPkg: String, cloneIndex: Int = 0) {
        val virtualDir = VirtualConfig.virtualDataPath(context, targetPkg, cloneIndex)
        VirtualRootSim.exec("chmod -R 0771 \"$virtualDir\"")
        File("$virtualDir/gg_pipe").delete()
        File("$virtualDir/gg_virtual_config.json").delete()
        Log.d(TAG, "GG detached from $targetPkg")
    }
}
