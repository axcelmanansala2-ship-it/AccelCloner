package com.accel.cloner.core

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val sourceApk: String = "",
    val sizeBytes: Long = 0L,
    val isCloned: Boolean = false,
    val cloneIndex: Int = 0
)

data class ClonedApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val cloneIndex: Int,
    val virtualDataPath: String,
    val sizeBytes: Long
)
