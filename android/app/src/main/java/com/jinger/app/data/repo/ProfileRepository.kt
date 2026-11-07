package com.jinger.app.data.repo

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.jinger.app.data.api.JingerApi
import com.jinger.app.data.api.ProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** Profile lives in SharedPreferences as JSON (tiny, single user); the CV PDF is copied into app storage. */
class ProfileRepository(private val context: Context, private val api: JingerApi) {
    private val prefs = context.getSharedPreferences("jinger_profile", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<ProfileDto?> = _profile

    val cvFile: File get() = File(context.filesDir, "cv.pdf")
    val hasCv: Boolean get() = cvFile.exists()

    private fun load(): ProfileDto? =
        prefs.getString("profile", null)?.let { gson.fromJson(it, ProfileDto::class.java) }

    suspend fun save(profile: ProfileDto) {
        prefs.edit().putString("profile", gson.toJson(profile)).apply()
        _profile.value = profile
        try {
            api.saveProfile(profile)
        } catch (_: Exception) {
            // Backend is best-effort here; local copy is the source of truth for the app.
        }
    }

    /** Copies the picked PDF into private storage and asks the backend to turn it into a profile. */
    suspend fun importCv(uri: Uri): ProfileDto = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            cvFile.outputStream().use { input.copyTo(it) }
        }
        val part = MultipartBody.Part.createFormData(
            "file", "cv.pdf", cvFile.asRequestBody("application/pdf".toMediaType())
        )
        api.parseCv(part)
    }
}
