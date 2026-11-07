package com.jinger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jinger.app.data.api.JobDto

enum class JobStatus { NEW, SKIPPED, APPLIED }

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val source: String,
    val company: String,
    val title: String,
    val location: String,
    val remote: Boolean,
    val description: String,
    val applyUrl: String,
    val postedAt: String?,
    val fitScore: Int?,
    val fitReason: String?,
    val status: JobStatus = JobStatus.NEW,
    val fetchedAt: Long = System.currentTimeMillis(),
    val decidedAt: Long? = null,
)

fun JobDto.toEntity() = JobEntity(
    id = id, source = source, company = company, title = title, location = location, remote = remote,
    description = description, applyUrl = applyUrl, postedAt = postedAt, fitScore = fitScore, fitReason = fitReason,
)

fun JobEntity.toDto() = JobDto(
    id = id, source = source, company = company, title = title, location = location, remote = remote,
    description = description, applyUrl = applyUrl, postedAt = postedAt, fitScore = fitScore, fitReason = fitReason,
)
