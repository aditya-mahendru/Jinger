package com.jinger.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinger.app.data.AppContainer
import com.jinger.app.data.api.EducationDto
import com.jinger.app.data.api.ExperienceDto
import com.jinger.app.ui.theme.Flame
import com.jinger.app.ui.theme.Ink
import com.jinger.app.ui.theme.Violet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val HeaderGradient = Brush.linearGradient(listOf(Violet, Flame))
private val FieldShape = RoundedCornerShape(18.dp)
private val SoftCard = RoundedCornerShape(22.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
    vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(container.profile, container.jobs)),
) {
    val draft by vm.draft.collectAsState()
    val parsing by vm.parsing.collectAsState()
    var skillText by remember { mutableStateOf("") }
    var countryText by remember { mutableStateOf("") }
    val addSkill = { s: String -> vm.update { copy(skills = (skills + s).distinctBy { it.lowercase() }) } }
    val addCountry = { c: String ->
        vm.update { copy(workAuthorization = workAuthorization.copy(authorizedIn = (workAuthorization.authorizedIn + c).distinct())) }
    }
    val error by vm.error.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCv)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        ProfileHeader(
            name = draft.name, subtitle = listOf(draft.email, draft.location).filter { it.isNotBlank() }.joinToString("  ·  "),
            parsing = parsing, hasCv = vm.hasCv, error = error,
            onPickCv = { picker.launch(arrayOf("application/pdf")) },
        )

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(2.dp))
            Section("About you") {
                Field("Full name", draft.name, capitalization = KeyboardCapitalization.Words) { v -> vm.update { copy(name = v) } }
                Field("Email", draft.email, keyboard = KeyboardType.Email) { v -> vm.update { copy(email = v) } }
                Field("Phone", draft.phone, keyboard = KeyboardType.Phone, placeholder = "+353 89 123 4567") { v -> vm.update { copy(phone = v) } }
                Field("Location", draft.location, capitalization = KeyboardCapitalization.Words, placeholder = "Dublin, Ireland") { v ->
                    vm.update { copy(location = v) }
                }
                Field("About me", draft.summary, minLines = 3, placeholder = "Two or three lines recruiters should read first") { v ->
                    vm.update { copy(summary = v) }
                }
            }

            Section("Links") {
                Field("LinkedIn", draft.links.linkedin, keyboard = KeyboardType.Uri, placeholder = "https://linkedin.com/in/you") { v ->
                    vm.update { copy(links = links.copy(linkedin = v)) }
                }
                Field("GitHub", draft.links.github, keyboard = KeyboardType.Uri, placeholder = "https://github.com/you") { v ->
                    vm.update { copy(links = links.copy(github = v)) }
                }
                Field("Website / portfolio", draft.links.website, keyboard = KeyboardType.Uri) { v -> vm.update { copy(links = links.copy(website = v)) } }
            }

            Section("Skills") {
                ChipEditor(
                    items = draft.skills, text = skillText, onTextChange = { skillText = it }, placeholder = "Add a skill, e.g. Kotlin",
                    onAdd = addSkill,
                    onRemove = { s -> vm.update { copy(skills = skills - s) } },
                )
            }

            Section("Experience", action = { AddButton("Add role") { vm.update { copy(experience = experience + ExperienceDto()) } } }) {
                if (draft.experience.isEmpty()) EmptyHint("No roles yet — import your CV or add one.")
                draft.experience.forEachIndexed { i, exp ->
                    SoftCard(onRemove = { vm.update { copy(experience = experience.filterIndexed { j, _ -> j != i }) } }) {
                        Field("Company", exp.company, capitalization = KeyboardCapitalization.Words) { v ->
                            vm.update { copy(experience = experience.replaceAt(i) { copy(company = v) }) }
                        }
                        Field("Title", exp.title, capitalization = KeyboardCapitalization.Words) { v ->
                            vm.update { copy(experience = experience.replaceAt(i) { copy(title = v) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MonthField("Start", exp.start, Modifier.weight(1f)) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(start = v) }) } }
                            MonthField("End", exp.end, Modifier.weight(1f), allowPresent = true) { v ->
                                vm.update { copy(experience = experience.replaceAt(i) { copy(end = v) }) }
                            }
                        }
                        Field("What you did", exp.summary, minLines = 2) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(summary = v) }) } }
                    }
                }
            }

            Section("Education", action = { AddButton("Add school") { vm.update { copy(education = education + EducationDto()) } } }) {
                if (draft.education.isEmpty()) EmptyHint("No education yet.")
                draft.education.forEachIndexed { i, edu ->
                    SoftCard(onRemove = { vm.update { copy(education = education.filterIndexed { j, _ -> j != i }) } }) {
                        Field("School", edu.school, capitalization = KeyboardCapitalization.Words) { v ->
                            vm.update { copy(education = education.replaceAt(i) { copy(school = v) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("Degree", edu.degree, Modifier.weight(1f), placeholder = "MSc") { v ->
                                vm.update { copy(education = education.replaceAt(i) { copy(degree = v) }) }
                            }
                            Field("Field", edu.field, Modifier.weight(1f), placeholder = "Computer Science") { v ->
                                vm.update { copy(education = education.replaceAt(i) { copy(field = v) }) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MonthField("Start", edu.start, Modifier.weight(1f)) { v -> vm.update { copy(education = education.replaceAt(i) { copy(start = v) }) } }
                            MonthField("End", edu.end, Modifier.weight(1f), allowPresent = true) { v ->
                                vm.update { copy(education = education.replaceAt(i) { copy(end = v) }) }
                            }
                        }
                    }
                }
            }

            Section("Work authorisation") {
                Text("Where can you work without sponsorship?", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                ChipEditor(
                    items = draft.workAuthorization.authorizedIn, text = countryText, onTextChange = { countryText = it }, placeholder = "Add a country, e.g. Ireland",
                    onAdd = addCountry,
                    onRemove = { c -> vm.update { copy(workAuthorization = workAuthorization.copy(authorizedIn = workAuthorization.authorizedIn - c)) } },
                )
                Text("Will you need visa sponsorship?", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                val options = listOf("Yes" to true, "No" to false, "Not sure" to null)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (label, value) ->
                        SegmentedButton(
                            selected = draft.workAuthorization.requiresSponsorship == value,
                            onClick = { vm.update { copy(workAuthorization = workAuthorization.copy(requiresSponsorship = value)) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                Field("Notes for recruiters", draft.workAuthorization.notes, placeholder = "e.g. Stamp 1G until 2027") { v ->
                    vm.update { copy(workAuthorization = workAuthorization.copy(notes = v)) }
                }
            }

            Button(
                onClick = {
                    // a half-typed chip must not be lost just because the user tapped Save instead of Add
                    skillText.pendingChip()?.let(addSkill); skillText = ""
                    countryText.pendingChip()?.let(addCountry); countryText = ""
                    vm.save(onDone)
                },
                enabled = draft.name.isNotBlank() && draft.email.isNotBlank() && !parsing,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save & start swiping", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProfileHeader(name: String, subtitle: String, parsing: Boolean, hasCv: Boolean, error: String?, onPickCv: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(HeaderGradient)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                val initials = name.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
                if (initials.isEmpty()) Icon(Icons.Default.Person, null, Modifier.size(52.dp), tint = Flame)
                else Text(initials, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Flame)
            }
            Text(
                name.ifBlank { "Your profile" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onPickCv, enabled = !parsing, shape = RoundedCornerShape(24.dp)) {
                if (parsing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Reading your CV…")
                } else {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (hasCv) "Replace CV (PDF)" else "Import CV (PDF)")
                }
            }
            Text(
                error ?: "We fill everything below from your CV and attach it to applications. Edit anything that looks off.",
                color = if (error != null) Color(0xFFFFE0E0) else Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Section(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(shape = SoftCard, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SoftCard(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Flame.copy(alpha = 0.06f)).padding(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
            Icon(Icons.Default.Close, "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.size(4.dp))
        Text(label)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, minLines = minLines,
        placeholder = placeholder?.let { { Text(it, color = Color.LightGray) } },
        modifier = modifier.fillMaxWidth(), singleLine = minLines == 1, shape = FieldShape,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard, capitalization = capitalization,
            imeAction = if (minLines == 1) ImeAction.Next else ImeAction.Default,
        ),
    )
}

/** Month picker stored as "YYYY-MM" (or "Present"); tapping opens a calendar instead of a free-text box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthField(label: String, value: String, modifier: Modifier = Modifier, allowPresent: Boolean = false, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true, shape = FieldShape,
            placeholder = { Text("YYYY-MM", color = Color.LightGray) },
            trailingIcon = { Icon(Icons.Default.DateRange, null, tint = Flame) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay: a read-only text field swallows taps, so catch them here.
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(FieldShape).clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = parseMonth(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(formatMonth(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowPresent) TextButton(onClick = { onChange("Present"); open = false }) { Text("Present") }
                    TextButton(onClick = { onChange(""); open = false }) { Text("Clear") }
                }
            },
        ) { DatePicker(state = state, title = { Text("  $label date", Modifier.padding(16.dp)) }) }
    }
}

private fun parseMonth(value: String): Long? {
    val m = Regex("(\\d{4})(?:-(\\d{1,2}))?").find(value) ?: return null
    val year = m.groupValues[1].toInt()
    val month = m.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 1
    return LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun formatMonth(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipEditor(
    items: List<String>,
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val commit = {
        text.pendingChip()?.let(onAdd)
        onTextChange("")
    }
    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                InputChip(
                    selected = true, onClick = { onRemove(item) }, label = { Text(item) }, shape = RoundedCornerShape(16.dp),
                    trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) },
                )
            }
        }
    }
    OutlinedTextField(
        value = text, onValueChange = { if (it.endsWith(",")) { it.pendingChip()?.let(onAdd); onTextChange("") } else onTextChange(it) },
        placeholder = { Text(placeholder, color = Color.LightGray) }, singleLine = true, shape = FieldShape,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = { IconButton(onClick = commit) { Icon(Icons.Default.Add, "Add", tint = Flame) } },
    )
}

private fun String.pendingChip(): String? = trim().trimEnd(',').takeIf { it.isNotEmpty() }

private inline fun <T> List<T>.replaceAt(index: Int, transform: T.() -> T): List<T> =
    mapIndexed { i, item -> if (i == index) item.transform() else item }
