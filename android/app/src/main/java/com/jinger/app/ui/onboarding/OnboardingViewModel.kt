package com.jinger.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinger.app.data.api.ProfileDto
import com.jinger.app.data.repo.JobRepository
import com.jinger.app.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val profiles: ProfileRepository, private val jobs: JobRepository) : ViewModel() {
    private val _draft = MutableStateFlow(profiles.profile.value ?: ProfileDto())
    val draft: StateFlow<ProfileDto> = _draft
    private val _parsing = MutableStateFlow(false)
    val parsing: StateFlow<Boolean> = _parsing
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    val hasCv: Boolean get() = profiles.hasCv

    fun update(transform: ProfileDto.() -> ProfileDto) {
        _draft.value = _draft.value.transform()
    }

    fun importCv(uri: Uri) {
        viewModelScope.launch {
            _parsing.value = true
            _error.value = null
            try {
                _draft.value = profiles.importCv(uri)
            } catch (e: Exception) {
                _error.value = "Couldn't parse CV: ${e.message}"
            } finally {
                _parsing.value = false
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            profiles.save(_draft.value)
            try {
                jobs.refreshFeed() // re-rank the deck against the new profile
            } catch (_: Exception) {
            }
            onSaved()
        }
    }

    class Factory(private val profiles: ProfileRepository, private val jobs: JobRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(profiles, jobs) as T
    }
}
