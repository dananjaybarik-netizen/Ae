package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.StudioProjectSummary
import com.example.ui.components.StudioBadge
import com.example.ui.components.formatTimecode
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<StudioProjectSummary>,
    onNewProject: (String) -> Unit,
    onOpenProject: (Long) -> Unit,
    onRenameProject: (Long, String) -> Unit,
    onDuplicateProject: (Long) -> Unit,
    onDeleteProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<StudioProjectSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectTitle by remember { mutableStateOf("Cinema Cut") }

    val filtered = remember(projects, query) {
        if (query.isBlank()) projects else projects.filter { it.title.contains(query, ignoreCase = true) }
    }
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        Surface(color = StudioSurface, border = BorderStroke(1.dp, StudioBorder)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Projects Library", style = MaterialTheme.typography.headlineSmall)
                        Text("${projects.size} sequences stored locally via Room", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("projects_new_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search sequences...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text("No Matching Projects", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Create a new sequence or save an active edit from the Timeline Editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { proj ->
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
                                Text(
                                    text = proj.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                StudioBadge(text = proj.resolutionLabel, accentColor = ElectricCyan)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${proj.clipCount} clips • ${formatTimecode(proj.durationMs)} • Updated ${dateFmt.format(Date(proj.updatedAt))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onOpenProject(proj.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Editor", fontWeight = FontWeight.Bold)
                                }
                                Row {
                                    IconButton(onClick = {
                                        renameTarget = proj
                                        renameText = proj.title
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename project")
                                    }
                                    IconButton(onClick = { onDuplicateProject(proj.id) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate project")
                                    }
                                    IconButton(onClick = { onDeleteProject(proj.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = CrimsonAlert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Sequence") },
            text = {
                OutlinedTextField(
                    value = newProjectTitle,
                    onValueChange = { newProjectTitle = it },
                    label = { Text("Project Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCreateDialog = false
                    onNewProject(newProjectTitle.ifBlank { "Studio Sequence" })
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Sequence") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameProject(target.id, renameText)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}
