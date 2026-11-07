package com.jinger.app.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jinger.app.data.AppContainer
import com.jinger.app.data.db.JobEntity
import com.jinger.app.ui.feed.FitBadge
import com.jinger.app.ui.feed.Tag
import com.jinger.app.ui.theme.Flame
import com.jinger.app.ui.theme.Mint
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(container: AppContainer, onBack: () -> Unit, onOpen: (JobEntity) -> Unit) {
    val applied by container.jobs.applied.collectAsState(initial = emptyList())
    val skipped by container.jobs.skipped.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Applied (${applied.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Skipped (${skipped.size})") })
            }
            val list = if (tab == 0) applied else skipped
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == 0) "Nothing applied yet. Swipe right on a job to start." else "No skipped jobs yet.",
                        color = Color.Gray,
                    )
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { job -> TrackerRow(job, applied = tab == 0, onClick = { onOpen(job) }) }
                }
            }
        }
    }
}

@Composable
private fun TrackerRow(job: JobEntity, applied: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.company, fontWeight = FontWeight.SemiBold, color = Color.Gray, modifier = Modifier.weight(1f))
                job.fitScore?.let { FitBadge(it) }
            }
            Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (applied) "Applied" else "Skipped", if (applied) Mint else Flame)
                Text(job.location, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                job.decidedAt?.let { Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)), color = Color.Gray) }
            }
        }
    }
}
