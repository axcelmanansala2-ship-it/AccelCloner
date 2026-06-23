package com.accel.cloner.core

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

    /** Base dir: /sdcard/Android/data/com.accel.cloner/virtual_space/<pkg>/ */
    fun virtualDataPath(pkg: String, cloneIndex: Int = 0): String =
        "/sdcard/Android/data/$CLONER_PKG/$VIRTUAL_SPACE_DIR/${pkg}_$cloneIndex"

    fun virtualFilesPath(pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(pkg, cloneIndex)}/files"

    fun virtualDbPath(pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(pkg, cloneIndex)}/databases"

    fun virtualPrefsPath(pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(pkg, cloneIndex)}/shared_prefs"

    fun pluginApkPath(pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(pkg, cloneIndex)}/base.apk"

    fun odexPath(pkg: String, cloneIndex: Int = 0) =
        "${virtualDataPath(pkg, cloneIndex)}/$ODEX_DIR"
}
