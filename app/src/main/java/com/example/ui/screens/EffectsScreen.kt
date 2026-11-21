package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.GlShaderLivePreview
import com.example.model.EffectCategory
import com.example.model.EffectsCatalog
import com.example.model.InstalledPlugin
import com.example.model.ShaderEffectParams
import com.example.ui.components.IndependentPixelControlsPanel
import com.example.ui.components.StudioBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(
    params: ShaderEffectParams,
    initialCategory: EffectCategory,
    activePlugin: InstalledPlugin?,
    onSelectEffect: (String) -> Unit,
    onUpdateParams: ((ShaderEffectParams) -> ShaderEffectParams) -> Unit,
    onResetAll: () -> Unit,
    onOpenInEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    val categoryEffects = remember(selectedCategory) { EffectsCatalog.byCategory(selectedCategory) }
    val activeDef = remember(params.activeEffectId) { EffectsCatalog.findById(params.activeEffectId) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real-Time OpenGL ES GPU Shader Bench
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(Color.Black)
                ) {
                    GlShaderLivePreview(
                        params = params,
                        pluginGlslCode = activePlugin?.fragmentShaderCode,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioBadge(text = "LIVE GLSL PIXEL BENCH", accentColor = ElectricCyan)
                        StudioBadge(
                            text = activePlugin?.manifest?.name ?: activeDef.name,
                            accentColor = EmeraldReady
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeDef.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = activeDef.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onOpenInEditor,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("effects_open_editor_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Timeline", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6 Categories Selector
        item {
            Text("Effect Categories", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EffectCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.badgeCode} • ${cat.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("effect_cat_${cat.name.lowercase()}")
                    )
                }
            }
        }

        // Effects Grid / List for Selected Category
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categoryEffects.forEach { effect ->
                    val isSelected = params.activeEffectId == effect.id && params.activePluginId == null
                    Surface(
                        color = if (isSelected) ElectricCyan.copy(alpha = 0.14f) else StudioSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isSelected) ElectricCyan else StudioBorder),
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectEffect(effect.id) }
                            .testTag("effect_card_${effect.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioBadge(text = effect.category.badgeCode, accentColor = ElectricCyan)
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(effect.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = effect.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // Independent Master Parameter Controls
        item {
            IndependentPixelControlsPanel(
                params = params,
                onUpdate = onUpdateParams,
                onResetAll = onResetAll
            )
        }
    }
}
