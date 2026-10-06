package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.healthcheck.buddy.ai.AiException
import com.healthcheck.buddy.ai.FoodAnalysis
import com.healthcheck.buddy.data.MealEntry
import com.healthcheck.buddy.data.today
import com.healthcheck.buddy.domain.Nutrition
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.NeedsKeyCard
import com.healthcheck.buddy.ui.components.SectionTitle
import com.healthcheck.buddy.ui.components.rememberPhotoSource
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch
import java.io.File

private sealed interface AddState {
    data object Idle : AddState
    data object ChooseSource : AddState
    data class Analyzing(val photo: File) : AddState
    /** Review/edit before saving. [analysis] is null for manual entry. */
    data class Review(val photo: File?, val analysis: FoodAnalysis?) : AddState
}

@Composable
fun TodayScreen(profile: Profile, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = context.repo
    val scope = rememberCoroutineScope()
    val day = remember { today() }
    val meals by remember { repo.db.meals().forDay(day) }.collectAsState(initial = emptyList())
    val apiKey by repo.settings.apiKey.collectAsState(initial = null)
    val targets = remember(profile) { Nutrition.targets(profile) }

    var add by remember { mutableStateOf<AddState>(AddState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkIn by remember { mutableStateOf<String?>(null) }

    val photos = rememberPhotoSource { file ->
        add = AddState.Analyzing(file)
        scope.launch {
            val buddy = repo.buddy() ?: run { add = AddState.Review(file, null); return@launch }
            add = try {
                AddState.Review(file, buddy.analyzeFood(file, "", repo.context()))
            } catch (e: AiException) {
                error = e.message
                AddState.Review(file, null)
            }
        }
    }

    // One AI check-in per day, refreshed when the key appears.
    LaunchedEffect(apiKey) {
        checkIn = repo.settings.checkInFor(day)
        if (checkIn == null && apiKey != null) {
            val buddy = repo.buddy() ?: return@LaunchedEffect
            runCatching { buddy.dailyCheckIn(repo.context()) }.onSuccess {
                checkIn = it
                repo.settings.saveCheckIn(day, it)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { add = AddState.ChooseSource },
                icon = { Icon(Icons.Default.CameraAlt, null) },
                text = { Text("Log a meal") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BuddyCard(
                    name = profile.buddyName,
                    text = checkIn ?: localGreeting(profile, meals.sumOf { it.calories }, targets.calories),
                )
            }
            item { CalorieSummary(meals, targets.calories, targets.proteinG, targets.carbsG, targets.fatG) }
            if (apiKey == null) item { NeedsKeyCard("food photo scanning", onOpenSettings) }
            item { SectionTitle("Today's meals") }
            if (meals.isEmpty()) item {
                Text("Nothing yet. Snap your next meal and I'll count it for you.", style = MaterialTheme.typography.bodyMedium)
            }
            items(meals, key = { it.id }) { meal ->
                MealRow(meal, onDelete = { scope.launch { repo.db.meals().delete(meal) } })
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    when (val s = add) {
        AddState.Idle -> Unit
        AddState.ChooseSource -> AlertDialog(
            onDismissRequest = { add = AddState.Idle },
            title = { Text("Log a meal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { add = AddState.Idle; photos.takePhoto() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.size(8.dp)); Text("Take photo")
                    }
                    OutlinedButton(onClick = { add = AddState.Idle; photos.pickPhoto() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.size(8.dp)); Text("From gallery")
                    }
                    OutlinedButton(onClick = { add = AddState.Review(null, null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.size(8.dp)); Text("Enter manually")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { add = AddState.Idle }) { Text("Cancel") } },
        )
        is AddState.Analyzing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("${profile.buddyName} is looking…") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(s.photo, null, Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
        )
        is AddState.Review -> ReviewMealDialog(
            state = s,
            onDismiss = { add = AddState.Idle },
            onSave = { meal ->
                scope.launch { repo.db.meals().insert(meal) }
                add = AddState.Idle
            },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Couldn't analyze that") },
            text = { Text("$it\n\nYou can still enter the values yourself.") },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

private fun localGreeting(p: Profile, eaten: Int, target: Int): String {
    val left = target - eaten
    val who = p.name.ifBlank { "friend" }
    return when {
        eaten == 0 -> "Morning, $who! Fresh day — log your first meal and let's see where we land."
        left > 600 -> "Nice start, $who. About $left kcal to go — make the next one protein-heavy."
        left > 0 -> "Almost there — $left kcal left. A light, protein-rich dinner fits perfectly."
        left > -200 -> "Right on target today. That's exactly how progress happens."
        else -> "A bit over today (${-left} kcal) — no stress, one day doesn't define the week."
    }
}

@Composable
private fun BuddyCard(name: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CalorieSummary(meals: List<MealEntry>, kcal: Int, protein: Int, carbs: Int, fat: Int) {
    val eaten = meals.sumOf { it.calories }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                val fill = if (eaten > kcal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                    drawArc(fill, -90f, 360f * (eaten.toFloat() / kcal).coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$eaten", style = MaterialTheme.typography.headlineSmall)
                    Text("of $kcal", style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Macro("Protein", meals.sumOf { it.proteinG }, protein)
                Macro("Carbs", meals.sumOf { it.carbsG }, carbs)
                Macro("Fat", meals.sumOf { it.fatG }, fat)
            }
        }
    }
}

@Composable
private fun Macro(label: String, value: Int, target: Int) {
    Column {
        Text("$label  $value / $target g", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (value.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun MealRow(meal: MealEntry, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            meal.photoPath?.let {
                AsyncImage(File(it), null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(meal.name, style = MaterialTheme.typography.titleSmall)
                Text("${meal.calories} kcal · P ${meal.proteinG} · C ${meal.carbsG} · F ${meal.fatG}", style = MaterialTheme.typography.bodySmall)
                meal.buddyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun ReviewMealDialog(state: AddState.Review, onDismiss: () -> Unit, onSave: (MealEntry) -> Unit) {
    val a = state.analysis
    var name by remember { mutableStateOf(a?.mealName ?: "") }
    var kcal by remember { mutableStateOf(a?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(a?.proteinG?.toString() ?: "") }
    var carbs by remember { mutableStateOf(a?.carbsG?.toString() ?: "") }
    var fat by remember { mutableStateOf(a?.fatG?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a != null) "Here's my guess" else "Add meal") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.photo?.let { item { AsyncImage(it, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) } }
                if (a != null) {
                    item { Text(a.buddyNote, color = MaterialTheme.colorScheme.primary) }
                    items(a.items) { Text("• ${it.name} (${it.portion}) — ${it.calories} kcal", style = MaterialTheme.typography.bodySmall) }
                    item { Text("Confidence: ${a.confidence}. Adjust anything that looks off.", style = MaterialTheme.typography.labelSmall) }
                }
                item { OutlinedTextField(name, { name = it }, label = { Text("Meal") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Num("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                        Num("Protein g", protein, Modifier.weight(1f)) { protein = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Num("Carbs g", carbs, Modifier.weight(1f)) { carbs = it }
                        Num("Fat g", fat, Modifier.weight(1f)) { fat = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && kcal.toIntOrNull() != null,
                onClick = {
                    onSave(
                        MealEntry(
                            day = today(), timestamp = System.currentTimeMillis(), name = name.trim(),
                            calories = kcal.toInt(), proteinG = protein.toIntOrNull() ?: 0,
                            carbsG = carbs.toIntOrNull() ?: 0, fatG = fat.toIntOrNull() ?: 0,
                            photoPath = state.photo?.absolutePath, buddyNote = a?.buddyNote,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
