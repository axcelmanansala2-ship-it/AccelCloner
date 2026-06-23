package com.accel.cloner.core

import android.content.Context

object VirtualConfig {
    const val CLONER_PKG         = "com.accel.cloner"
    const val VIRTUAL_SPACE_DIR  = "virtual_space"
    const val PLUGINS_DIR        = "plugins"
    const val DATA_DIR           = "data"
    const val LIBS_DIR           = "lib"
    const val ODEX_DIR           = "odex"
    const val GG_BRIDGE_SOCKET   = "accel_gg_bridge"
    const val VIRTUAL_SU_SOCKET  = "accel_virtual_su"
    const val MAX_CLONES         = 5

    /**
     * Returns the writable base dir owned by this app — no special permissions needed.
     * Uses external files dir (shows in Files app) with internal dir as fallback.
     */
    fun storageBase(context: Context): String =
        (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath

    /** Base dir: <externalFilesDir>/virtual_space/<pkg>_<cloneIndex>/ */
    fun virtualDataPath(context: Context, pkg: String, cloneIndex: Int = 0): String =
        "${storageBase(context)}/$VIRTUAL_SPACE_DIR/${pkg}_$cloneIndex"

    fun virtualFilesPath(context: Context, pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(context, pkg, cloneIndex)}/files"

    fun virtualDbPath(context: Context, pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(context, pkg, cloneIndex)}/databases"

    fun virtualPrefsPath(context: Context, pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(context, pkg, cloneIndex)}/shared_prefs"

    fun pluginApkPath(context: Context, pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(context, pkg, cloneIndex)}/base.apk"

    fun odexPath(context: Context, pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(context, pkg, cloneIndex)}/$ODEX_DIR"
}
