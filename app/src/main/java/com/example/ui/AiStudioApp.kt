package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.EffectCategory
import com.example.model.StudioRoute
import com.example.model.TrackType
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.EffectsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PluginsScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioSurface
import com.example.viewmodel.StudioViewModel

private data class NavItemSpec(
    val label: String,
    val route: StudioRoute,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AiStudioApp(
    viewModel: StudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val installedPlugins by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val storeCatalog by viewModel.storeCatalog.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hardwareReport by viewModel.hardwareReport.collectAsStateWithLifecycle()
    val cacheSizeLabel by viewModel.cacheSizeLabel.collectAsStateWithLifecycle()
    val pluginBanner by viewModel.pluginBanner.collectAsStateWithLifecycle()

    var initialEffectsCategory by remember { mutableStateOf(EffectCategory.SHARPEN) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(editorState.statusMessage) {
        val msg = editorState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val navItems = remember {
        listOf(
            NavItemSpec("Home", StudioRoute.Home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            NavItemSpec("Editor", StudioRoute.Editor(), Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation, "nav_editor"),
            NavItemSpec("Effects", StudioRoute.Effects, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_effects"),
            NavItemSpec("Plugins", StudioRoute.Plugins, Icons.Filled.Extension, Icons.Outlined.Extension, "nav_plugins"),
            NavItemSpec("Projects", StudioRoute.Projects, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_projects"),
            NavItemSpec("Settings", StudioRoute.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateTopLevel(route: StudioRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = when (item.route) {
                                is StudioRoute.Home -> currentDest?.hasRoute<StudioRoute.Home>() == true
                                is StudioRoute.Editor -> currentDest?.hasRoute<StudioRoute.Editor>() == true
                                is StudioRoute.Effects -> currentDest?.hasRoute<StudioRoute.Effects>() == true
                                is StudioRoute.Plugins -> currentDest?.hasRoute<StudioRoute.Plugins>() == true
                                is StudioRoute.Projects -> currentDest?.hasRoute<StudioRoute.Projects>() == true
                                is StudioRoute.Settings -> currentDest?.hasRoute<StudioRoute.Settings>() == true
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = StudioSurface) {
                        navItems.forEach { item ->
                            val selected = when (item.route) {
                                is StudioRoute.Home -> currentDest?.hasRoute<StudioRoute.Home>() == true
                                is StudioRoute.Editor -> currentDest?.hasRoute<StudioRoute.Editor>() == true
                                is StudioRoute.Effects -> currentDest?.hasRoute<StudioRoute.Effects>() == true
                                is StudioRoute.Plugins -> currentDest?.hasRoute<StudioRoute.Plugins>() == true
                                is StudioRoute.Projects -> currentDest?.hasRoute<StudioRoute.Projects>() == true
                                is StudioRoute.Settings -> currentDest?.hasRoute<StudioRoute.Settings>() == true
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = StudioRoute.Home,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<StudioRoute.Home> {
                        HomeScreen(
                            projects = projects,
                            hardwareReport = hardwareReport,
                            isGeneratingSample = editorState.isGeneratingSample,
                            installedPluginCount = installedPlugins.size,
                            onCreateNewSequence = {
                                viewModel.createNewProject()
                                navigateTopLevel(StudioRoute.Editor())
                            },
                            onImportVideo = { uri ->
                                viewModel.importVideoUri(uri, TrackType.VIDEO_PRIMARY)
                                navigateTopLevel(StudioRoute.Editor())
                            },
                            onGenerateSampleAndOpen = {
                                viewModel.generateCalibrationClipAndLoad()
                                navigateTopLevel(StudioRoute.Editor())
                            },
                            onOpenProject = { id ->
                                viewModel.openProject(id)
                                navigateTopLevel(StudioRoute.Editor(id))
                            },
                            onOpenEffectsCategory = { cat ->
                                initialEffectsCategory = cat
                                navigateTopLevel(StudioRoute.Effects)
                            },
                            onNavigatePlugins = { navigateTopLevel(StudioRoute.Plugins) },
                            onNavigateProjects = { navigateTopLevel(StudioRoute.Projects) }
                        )
                    }

                    composable<StudioRoute.Editor> {
                        EditorScreen(
                            uiState = editorState,
                            liveShaderState = viewModel.liveShaderState,
                            installedPlugins = installedPlugins,
                            hardwareReport = hardwareReport,
                            onImportVideo = viewModel::importVideoUri,
                            onGenerateSample = viewModel::generateCalibrationClipAndLoad,
                            onSelectClip = viewModel::selectClip,
                            onUpdatePlayhead = viewModel::updatePlayhead,
                            onSetPlaying = viewModel::setPlaying,
                            onSetZoom = viewModel::setTimelineZoom,
                            onTrimClip = viewModel::trimSelectedClip,
                            onSplitAtPlayhead = viewModel::splitSelectedClipAtPlayhead,
                            onDeleteClip = viewModel::deleteSelectedClip,
                            onSetSpeed = viewModel::setSelectedClipSpeed,
                            onRotateClip = viewModel::rotateSelectedClip,
                            onSetCrop = viewModel::setSelectedClipCrop,
                            onToggleMute = viewModel::toggleSelectedClipMute,
                            onSetTransition = viewModel::setSelectedClipTransition,
                            onAddKeyframe = viewModel::addKeyframeAtPlayhead,
                            onRemoveKeyframe = viewModel::removeKeyframe,
                            onUpdateShaderParams = viewModel::updateMasterEffectParams,
                            onResetShaders = viewModel::resetAllEffects,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onSaveProject = viewModel::saveCurrentProject,
                            onUpdateExportConfig = viewModel::updateExportConfig,
                            onStartExport = viewModel::startExport,
                            onCancelExport = viewModel::cancelExport,
                            onResetExport = viewModel::resetExportState
                        )
                    }

                    composable<StudioRoute.Effects> {
                        val activePlugin = editorState.project.masterEffects.activePluginId?.let { pid ->
                            installedPlugins.firstOrNull { it.pluginId == pid && it.isEnabled }
                        }
                        EffectsScreen(
                            params = editorState.project.masterEffects,
                            initialCategory = initialEffectsCategory,
                            activePlugin = activePlugin,
                            onSelectEffect = viewModel::selectBuiltinEffect,
                            onUpdateParams = { update -> viewModel.updateMasterEffectParams(false, update) },
                            onResetAll = viewModel::resetAllEffects,
                            onOpenInEditor = { navigateTopLevel(StudioRoute.Editor()) }
                        )
                    }

                    composable<StudioRoute.Plugins> {
                        PluginsScreen(
                            storeCatalog = storeCatalog,
                            installedPlugins = installedPlugins,
                            bannerMessage = pluginBanner,
                            onClearBanner = viewModel::clearPluginBanner,
                            onInstallStorePlugin = viewModel::installStorePlugin,
                            onImportAifxUri = viewModel::importAifxFile,
                            onPackageSampleAifx = viewModel::createSampleAifxFile,
                            onTogglePluginEnabled = viewModel::togglePluginEnabled,
                            onRemovePlugin = viewModel::removePlugin,
                            onActivateInEditor = { plugin ->
                                viewModel.activatePluginShader(plugin)
                                navigateTopLevel(StudioRoute.Editor())
                            }
                        )
                    }

                    composable<StudioRoute.Projects> {
                        ProjectsScreen(
                            projects = projects,
                            onNewProject = { title ->
                                viewModel.createNewProject(title)
                                navigateTopLevel(StudioRoute.Editor())
                            },
                            onOpenProject = { id ->
                                viewModel.openProject(id)
                                navigateTopLevel(StudioRoute.Editor(id))
                            },
                            onRenameProject = viewModel::renameProject,
                            onDuplicateProject = viewModel::duplicateProject,
                            onDeleteProject = viewModel::deleteProject
                        )
                    }

                    composable<StudioRoute.Settings> {
                        SettingsScreen(
                            settings = settings,
                            hardwareReport = hardwareReport,
                            cacheSizeLabel = cacheSizeLabel,
                            onToggleLowRamMode = viewModel::setLowRamSafeMode,
                            onSelectPreviewResolution = viewModel::setPreviewResolution,
                            onToggleEffectCache = viewModel::setEffectCacheEnabled,
                            onToggleThermalThrottling = viewModel::setThermalThrottling,
                            onTogglePreferVulkan = viewModel::setPreferVulkan,
                            onSelectDefaultExportRes = viewModel::setDefaultExportResolution,
                            onSelectDefaultExportFps = viewModel::setDefaultExportFps,
                            onClearCache = viewModel::clearShaderAndMediaCache,
                            onRefreshTelemetry = viewModel::refreshHardwareAndCache
                        )
                    }
                }
            }
        }
    }
}
