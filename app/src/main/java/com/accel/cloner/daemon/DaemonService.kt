package com.accel.cloner.daemon

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accel.cloner.R
import com.accel.cloner.core.CloneConstants
import com.accel.cloner.root.RootUtils
import kotlinx.coroutines.*

class DaemonService : Service() {

    private val TAG = "DaemonService"
    private val CHANNEL_ID = "accel_cloner_daemon"
    private val NOTIF_ID = 1001
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DaemonService created")
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("AccelCloner Daemon running…"))
        startDaemonLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")
        when (action) {
            ACTION_CHMOD -> handleChmod(intent)
            ACTION_MOUNT  -> handleMount(intent)
            ACTION_UMOUNT -> handleUmount(intent)
            ACTION_GG_ATTACH -> handleGGAttach(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "DaemonService destroyed — will restart via BootReceiver")
    }

    // ── Daemon loop ───────────────────────────────────────────────────────────

    private fun startDaemonLoop() {
        scope.launch {
            while (isActive) {
                ensureCloneBaseDirExists()
                checkGGCompatibility()
                delay(30_000L) // heartbeat every 30s
            }
        }
    }

    private fun ensureCloneBaseDirExists() {
        val base = CloneConstants.CLONE_DATA_BASE
        if (RootUtils.isRooted()) {
            RootUtils.mkdirWithPerms(base, "0771")
            Log.d(TAG, "Clone base dir ensured: $base")
        }
    }

    // ── chmod action ──────────────────────────────────────────────────────────

    private fun handleChmod(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "0771"
        val recursive = intent.getBooleanExtra(EXTRA_RECURSIVE, false)
        scope.launch {
            val ok = RootUtils.chmod(path, mode, recursive)
            Log.d(TAG, "chmod $mode $path recursive=$recursive -> $ok")
            updateNotification("chmod $mode applied to ${path.substringAfterLast('/')}")
        }
    }

    // ── bind mount action ─────────────────────────────────────────────────────

    private fun handleMount(intent: Intent) {
        val src = intent.getStringExtra(EXTRA_SRC) ?: return
        val dst = intent.getStringExtra(EXTRA_DST) ?: return
        scope.launch {
            val ok = RootUtils.bindMount(src, dst)
            Log.d(TAG, "bindMount $src -> $dst : $ok")
        }
    }

    private fun handleUmount(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        scope.launch { RootUtils.umount(path) }
    }

    // ── Game Guardian attach ───────────────────────────────────────────────────

    private fun handleGGAttach(intent: Intent) {
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG) ?: return
        scope.launch {
            GameGuardianBridge.attach(targetPkg)
        }
    }

    private fun checkGGCompatibility() {
        if (GameGuardianBridge.isGGInstalled(this)) {
            Log.d(TAG, "Game Guardian detected — compatibility layer active")
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AccelCloner Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps cloner daemon alive" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AccelCloner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_CHMOD     = "com.accel.cloner.ACTION_CHMOD"
        const val ACTION_MOUNT     = "com.accel.cloner.ACTION_MOUNT"
        const val ACTION_UMOUNT    = "com.accel.cloner.ACTION_UMOUNT"
        const val ACTION_GG_ATTACH = "com.accel.cloner.ACTION_GG_ATTACH"

        const val EXTRA_PATH       = "path"
        const val EXTRA_MODE       = "mode"
        const val EXTRA_RECURSIVE  = "recursive"
        const val EXTRA_SRC        = "src"
        const val EXTRA_DST        = "dst"
        const val EXTRA_TARGET_PKG = "target_pkg"

        fun chmod(context: Context, path: String, mode: String, recursive: Boolean = false) {
            context.startService(Intent(context, DaemonService::class.java).apply {
                action = ACTION_CHMOD
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_RECURSIVE, recursive)
            })
        }

        fun mount(context: Context, src: String, dst: String) {
            context.startService(Intent(context, DaemonService::class.java).apply {
                action = ACTION_MOUNT
                putExtra(EXTRA_SRC, src)
                putExtra(EXTRA_DST, dst)
            })
        }

        fun attachGG(context: Context, targetPkg: String) {
            context.startService(Intent(context, DaemonService::class.java).apply {
                action = ACTION_GG_ATTACH
                putExtra(EXTRA_TARGET_PKG, targetPkg)
            })
        }
    }
}
