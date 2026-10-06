package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.healthcheck.buddy.ai.AiException
import com.healthcheck.buddy.ai.ProgressReview
import com.healthcheck.buddy.data.ProgressPhoto
import com.healthcheck.buddy.data.WeightEntry
import com.healthcheck.buddy.data.today
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.NeedsKeyCard
import com.healthcheck.buddy.ui.components.SectionTitle
import com.healthcheck.buddy.ui.components.rememberPhotoSource
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProgressScreen(profile: Profile, onOpenSettings: () -> Unit) {
    val repo = LocalContext.current.repo
    val scope = rememberCoroutineScope()
    val weights by remember { repo.db.weights().all() }.collectAsState(initial = emptyList())
    val photos by remember { repo.db.photos().all() }.collectAsState(initial = emptyList())
    val apiKey by repo.settings.apiKey.collectAsState(initial = null)

    var weightInput by remember { mutableStateOf("") }
    var reviewing by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<ProgressReview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val photoSource = rememberPhotoSource { file ->
        scope.launch {
            repo.db.photos().insert(
                ProgressPhoto(day = today(), timestamp = System.currentTimeMillis(), path = file.absolutePath, weightKg = weights.lastOrNull()?.weightKg)
            )
        }
    }

    fun logWeight() {
        val kg = weightInput.replace(',', '.').toDoubleOrNull() ?: return
        weightInput = ""
        scope.launch {
            repo.db.weights().insert(WeightEntry(day = today(), timestamp = System.currentTimeMillis(), weightKg = kg))
            repo.settings.saveProfile(profile.copy(weightKg = kg)) // keeps targets in sync with current weight
        }
    }

    fun runReview() {
        val latest = photos.firstOrNull() ?: return
        reviewing = true
        error = null
        scope.launch {
            try {
                val buddy = repo.buddy() ?: throw AiException("Add your API key in Settings first.")
                val first = repo.db.photos().first()
                val result = buddy.reviewProgress(first?.let { File(it.path) }, File(latest.path), repo.context())
                review = result
                repo.db.photos().update(latest.copy(aiFeedback = result.summary))
            } catch (e: AiException) {
                error = e.message
            } finally {
                reviewing = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Weight") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    weightInput, { weightInput = it }, label = { Text("Today's weight (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                Button(onClick = ::logWeight, enabled = weightInput.isNotBlank()) { Text("Log") }
            }
        }
        item { WeightChart(weights, profile.goalWeightKg) }
        item {
            val first = weights.firstOrNull()?.weightKg
            val last = weights.lastOrNull()?.weightKg
            if (first != null && last != null && weights.size > 1) {
                val diff = last - first
                Text(
                    "%s%.1f kg since you started%s".format(
                        if (diff > 0) "+" else "", diff,
                        profile.goalWeightKg?.let { " · %.1f kg to goal".format(kotlin.math.abs(it - last)) } ?: "",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { SectionTitle("Progress photos") }
        item {
            Text(
                "Same spot, same light, same time of day — ideally once a week. Photos stay on your phone; " +
                    "they're only sent to Claude when you tap \"Check my progress\".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = photoSource.takePhoto) { Icon(Icons.Default.CameraAlt, null); Text("  Take") }
                OutlinedButton(onClick = photoSource.pickPhoto) { Icon(Icons.Default.PhotoLibrary, null); Text("  Pick") }
            }
        }
        if (photos.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p ->
                    Column {
                        AsyncImage(File(p.path), null, Modifier.size(110.dp, 150.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Text(p.day, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            when {
                apiKey == null -> NeedsKeyCard("AI progress checks", onOpenSettings)
                photos.isEmpty() -> Text("Add a photo to get an honest progress check.", style = MaterialTheme.typography.bodyMedium)
                else -> Button(onClick = ::runReview, enabled = !reviewing, modifier = Modifier.fillMaxWidth()) {
                    if (reviewing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Check my progress")
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        review?.let { item { ReviewCard(it, profile.buddyName) } }
    }
}

@Composable
private fun ReviewCard(r: ProgressReview, buddyName: String) {
    val (label, color) = when (r.verdict) {
        "on_track" -> "On track ✅" to MaterialTheme.colorScheme.primaryContainer
        "slow" -> "Slower than planned" to MaterialTheme.colorScheme.tertiaryContainer
        "off_track" -> "Off track — let's fix it" to MaterialTheme.colorScheme.errorContainer
        else -> "Too early to tell" to MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(r.summary)
            if (r.observations.isNotEmpty()) {
                Text("What I see", style = MaterialTheme.typography.labelLarge)
                r.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (r.nextSteps.isNotEmpty()) {
                Text("Next steps", style = MaterialTheme.typography.labelLarge)
                r.nextSteps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            Text("— $buddyName: ${r.buddyNote}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WeightChart(entries: List<WeightEntry>, goal: Double?) {
    if (entries.size < 2) {
        Text("Log a few weigh-ins and your trend shows up here.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val line = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.secondary
    Card(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(16.dp)) {
            val values = entries.map { it.weightKg } + listOfNotNull(goal)
            val min = values.min() - 0.5
            val max = values.max() + 0.5
            fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
            val stepX = size.width / (entries.size - 1)

            goal?.let { drawLine(goalColor, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2.dp.toPx()) }
            val path = Path()
            entries.forEachIndexed { i, e ->
                val p = Offset(i * stepX, y(e.weightKg))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                drawCircle(line, 3.dp.toPx(), p)
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
