package com.accel.cloner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.accel.cloner.core.AppInfo
import com.accel.cloner.core.CloneManager
import com.accel.cloner.core.CloneResult
import com.accel.cloner.daemon.DaemonService
import com.accel.cloner.root.RootUtils
import com.accel.cloner.ui.adapter.AppListAdapter
import com.accel.cloner.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cloneManager: CloneManager
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cloneManager = CloneManager(this)
        setupRecyclerView()
        startDaemon()
        checkPermissions()
        loadApps()
    }

    private fun startDaemon() {
        val intent = Intent(this, DaemonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"))
                )
            }
        }
        if (!RootUtils.isRooted()) {
            Toast.makeText(this, "⚠️ Root access required for full functionality", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onClone = { app -> cloneApp(app) },
            onRemove = { app -> removeClone(app) },
            onGGAttach = { app ->
                DaemonService.attachGG(this, app.packageName)
                Toast.makeText(this, "GG attached to ${app.appName}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadApps() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val apps = cloneManager.getInstalledApps()
            adapter.submitList(apps)
            binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener { loadApps() }
    }

    private fun cloneApp(app: AppInfo) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Cloning ${app.appName}…", Toast.LENGTH_SHORT).show()
            when (val result = cloneManager.cloneApp(app.packageName)) {
                is CloneResult.Success -> {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Cloned to:\n${result.clonePath}",
                        Toast.LENGTH_LONG
                    ).show()
                    loadApps()
                }
                is CloneResult.Failure -> {
                    Toast.makeText(this@MainActivity, "❌ ${result.reason}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun removeClone(app: AppInfo) {
        lifecycleScope.launch {
            val ok = cloneManager.removeClone(app.packageName, app.cloneIndex)
            Toast.makeText(
                this@MainActivity,
                if (ok) "🗑 Clone removed" else "❌ Failed to remove clone",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) loadApps()
        }
    }
}
