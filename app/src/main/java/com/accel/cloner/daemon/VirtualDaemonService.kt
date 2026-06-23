package com.accel.cloner.daemon

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accel.cloner.core.VirtualConfig
import com.accel.cloner.core.VirtualRootSim
import kotlinx.coroutines.*
import java.io.File

class VirtualDaemonService : Service() {
    private val TAG = "VirtualDaemon"
    private val CHANNEL_ID = "accel_virtual_daemon"
    private val NOTIF_ID = 2001
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotif("AccelCloner Virtual Space running"))
        Log.d(TAG, "VirtualDaemonService started")
        startHeartbeat()
        ensureVirtualBaseDir()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_VIRTUAL_CHMOD  -> handleChmod(intent)
            ACTION_GG_ATTACH      -> handleGGAttach(intent)
            ACTION_VIRTUAL_EXEC   -> handleExec(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                ensureVirtualBaseDir()
                delay(60_000)
            }
        }
    }

    private fun ensureVirtualBaseDir() {
        val base = getExternalFilesDir(null) ?: filesDir
        File(base, VirtualConfig.VIRTUAL_SPACE_DIR).mkdirs()
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleChmod(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "0771"
        val recursive = intent.getBooleanExtra(EXTRA_RECURSIVE, false)
        scope.launch {
            val flag = if (recursive) "-R " else ""
            val result = VirtualRootSim.exec("chmod $flag$mode \"$path\"")
            Log.d(TAG, "chmod $flag$mode $path -> ${result.exitCode}")
        }
    }

    private fun handleGGAttach(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_TARGET_PKG) ?: return
        scope.launch {
            GGVirtualAdapter.attachToVirtualProcess(this@VirtualDaemonService, pkg)
            updateNotif("GG attached to $pkg")
        }
    }

    private fun handleExec(intent: Intent) {
        val cmd = intent.getStringExtra(EXTRA_CMD) ?: return
        scope.launch {
            val result = VirtualRootSim.exec(cmd)
            Log.d(TAG, "exec[$cmd] -> ${result.exitCode}: ${result.stdout}")
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "AccelCloner Virtual Daemon", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AccelCloner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(text))
    }

    companion object {
        const val ACTION_VIRTUAL_CHMOD = "com.accel.cloner.VIRTUAL_CHMOD"
        const val ACTION_GG_ATTACH     = "com.accel.cloner.GG_ATTACH"
        const val ACTION_VIRTUAL_EXEC  = "com.accel.cloner.VIRTUAL_EXEC"
        const val EXTRA_PATH           = "path"
        const val EXTRA_MODE           = "mode"
        const val EXTRA_RECURSIVE      = "recursive"
        const val EXTRA_TARGET_PKG     = "target_pkg"
        const val EXTRA_CMD            = "cmd"

        fun start(context: Context) {
            val intent = Intent(context, VirtualDaemonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun chmod(context: Context, path: String, mode: String, recursive: Boolean = false) {
            context.startService(Intent(context, VirtualDaemonService::class.java).apply {
                action = ACTION_VIRTUAL_CHMOD
                putExtra(EXTRA_PATH, path); putExtra(EXTRA_MODE, mode); putExtra(EXTRA_RECURSIVE, recursive)
            })
        }

        fun attachGG(context: Context, pkg: String) {
            context.startService(Intent(context, VirtualDaemonService::class.java).apply {
                action = ACTION_GG_ATTACH; putExtra(EXTRA_TARGET_PKG, pkg)
            })
        }
    }
}
