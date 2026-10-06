package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.healthcheck.buddy.ai.AiException
import com.healthcheck.buddy.ai.MealPlan
import com.healthcheck.buddy.domain.Nutrition
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.NeedsKeyCard
import com.healthcheck.buddy.ui.components.SectionTitle
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun PlanScreen(profile: Profile, onOpenSettings: () -> Unit) {
    val repo = LocalContext.current.repo
    val scope = rememberCoroutineScope()
    val apiKey by repo.settings.apiKey.collectAsState(initial = null)
    val savedPlanJson by repo.settings.planJson.collectAsState(initial = null)
    val plan: MealPlan? = remember(savedPlanJson) {
        savedPlanJson?.let { runCatching { Json.decodeFromString<MealPlan>(it) }.getOrNull() }
    }
    val t = remember(profile) { Nutrition.targets(profile) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun generate() {
        loading = true
        error = null
        scope.launch {
            try {
                val b = repo.buddy() ?: throw AiException("Add your API key in Settings first.")
                repo.settings.savePlan(Json.encodeToString(b.generatePlan(repo.context())))
            } catch (e: AiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Baseline", style = MaterialTheme.typography.labelLarge)
                    Text("${t.calories} kcal", style = MaterialTheme.typography.headlineMedium)
                    Text("P ${t.proteinG} g · C ${t.carbsG} g · F ${t.fatG} g")
                    Text(
                        "From your stats (maintenance ≈ ${Nutrition.tdee(profile)} kcal, goal: ${profile.goal.label.lowercase()}).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            if (apiKey == null) NeedsKeyCard("your AI-optimized plan", onOpenSettings)
            else Button(onClick = ::generate, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (plan == null) "Build my optimal plan" else "Rebuild my plan")
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        plan?.let { p ->
            item { SectionTitle("${profile.buddyName}'s plan for you") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text("Training day", style = MaterialTheme.typography.labelMedium)
                                Text("${p.trainingDayCalories} kcal", style = MaterialTheme.typography.titleLarge)
                            }
                            Column {
                                Text("Rest day", style = MaterialTheme.typography.labelMedium)
                                Text("${p.restDayCalories} kcal", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Text("P ${p.proteinG} g · C ${p.carbsG} g · F ${p.fatG} g")
                        Text(p.rationale, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionTitle("Sample training day") }
            items(p.trainingDayMeals) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${m.time} · ${m.name}", style = MaterialTheme.typography.titleSmall)
                        Text(m.description, style = MaterialTheme.typography.bodySmall)
                        Text("${m.calories} kcal · ${m.proteinG} g protein", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (p.tips.isNotEmpty()) {
                item { SectionTitle("Tips") }
                items(p.tips) { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
