package com.accel.cloner.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.accel.cloner.core.*
import com.accel.cloner.daemon.GGVirtualAdapter
import com.accel.cloner.daemon.VirtualDaemonService
import com.accel.cloner.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPickApp: () -> Unit) {
    val ctx = LocalContext.current
    val manager = remember { VirtualSpaceManager(ctx) }
    val scope = rememberCoroutineScope()

    var clonedApps by remember { mutableStateOf<List<ClonedApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val snackState = remember { SnackbarHostState() }
    val ggInstalled = remember { GGVirtualAdapter.isGGInstalled(ctx) }

    fun reload() {
        scope.launch {
            isLoading = true
            clonedApps = manager.getClonedApps()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackState.showSnackbar(it); snackMsg = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AccelCloner", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Virtual Space • No Root Required",
                            fontSize = 11.sp, color = OnSurfaceMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    if (ggInstalled) {
                        Icon(Icons.Default.SportsEsports, null,
                            tint = GreenActive, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickApp,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Clone App") },
                containerColor = VioletPrimary,
                contentColor = Color.White
            )
        },
        containerColor = SurfaceDark
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = VioletPrimary
                    )
                }
                clonedApps.isEmpty() -> EmptyState(onPickApp)
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { VirtualSpaceHeader(clonedApps.size) }
                        items(clonedApps, key = { "${it.packageName}_${it.cloneIndex}" }) { app ->
                            ClonedAppCard(
                                app = app,
                                ggInstalled = ggInstalled,
                                onRemove = {
                                    scope.launch {
                                        manager.removeClone(app.packageName, app.cloneIndex)
                                        reload()
                                        snackMsg = "Removed ${app.appName}"
                                    }
                                },
                                onGGAttach = {
                                    VirtualDaemonService.attachGG(ctx, app.packageName)
                                    snackMsg = "GG attached to ${app.appName} in virtual space"
                                }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualSpaceHeader(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VioletContainer)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VioletPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, null, tint = VioletLight, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Virtual Space Active", fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                Text("$count app${if (count != 1) "s" else ""} cloned • Self-rooted environment",
                    fontSize = 12.sp, color = OnSurfaceMuted)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenActive.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("ROOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenActive)
            }
        }
    }
}

@Composable
private fun ClonedAppCard(
    app: ClonedApp,
    ggInstalled: Boolean,
    onRemove: () -> Unit,
    onGGAttach: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Clone?") },
            text = { Text("This will delete all virtual data for ${app.appName}.") },
            confirmButton = {
                TextButton(onClick = { showRemoveDialog = false; onRemove() }) { Text("Remove", color = RedError) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = app.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                )
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(VioletPrimary)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${app.cloneIndex}", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight, fontSize = 15.sp)
                Text(app.packageName, fontSize = 10.sp, color = OnSurfaceMuted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip("Clone #${app.cloneIndex}", VioletPrimary)
                    StatusChip(VirtualFileSystem.run { app.sizeBytes.formatSize() }, SurfaceElevated)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ggInstalled) {
                    IconButton(onClick = onGGAttach, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SportsEsports, "Attach GG",
                            tint = GreenActive, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { showRemoveDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Remove", tint = RedError, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(onPickApp: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val inf = rememberInfiniteTransition(label = "pulse")
        val scale by inf.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "scale"
        )
        Box(
            Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(VioletPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CopyAll, null, tint = VioletPrimary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("No Cloned Apps Yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
        Spacer(Modifier.height(8.dp))
        Text("Clone any app into an isolated virtual space.\nNo root required on your device.",
            fontSize = 14.sp, color = OnSurfaceMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPickApp,
            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick an App to Clone")
        }
    }
}
