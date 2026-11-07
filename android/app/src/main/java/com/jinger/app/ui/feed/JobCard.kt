package com.jinger.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinger.app.data.db.JobEntity
import com.jinger.app.ui.theme.Flame
import com.jinger.app.ui.theme.Mint
import com.jinger.app.ui.theme.Sun

@Composable
fun JobCard(job: JobEntity, progress: Float, modifier: Modifier = Modifier) {
    var expanded by remember(job.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxSize().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Flame.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Business, null, tint = Flame) }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.company, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        Text(job.source.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = Color.LightGray)
                    }
                    job.fitScore?.let { FitBadge(it) }
                }
                Spacer(Modifier.height(16.dp))
                Text(job.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Place, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Text(job.location.ifBlank { "Location not listed" }, color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (job.remote) Tag("Remote", Mint)
                }
                job.fitReason?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sun.copy(alpha = 0.15f)).padding(12.dp)) {
                        Text("Why it fits: $it", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    job.description.ifBlank { "No description provided." },
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    maxLines = if (expanded) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (expanded) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,
                )
                if (!expanded) {
                    Spacer(Modifier.weight(1f))
                    Text("Tap to read more", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            // Colour hints while dragging: green APPLY on the right, red SKIP on the left.
            if (progress > 0.05f) Stamp("APPLY", Mint, progress, Modifier.align(Alignment.TopStart).padding(28.dp))
            if (progress < -0.05f) Stamp("SKIP", Flame, -progress, Modifier.align(Alignment.TopEnd).padding(28.dp))
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, modifier: Modifier) {
    Box(
        modifier
            .border(3.dp, color.copy(alpha = alpha), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = color.copy(alpha = alpha), fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp)
    }
}

@Composable
fun FitBadge(score: Int) {
    val color = when {
        score >= 70 -> Mint
        score >= 45 -> Sun
        else -> Color.Gray
    }
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("$score% fit", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
