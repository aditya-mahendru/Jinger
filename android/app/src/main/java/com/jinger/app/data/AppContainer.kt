package com.jinger.app.data

import android.content.Context
import com.jinger.app.data.api.JingerApi
import com.jinger.app.data.db.JingerDatabase
import com.jinger.app.data.repo.JobRepository
import com.jinger.app.data.repo.ProfileRepository

/** Hand-rolled dependency container: small enough that a DI framework would be noise. */
class AppContainer(context: Context) {
    val api: JingerApi = JingerApi.create()
    val db: JingerDatabase = JingerDatabase.create(context)
    val jobs = JobRepository(api, db.jobDao())
    val profile = ProfileRepository(context, api)
}
