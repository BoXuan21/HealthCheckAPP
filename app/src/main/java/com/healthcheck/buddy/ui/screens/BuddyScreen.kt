package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.healthcheck.buddy.ai.AiException
import com.healthcheck.buddy.data.ChatMessage
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.components.NeedsKeyCard
import com.healthcheck.buddy.ui.components.repo
import kotlinx.coroutines.launch

private val starters = listOf(
    "What should I eat after training?",
    "How am I doing this week?",
    "I'm craving something sweet 😅",
    "Give me a high-protein dinner idea",
)

@Composable
fun BuddyScreen(profile: Profile, onOpenSettings: () -> Unit) {
    val repo = LocalContext.current.repo
    val scope = rememberCoroutineScope()
    val messages by remember { repo.db.chat().all() }.collectAsState(initial = emptyList())
    val apiKey by repo.settings.apiKey.collectAsState(initial = null)
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send(text: String) {
        if (text.isBlank() || thinking) return
        input = ""
        thinking = true
        scope.launch {
            val chat = repo.db.chat()
            chat.insert(ChatMessage(timestamp = System.currentTimeMillis(), fromUser = true, text = text.trim()))
            val reply = try {
                val buddy = repo.buddy() ?: throw AiException("Add your API key in Settings so I can answer.")
                buddy.chat(chat.recent(30), repo.context())
            } catch (e: AiException) {
                "Hmm, I couldn't answer just now: ${e.message}"
            }
            chat.insert(ChatMessage(timestamp = System.currentTimeMillis(), fromUser = false, text = reply))
            thinking = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (apiKey == null) {
            NeedsKeyCard("chatting with ${profile.buddyName}", onOpenSettings, Modifier.padding(16.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) item {
                Bubble(
                    fromUser = false,
                    text = "Hey${if (profile.name.isNotBlank()) " ${profile.name}" else ""}! I'm ${profile.buddyName}. " +
                        "Ask me anything about food, training, or how your week is going.",
                )
            }
            items(messages, key = { it.id }) { Bubble(it.fromUser, it.text) }
        }
        if (thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (messages.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                starters.take(2).forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }) }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Message ${profile.buddyName}") },
                modifier = Modifier.weight(1f), maxLines = 4,
            )
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !thinking) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun Bubble(fromUser: Boolean, text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
            color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
