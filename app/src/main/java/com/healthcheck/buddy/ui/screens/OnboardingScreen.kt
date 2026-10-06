package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.healthcheck.buddy.data.WeightEntry
import com.healthcheck.buddy.data.today
import com.healthcheck.buddy.domain.Nutrition
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val repo = LocalContext.current.repo
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf(Profile()) }
    var key by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> {
                Text("Hey! 👋", style = MaterialTheme.typography.displaySmall)
                Text(
                    "I'm your health buddy. Tell me a bit about you and I'll figure out what you actually need to eat " +
                        "for your goal — then I'll keep you company along the way.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                ProfileForm(profile) { profile = it }
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }
            1 -> {
                val t = Nutrition.targets(profile)
                Text("Your starting point", style = MaterialTheme.typography.headlineSmall)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${t.calories} kcal / day", style = MaterialTheme.typography.headlineMedium)
                        Text("Protein ${t.proteinG} g · Carbs ${t.carbsG} g · Fat ${t.fatG} g")
                        Text(
                            "Maintenance is about ${Nutrition.tdee(profile)} kcal. Add your API key and ${profile.buddyName} " +
                                "can fine-tune this to your exact training.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text("Claude API key (optional)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Unlocks food photo scanning, progress checks, your AI meal plan and chatting with " +
                        "${profile.buddyName}. Get one at console.anthropic.com. It's stored encrypted on this phone only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("sk-ant-…") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            repo.settings.saveApiKey(key)
                            repo.db.weights().insert(WeightEntry(day = today(), timestamp = System.currentTimeMillis(), weightKg = profile.weightKg))
                            repo.settings.saveProfile(profile) // last: flips the app out of onboarding
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (key.isBlank()) "Start without AI" else "Let's go") }
                TextButton(onClick = { step = 0 }) { Text("Back") }
            }
        }
    }
}
