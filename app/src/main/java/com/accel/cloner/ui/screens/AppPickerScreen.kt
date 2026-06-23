package com.accel.cloner.ui.screens

import androidx.compose.animation.*
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
import coil.compose.AsyncImage
import com.accel.cloner.core.*
import com.accel.cloner.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val manager = remember { VirtualSpaceManager(ctx) }
    val scope = rememberCoroutineScope()
    val snackState = remember { SnackbarHostState() }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var cloningPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = manager.getInstallableApps()
        isLoading = false
    }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps
        else apps.filter {
            it.appName.contains(search, true) || it.packageName.contains(search, true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = { Text("Pick App to Clone", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Search bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps…", color = OnSurfaceMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceMuted) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Clear, null, tint = OnSurfaceMuted)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioletPrimary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedTextColor = OnSurfaceLight,
                    unfocusedTextColor = OnSurfaceLight
                ),
                singleLine = true
            )

            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No apps found", color = OnSurfaceMuted)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("${filtered.size} apps", fontSize = 12.sp, color = OnSurfaceMuted,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        PickerAppCard(
                            app = app,
                            isCloned = manager.isCloned(app.packageName),
                            isCloning = cloningPkg == app.packageName,
                            onClick = {
                                scope.launch {
                                    cloningPkg = app.packageName
                                    val index = manager.getNextCloneIndex(app.packageName)
                                    if (index == -1) {
                                        snackState.showSnackbar("Max clones reached for ${app.appName}")
                                    } else {
                                        when (val r = manager.cloneApp(app.packageName, index)) {
                                            is VirtualSpaceResult.Success ->
                                                snackState.showSnackbar("✓ ${app.appName} cloned to virtual space")
                                            is VirtualSpaceResult.Failure ->
                                                snackState.showSnackbar("✗ ${r.reason}")
                                        }
                                    }
                                    cloningPkg = null
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickerAppCard(
    app: AppInfo,
    isCloned: Boolean,
    isCloning: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = if (isCloned) BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.4f)) else null
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = app.icon,
                contentDescription = app.appName,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Medium, color = OnSurfaceLight, fontSize = 14.sp)
                Text(app.packageName, fontSize = 10.sp, color = OnSurfaceMuted, maxLines = 1)
                Text("v${app.versionName}", fontSize = 10.sp, color = OnSurfaceMuted)
            }
            Spacer(Modifier.width(8.dp))
            if (isCloning) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = VioletPrimary, strokeWidth = 3.dp)
            } else {
                Button(
                    onClick = onClick,
                    enabled = !isCloned,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCloned) SurfaceElevated else VioletPrimary,
                        contentColor = if (isCloned) OnSurfaceMuted else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isCloned) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cloned", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.CopyAll, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clone", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
