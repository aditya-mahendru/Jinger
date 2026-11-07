package com.jinger.app.data.api

import com.google.gson.annotations.SerializedName

data class JobDto(
    val id: String,
    val source: String,
    val company: String,
    val title: String,
    val location: String,
    val remote: Boolean,
    val description: String,
    @SerializedName("apply_url") val applyUrl: String,
    @SerializedName("posted_at") val postedAt: String?,
    @SerializedName("fit_score") val fitScore: Int? = null,
    @SerializedName("fit_reason") val fitReason: String? = null,
)

data class SeenRequest(@SerializedName("job_ids") val jobIds: List<String>, val status: String)

data class OkResponse(val ok: Boolean)

data class LinksDto(
    var linkedin: String = "",
    var github: String = "",
    var website: String = "",
    var other: List<String> = emptyList(),
)

data class EducationDto(
    var school: String = "", var degree: String = "", var field: String = "", var start: String = "", var end: String = "",
)

data class ExperienceDto(
    var company: String = "", var title: String = "", var start: String = "", var end: String = "", var summary: String = "",
)

data class WorkAuthorizationDto(
    @SerializedName("authorized_in") var authorizedIn: List<String> = emptyList(),
    @SerializedName("requires_sponsorship") var requiresSponsorship: Boolean? = null,
    var notes: String = "",
)

data class ProfileDto(
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var location: String = "",
    var summary: String = "",
    var links: LinksDto = LinksDto(),
    var education: List<EducationDto> = emptyList(),
    var experience: List<ExperienceDto> = emptyList(),
    var skills: List<String> = emptyList(),
    @SerializedName("work_authorization") var workAuthorization: WorkAuthorizationDto = WorkAuthorizationDto(),
    @SerializedName("cv_text") var cvText: String = "",
)

data class FormFieldDto(
    val selector: String,
    val label: String = "",
    val name: String = "",
    val id: String = "",
    val type: String = "text",
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val value: String = "",
)

data class FillPlanRequest(val profile: ProfileDto, val job: JobDto, val fields: List<FormFieldDto>)

data class FieldPlanDto(
    val selector: String,
    val label: String = "",
    val value: String = "",
    val confidence: Double = 0.0,
    @SerializedName("needs_user") val needsUser: Boolean = true,
    val reason: String = "",
)

data class FillPlanDto(
    val fields: List<FieldPlanDto>,
    val drafts: Map<String, String> = emptyMap(),
    @SerializedName("llm_used") val llmUsed: Boolean = false,
)
