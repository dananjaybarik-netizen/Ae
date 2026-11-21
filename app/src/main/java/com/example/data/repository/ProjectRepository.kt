package com.example.data.repository

import com.example.data.db.ProjectDao
import com.example.data.db.ProjectEntity
import com.example.model.TimelineProjectData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class StudioProjectSummary(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val clipCount: Int,
    val resolutionLabel: String,
    val primaryMediaUri: String?,
    val updatedAt: Long,
    val createdAt: Long
)

class ProjectRepository(private val projectDao: ProjectDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allProjects: Flow<List<StudioProjectSummary>> =
        projectDao.observeAllProjects().map { list ->
            list.map { entity ->
                StudioProjectSummary(
                    id = entity.id,
                    title = entity.title,
                    durationMs = entity.durationMs,
                    clipCount = entity.clipCount,
                    resolutionLabel = entity.resolutionLabel,
                    primaryMediaUri = entity.primaryMediaUri,
                    updatedAt = entity.updatedAt,
                    createdAt = entity.createdAt
                )
            }
        }

    suspend fun loadProject(id: Long): Pair<ProjectEntity, TimelineProjectData>? {
        val entity = projectDao.getProjectById(id) ?: return null
        val timeline = runCatching {
            json.decodeFromString<TimelineProjectData>(entity.timelineJson)
        }.getOrElse {
            TimelineProjectData(projectName = entity.title)
        }
        return entity to timeline
    }

    suspend fun saveProject(
        existingId: Long?,
        data: TimelineProjectData
    ): Long {
        val serialized = json.encodeToString(data)
        val now = System.currentTimeMillis()
        val firstMedia = data.clips.firstOrNull()?.sourceUri
        return if (existingId != null && existingId > 0L) {
            val prev = projectDao.getProjectById(existingId)
            val entity = ProjectEntity(
                id = existingId,
                title = data.projectName,
                timelineJson = serialized,
                durationMs = data.totalDurationMs,
                clipCount = data.clips.size,
                resolutionLabel = data.aspectRatioLabel,
                primaryMediaUri = firstMedia,
                createdAt = prev?.createdAt ?: now,
                updatedAt = now
            )
            projectDao.updateProject(entity)
            existingId
        } else {
            val entity = ProjectEntity(
                title = data.projectName,
                timelineJson = serialized,
                durationMs = data.totalDurationMs,
                clipCount = data.clips.size,
                resolutionLabel = data.aspectRatioLabel,
                primaryMediaUri = firstMedia,
                createdAt = now,
                updatedAt = now
            )
            projectDao.insertProject(entity)
        }
    }

    suspend fun renameProject(id: Long, newTitle: String) {
        val loaded = loadProject(id) ?: return
        val updatedData = loaded.second.copy(projectName = newTitle.trim().ifEmpty { "Untitled Sequence" })
        saveProject(id, updatedData)
    }

    suspend fun duplicateProject(id: Long): Long? {
        val loaded = loadProject(id) ?: return null
        val copyData = loaded.second.copy(projectName = "${loaded.second.projectName} Copy")
        return saveProject(null, copyData)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }
}
