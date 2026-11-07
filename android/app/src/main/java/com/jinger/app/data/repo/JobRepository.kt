package com.jinger.app.data.repo

import android.util.Log
import com.jinger.app.data.api.JingerApi
import com.jinger.app.data.api.SeenRequest
import com.jinger.app.data.db.JobDao
import com.jinger.app.data.db.JobEntity
import com.jinger.app.data.db.JobStatus
import com.jinger.app.data.db.toEntity
import kotlinx.coroutines.flow.Flow

class JobRepository(private val api: JingerApi, private val dao: JobDao) {
    val feed: Flow<List<JobEntity>> = dao.feed()
    val applied: Flow<List<JobEntity>> = dao.byStatus(JobStatus.APPLIED)
    val skipped: Flow<List<JobEntity>> = dao.byStatus(JobStatus.SKIPPED)

    suspend fun get(id: String): JobEntity? = dao.get(id)

    /** Pulls the next batch of unseen jobs from the backend into Room. Returns how many were new. */
    suspend fun loadMore(limit: Int = 20): Int {
        val known = dao.knownIds()
        val fresh = api.jobs(limit = limit, exclude = known.joinToString(","))
        dao.insertNew(fresh.map { it.toEntity() })
        fresh.forEach { dao.updateFit(it.id, it.fitScore, it.fitReason) }
        return fresh.size
    }

    /** Drops undecided cards so they get re-ranked (e.g. after the profile changes). */
    suspend fun refreshFeed(): Int {
        dao.clearFeed()
        return loadMore()
    }

    suspend fun skip(id: String) = decide(id, JobStatus.SKIPPED)

    suspend fun markApplied(id: String) = decide(id, JobStatus.APPLIED)

    private suspend fun decide(id: String, status: JobStatus) {
        dao.setStatus(id, status)
        try {
            api.markSeen(SeenRequest(listOf(id), status.name.lowercase()))
        } catch (e: Exception) {
            Log.w("JobRepository", "backend unreachable, decision kept locally: ${e.message}")
        }
    }
}
