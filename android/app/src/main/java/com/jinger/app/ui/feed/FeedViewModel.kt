package com.jinger.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinger.app.data.db.JobEntity
import com.jinger.app.data.repo.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedViewModel(private val repo: JobRepository) : ViewModel() {
    val cards: StateFlow<List<JobEntity>> =
        repo.feed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted

    init {
        loadMore()
    }

    fun loadMore() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _exhausted.value = repo.loadMore() == 0
            } catch (e: Exception) {
                _error.value = "Can't reach the Jinger backend: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _exhausted.value = repo.refreshFeed() == 0
            } catch (e: Exception) {
                _error.value = "Can't reach the Jinger backend: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Keep the stack topped up so the swipe never hits an empty deck mid-session. */
    fun onCardsRemaining(count: Int) {
        if (count <= 3 && !_exhausted.value) loadMore()
    }

    fun skip(job: JobEntity) = viewModelScope.launch { repo.skip(job.id) }

    class Factory(private val repo: JobRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repo) as T
    }
}
