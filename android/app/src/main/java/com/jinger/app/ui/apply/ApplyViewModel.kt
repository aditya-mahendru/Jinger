package com.jinger.app.ui.apply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jinger.app.data.api.FieldPlanDto
import com.jinger.app.data.api.FillPlanDto
import com.jinger.app.data.api.FillPlanRequest
import com.jinger.app.data.api.FormFieldDto
import com.jinger.app.data.api.JingerApi
import com.jinger.app.data.api.JobDto
import com.jinger.app.data.db.toDto
import com.jinger.app.data.repo.JobRepository
import com.jinger.app.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ApplyStage { LOADING_PAGE, SCANNING, PLANNING, FILLING, REVIEW, NO_FORM, SUBMITTED }

data class ApplyUiState(
    val job: JobDto? = null,
    val stage: ApplyStage = ApplyStage.LOADING_PAGE,
    val autoFilled: Int = 0,
    val needsUser: List<FieldPlanDto> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val llmUsed: Boolean = false,
    val error: String? = null,
    val sheetOpen: Boolean = false,
)

class ApplyViewModel(
    private val jobId: String,
    private val jobs: JobRepository,
    private val profiles: ProfileRepository,
    private val api: JingerApi,
) : ViewModel() {
    private val gson = Gson()
    private val _state = MutableStateFlow(ApplyUiState())
    val state: StateFlow<ApplyUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(job = jobs.get(jobId)?.toDto()) } }
    }

    fun onPageLoaded() {
        if (_state.value.stage == ApplyStage.LOADING_PAGE) _state.update { it.copy(stage = ApplyStage.SCANNING) }
    }

    /** Called with the JSON array returned by autofill.js collectFields(). Returns the plan to apply. */
    fun onFieldsCollected(json: String, onPlan: (List<FieldPlanDto>) -> Unit) {
        val type = object : TypeToken<List<FormFieldDto>>() {}.type
        val fields: List<FormFieldDto> = runCatching { gson.fromJson<List<FormFieldDto>>(json, type) }.getOrDefault(emptyList())
        if (fields.isEmpty()) {
            _state.update { it.copy(stage = ApplyStage.NO_FORM) }
            return
        }
        val job = _state.value.job ?: return
        val profile = profiles.profile.value ?: run {
            _state.update { it.copy(stage = ApplyStage.REVIEW, error = "No profile saved; fill the form manually.") }
            return
        }
        _state.update { it.copy(stage = ApplyStage.PLANNING, error = null) }
        viewModelScope.launch {
            try {
                val plan: FillPlanDto = api.fillPlan(FillPlanRequest(profile, job, fields))
                val auto = plan.fields.filter { !it.needsUser && it.value.isNotBlank() }
                val manual = plan.fields.filter { it.needsUser }.distinctBy { it.selector }
                _state.update {
                    it.copy(
                        stage = ApplyStage.FILLING,
                        autoFilled = auto.size,
                        needsUser = manual,
                        drafts = plan.drafts,
                        llmUsed = plan.llmUsed,
                    )
                }
                onPlan(auto)
            } catch (e: Exception) {
                _state.update { it.copy(stage = ApplyStage.REVIEW, error = "Fill plan failed: ${e.message}") }
            }
        }
    }

    fun onFilled() {
        _state.update { it.copy(stage = ApplyStage.REVIEW, sheetOpen = it.needsUser.isNotEmpty()) }
    }

    fun setSheet(open: Boolean) = _state.update { it.copy(sheetOpen = open) }

    fun updateDraft(selector: String, value: String) = _state.update { s ->
        s.copy(needsUser = s.needsUser.map { if (it.selector == selector) it.copy(value = value) else it })
    }

    fun resolveField(selector: String) = _state.update { s ->
        s.copy(needsUser = s.needsUser.filterNot { it.selector == selector })
    }

    fun markSubmitted(onDone: () -> Unit) {
        if (_state.value.stage == ApplyStage.SUBMITTED) return
        _state.update { it.copy(stage = ApplyStage.SUBMITTED, sheetOpen = false) }
        viewModelScope.launch {
            jobs.markApplied(jobId)
            onDone()
        }
    }

    fun toJson(plan: List<FieldPlanDto>): String =
        gson.toJson(plan.map { mapOf("selector" to it.selector, "value" to it.value) })

    fun selectorsJson(selectors: List<String>): String = gson.toJson(selectors)
}
