package com.jinger.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinger.app.data.AppContainer
import com.jinger.app.data.api.EducationDto
import com.jinger.app.data.api.ExperienceDto
import com.jinger.app.ui.theme.Flame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
    vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(container.profile, container.jobs)),
) {
    val draft by vm.draft.collectAsState()
    val parsing by vm.parsing.collectAsState()
    val error by vm.error.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCv)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Your profile", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Flame.copy(alpha = 0.08f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Upload your CV", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "We read it once, fill the form below, and attach it to applications for you. Edit anything that looks off.",
                        color = Color.DarkGray,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !parsing) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (vm.hasCv) "Replace CV (PDF)" else "Pick CV (PDF)")
                        }
                        if (parsing) CircularProgressIndicator(Modifier.size(22.dp), color = Flame, strokeWidth = 2.dp)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }

            Field("Full name", draft.name) { v -> vm.update { copy(name = v) } }
            Field("Email", draft.email) { v -> vm.update { copy(email = v) } }
            Field("Phone", draft.phone) { v -> vm.update { copy(phone = v) } }
            Field("Location", draft.location) { v -> vm.update { copy(location = v) } }
            Field("LinkedIn", draft.links.linkedin) { v -> vm.update { copy(links = links.copy(linkedin = v)) } }
            Field("GitHub", draft.links.github) { v -> vm.update { copy(links = links.copy(github = v)) } }
            Field("Website / portfolio", draft.links.website) { v -> vm.update { copy(links = links.copy(website = v)) } }
            Field("Summary", draft.summary, minLines = 3) { v -> vm.update { copy(summary = v) } }
            Field("Skills (comma separated)", draft.skills.joinToString(", ")) { v ->
                vm.update { copy(skills = v.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
            }

            SectionTitle("Experience")
            draft.experience.forEachIndexed { i, exp ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Company", exp.company) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(company = v) }) } }
                        Field("Title", exp.title) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(title = v) }) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Start", exp.start, Modifier.weight(1f)) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(start = v) }) } }
                            Field("End", exp.end, Modifier.weight(1f)) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(end = v) }) } }
                        }
                        Field("Summary", exp.summary, minLines = 2) { v -> vm.update { copy(experience = experience.replaceAt(i) { copy(summary = v) }) } }
                    }
                }
            }
            OutlinedButton(onClick = { vm.update { copy(experience = experience + ExperienceDto()) } }) { Text("+ Add experience") }

            SectionTitle("Education")
            draft.education.forEachIndexed { i, edu ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("School", edu.school) { v -> vm.update { copy(education = education.replaceAt(i) { copy(school = v) }) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Degree", edu.degree, Modifier.weight(1f)) { v -> vm.update { copy(education = education.replaceAt(i) { copy(degree = v) }) } }
                            Field("Field", edu.field, Modifier.weight(1f)) { v -> vm.update { copy(education = education.replaceAt(i) { copy(field = v) }) } }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { vm.update { copy(education = education + EducationDto()) } }) { Text("+ Add education") }

            SectionTitle("Work authorisation")
            Field("Authorised to work in (comma separated)", draft.workAuthorization.authorizedIn.joinToString(", ")) { v ->
                vm.update { copy(workAuthorization = workAuthorization.copy(authorizedIn = v.split(",").map { it.trim() }.filter { it.isNotEmpty() })) }
            }
            Field("Sponsorship / visa notes", draft.workAuthorization.notes) { v ->
                vm.update { copy(workAuthorization = workAuthorization.copy(notes = v)) }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(onDone) },
                enabled = draft.name.isNotBlank() && draft.email.isNotBlank() && !parsing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save & start swiping", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, minLines = minLines,
        modifier = modifier.fillMaxWidth(), singleLine = minLines == 1,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

private inline fun <T> List<T>.replaceAt(index: Int, transform: T.() -> T): List<T> =
    mapIndexed { i, item -> if (i == index) item.transform() else item }
