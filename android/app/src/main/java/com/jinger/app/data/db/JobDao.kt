package com.jinger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs WHERE status = 'NEW' ORDER BY fitScore DESC, fetchedAt ASC")
    fun feed(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY decidedAt DESC")
    fun byStatus(status: JobStatus): Flow<List<JobEntity>>

    @Query("SELECT id FROM jobs")
    suspend fun knownIds(): List<String>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(jobs: List<JobEntity>)

    @Query("UPDATE jobs SET fitScore = :score, fitReason = :reason WHERE id = :id")
    suspend fun updateFit(id: String, score: Int?, reason: String?)

    @Query("UPDATE jobs SET status = :status, decidedAt = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: JobStatus, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM jobs WHERE status = 'NEW'")
    suspend fun clearFeed()
}
