package com.accel.cloner.core

object CloneConstants {
    const val CLONER_PKG = "com.accel.cloner"

    // Base path where all cloned app data lives:
    // /sdcard/Android/data/com.accel.cloner/clones/<pkg>/
    const val CLONE_DATA_BASE = "/sdcard/Android/data/$CLONER_PKG/clones"

    // Real Android data dir (requires root)
    const val ANDROID_DATA = "/data/data"

    // Permissions applied to clone data dirs
    const val DIR_PERMISSION = "0771"
    const val FILE_PERMISSION = "0660"

    // Daemon IPC socket name
    const val DAEMON_SOCKET = "accel_cloner_daemon"

    // Game Guardian integration
    const val GG_PKG = "com.github.megatronking.stringfog"  // common GG pkg
    const val GG_PROCESS_NAME = "guardservice"

    // Max clones per app
    const val MAX_CLONES_PER_APP = 5
}
