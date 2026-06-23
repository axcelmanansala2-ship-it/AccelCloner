package com.accel.cloner.core

import android.util.Log
import java.io.*
import java.util.zip.*

/**
 * Reads a source APK, patches AndroidManifest.xml to change the package name,
 * and writes the result to a new APK file.
 */
object ApkRepackager {
    private const val TAG = "ApkRepackager"

    fun repackage(srcApk: File, dstApk: File, oldPkg: String, newPkg: String) {
        dstApk.parentFile?.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(srcApk))).use { zin ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(dstApk))).use { zout ->
                zout.setMethod(ZipOutputStream.DEFLATED)
                var entry: ZipEntry? = zin.nextEntry
                while (entry != null) {
                    val raw = zin.readBytes()
                    val data: ByteArray = if (entry.name == "AndroidManifest.xml") {
                        try {
                            AXMLPatcher.patchPackageName(raw, oldPkg, newPkg).also {
                                Log.d(TAG, "Manifest patched: $oldPkg → $newPkg (${it.size} bytes)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Manifest patch failed, using original: ${e.message}")
                            raw
                        }
                    } else {
                        raw
                    }
                    val ne = ZipEntry(entry.name)
                    zout.putNextEntry(ne)
                    zout.write(data)
                    zout.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }
        Log.d(TAG, "Repackaged APK written to ${dstApk.absolutePath} (${dstApk.length() / 1024} KB)")
    }
}
