package com.jinger.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinger.app.data.AppContainer
import com.jinger.app.data.db.JobEntity
import com.jinger.app.ui.theme.Flame
import com.jinger.app.ui.theme.Mint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    container: AppContainer,
    onApply: (JobEntity) -> Unit,
    onOpenTracker: () -> Unit,
    onEditProfile: () -> Unit,
    vm: FeedViewModel = viewModel(factory = FeedViewModel.Factory(container.jobs)),
) {
    val cards by vm.cards.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val exhausted by vm.exhausted.collectAsState()

    LaunchedEffect(cards.size) { vm.onCardsRemaining(cards.size) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jinger", fontWeight = FontWeight.Black, color = Flame) },
                actions = {
                    IconButton(onClick = onEditProfile) { Icon(Icons.Default.Person, "Profile") }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh feed") }
                    IconButton(onClick = onOpenTracker) { Icon(Icons.Default.ListAlt, "Tracker") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    cards.isEmpty() && loading -> CircularProgressIndicator(color = Flame)
                    cards.isEmpty() && error != null -> EmptyState(error!!, "Retry") { vm.loadMore() }
                    cards.isEmpty() && exhausted -> EmptyState("You've seen every job we have right now. Check back later!", "Refresh") { vm.refresh() }
                    cards.isEmpty() -> EmptyState("No jobs loaded yet.", "Load jobs") { vm.loadMore() }
                    else -> CardStack(cards, onSkip = { vm.skip(it) }, onApply = onApply)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                val top = cards.firstOrNull()
                FilledIconButton(
                    onClick = { top?.let { vm.skip(it) } }, enabled = top != null, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Flame.copy(alpha = 0.15f), contentColor = Flame),
                ) { Icon(Icons.Default.Close, "Skip", Modifier.size(32.dp)) }
                FilledIconButton(
                    onClick = { top?.let(onApply) }, enabled = top != null, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Mint.copy(alpha = 0.15f), contentColor = Mint),
                ) { Icon(Icons.Default.Check, "Apply", Modifier.size(32.dp)) }
            }
        }
    }
}

@Composable
private fun CardStack(cards: List<JobEntity>, onSkip: (JobEntity) -> Unit, onApply: (JobEntity) -> Unit) {
    Box(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        // Draw the two cards underneath first (slightly scaled) so the top card sits above them.
        cards.drop(1).take(2).reversed().forEachIndexed { i, job ->
            val depth = 2 - i
            Box(
                Modifier.fillMaxSize().zIndex(-depth.toFloat()).graphicsLayer {
                    scaleX = 1f - depth * 0.04f
                    scaleY = 1f - depth * 0.04f
                    translationY = depth * 18f
                }
            ) { JobCard(job, progress = 0f) }
        }
        val top = cards.first()
        SwipeCard(
            key = top.id,
            onSwiped = { dir -> if (dir == SwipeDirection.RIGHT) onApply(top) else onSkip(top) },
            modifier = Modifier.fillMaxSize().zIndex(1f),
        ) { progress -> JobCard(top, progress) }
    }
}

@Composable
private fun EmptyState(message: String, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text(message, textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
