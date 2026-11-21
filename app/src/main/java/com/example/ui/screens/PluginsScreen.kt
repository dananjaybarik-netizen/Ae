package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.engine.GlShaderLivePreview
import com.example.model.AifxManifest
import com.example.model.InstalledPlugin
import com.example.model.PluginCategory
import com.example.model.ShaderEffectParams
import com.example.model.StorePluginListing
import com.example.ui.components.StudioBadge
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioViolet
import java.io.File

private data class PluginPreviewTarget(
    val manifest: AifxManifest,
    val shaderCode: String,
    val sha256: String?,
    val isVerified: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    storeCatalog: List<StorePluginListing>,
    installedPlugins: List<InstalledPlugin>,
    bannerMessage: String?,
    onClearBanner: () -> Unit,
    onInstallStorePlugin: (StorePluginListing) -> Unit,
    onImportAifxUri: (Uri) -> Unit,
    onPackageSampleAifx: ((File) -> Unit) -> Unit,
    onTogglePluginEnabled: (String, Boolean) -> Unit,
    onRemovePlugin: (String) -> Unit,
    onActivateInEditor: (InstalledPlugin) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<PluginCategory?>(null) }
    var previewTarget by remember { mutableStateOf<PluginPreviewTarget?>(null) }

    val aifxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportAifxUri(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Header + Security Notice
        Surface(color = StudioSurface, border = BorderStroke(1.dp, StudioBorder)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(".aifx Plugin Studio", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Sandboxed GLSL shader packages • SHA-256 verified • Zero native code execution",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StudioBadge(text = "SANDBOXED", accentColor = EmeraldReady)
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { aifxPicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_aifx_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .aifx", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            onPackageSampleAifx { file ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share .aifx Package"))
                            }
                        },
                        modifier = Modifier.testTag("export_sample_aifx_button")
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Build .aifx")
                    }
                }

                if (bannerMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = StudioViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioViolet)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(bannerMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = onClearBanner, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = StudioSurface,
            contentColor = ElectricCyan
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Plugin Store (${storeCatalog.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Installed (${installedPlugins.size})") }
            )
        }

        // Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Categories") }
                )
            }
            items(PluginCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.displayName) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (selectedTab == 0) {
                val filtered = storeCatalog.filter { selectedCategory == null || it.manifest.category == selectedCategory }
                items(filtered, key = { it.manifest.pluginId }) { listing ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        border = BorderStroke(1.dp, StudioBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StudioBadge(text = listing.badge, accentColor = StudioViolet)
                                    StudioBadge(text = "v${listing.manifest.version}", accentColor = ElectricCyan)
                                }
                                Text(
                                    text = "API ${listing.manifest.compatibility.minApiLevel}+ • GLES ${listing.manifest.compatibility.minGlEsVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(listing.manifest.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "by ${listing.manifest.author} • ${listing.manifest.category.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = listing.manifest.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        previewTarget = PluginPreviewTarget(
                                            manifest = listing.manifest,
                                            shaderCode = listing.fragmentShaderCode,
                                            sha256 = null,
                                            isVerified = true
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Preview")
                                }
                                Button(
                                    onClick = { onInstallStorePlugin(listing) },
                                    enabled = !listing.isInstalled || listing.hasUpdate,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (listing.hasUpdate) KeyframeAmber else ElectricCyan,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("install_plugin_${listing.manifest.pluginId}")
                                ) {
                                    Icon(
                                        imageVector = if (listing.hasUpdate) Icons.Default.SystemUpdate else Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            listing.hasUpdate -> "Update"
                                            listing.isInstalled -> "Installed"
                                            else -> "Install"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val filtered = installedPlugins.filter { selectedCategory == null || it.manifest.category == selectedCategory }
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Extension, contentDescription = null, tint = StudioViolet, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No Plugins Installed", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Install verified GLSL plugins from the Store tab or import a custom .aifx package.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.pluginId }) { plugin ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioSurface),
                            border = BorderStroke(1.dp, StudioBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (plugin.isSignatureVerified) Icons.Default.Verified else Icons.Default.Security,
                                            contentDescription = "Signature",
                                            tint = if (plugin.isSignatureVerified) EmeraldReady else KeyframeAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(plugin.manifest.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Switch(
                                        checked = plugin.isEnabled,
                                        onCheckedChange = { onTogglePluginEnabled(plugin.pluginId, it) }
                                    )
                                }
                                Text(
                                    text = "v${plugin.manifest.version} • SHA-256: ${plugin.signatureSha256.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(plugin.manifest.description, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            previewTarget = PluginPreviewTarget(
                                                manifest = plugin.manifest,
                                                shaderCode = plugin.fragmentShaderCode,
                                                sha256 = plugin.signatureSha256,
                                                isVerified = plugin.isSignatureVerified
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Preview")
                                    }
                                    Button(
                                        onClick = { onActivateInEditor(plugin) },
                                        enabled = plugin.isEnabled,
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioViolet, contentColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Apply")
                                    }
                                    IconButton(onClick = { onRemovePlugin(plugin.pluginId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove plugin", tint = CrimsonAlert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { previewTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(target.manifest.name, style = MaterialTheme.typography.headlineSmall)
                    StudioBadge(
                        text = if (target.isVerified) "SHA-256 VERIFIED" else "SANDBOXED",
                        accentColor = if (target.isVerified) EmeraldReady else KeyframeAmber
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                ) {
                    GlShaderLivePreview(
                        params = ShaderEffectParams(
                            activePluginId = target.manifest.pluginId,
                            effectIntensity = target.manifest.parameters.firstOrNull()?.defaultValue ?: 0.85f
                        ),
                        pluginGlslCode = target.shaderCode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text("Presets (${target.manifest.presets.size}) • Parameters (${target.manifest.parameters.size})", style = MaterialTheme.typography.titleSmall)
                target.manifest.presets.forEach { preset ->
                    Surface(color = StudioSurfaceElevated, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${preset.name}: ${preset.values}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text("GLSL Sandbox Shader Entry", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                Surface(color = ObsidianVoid, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = target.shaderCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
