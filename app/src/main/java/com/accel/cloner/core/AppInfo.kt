package com.accel.cloner.core

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isCloned: Boolean = false,
    val cloneIndex: Int = 0
)
