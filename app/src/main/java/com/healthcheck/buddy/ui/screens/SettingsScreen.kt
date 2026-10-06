package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.healthcheck.buddy.ai.ClaudeClient
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.SectionTitle
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(profile: Profile) {
    val repo = LocalContext.current.repo
    val scope = rememberCoroutineScope()
    val savedKey by repo.settings.apiKey.collectAsState(initial = null)
    var keyInput by remember { mutableStateOf("") }
    var draft by remember(profile) { mutableStateOf(profile) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Claude API key")
        Text(
            if (savedKey != null) "✅ Key saved (…${savedKey!!.takeLast(4)}). AI features are on, using ${ClaudeClient.MODEL}."
            else "No key yet. Create one at console.anthropic.com → API Keys, then paste it here. " +
                "It's encrypted on this phone and only sent to Anthropic.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it },
            label = { Text(if (savedKey != null) "Replace key" else "sk-ant-…") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = keyInput.isNotBlank(),
                onClick = { scope.launch { repo.settings.saveApiKey(keyInput); keyInput = ""; status = "Key saved." } },
            ) { Text("Save key") }
            if (savedKey != null) OutlinedButton(
                onClick = { scope.launch { repo.settings.saveApiKey(null); status = "Key removed." } },
            ) { Text("Remove") }
        }

        HorizontalDivider()
        SectionTitle("Profile")
        ProfileForm(draft) { draft = it }
        Button(
            onClick = { scope.launch { repo.settings.saveProfile(draft); status = "Profile saved." } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save profile") }

        HorizontalDivider()
        OutlinedButton(onClick = { scope.launch { repo.db.chat().clear(); status = "Chat cleared." } }) {
            Text("Clear chat history")
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
