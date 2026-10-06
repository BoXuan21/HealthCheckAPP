package com.healthcheck.buddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthcheck.buddy.domain.ActivityLevel
import com.healthcheck.buddy.domain.Goal
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.domain.Sex

/** Editable profile fields. Calls [onChange] with a new Profile whenever a valid value is entered. */
@Composable
fun ProfileForm(profile: Profile, onChange: (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = profile.name, onValueChange = { onChange(profile.copy(name = it)) },
                label = { Text("Your name") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = profile.buddyName, onValueChange = { onChange(profile.copy(buddyName = it)) },
                label = { Text("Buddy's name") }, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s ->
                FilterChip(
                    selected = profile.sex == s, onClick = { onChange(profile.copy(sex = s)) },
                    label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Age", profile.age.toString(), Modifier.weight(1f)) { v ->
                v.toIntOrNull()?.let { onChange(profile.copy(age = it)) }
            }
            NumberField("Height (cm)", profile.heightCm.toString(), Modifier.weight(1f)) { v ->
                v.toIntOrNull()?.let { onChange(profile.copy(heightCm = it)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Weight (kg)", profile.weightKg.toString(), Modifier.weight(1f)) { v ->
                v.toDoubleOrNull()?.let { onChange(profile.copy(weightKg = it)) }
            }
            NumberField("Goal weight (kg)", profile.goalWeightKg?.toString() ?: "", Modifier.weight(1f)) { v ->
                onChange(profile.copy(goalWeightKg = v.toDoubleOrNull()))
            }
        }
        EnumDropdown("Goal", profile.goal, Goal.entries, { it.label }) { onChange(profile.copy(goal = it)) }
        EnumDropdown("Activity", profile.activity, ActivityLevel.entries, { it.label }) {
            onChange(profile.copy(activity = it))
        }
        OutlinedTextField(
            value = profile.training, onValueChange = { onChange(profile.copy(training = it)) },
            label = { Text("Your training") },
            placeholder = { Text("e.g. high-level calisthenics, 4x per week") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = profile.dietNotes, onValueChange = { onChange(profile.copy(dietNotes = it)) },
            label = { Text("Diet notes (optional)") },
            placeholder = { Text("e.g. vegetarian, no lactose, I love rice") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Keeps its own text so partially-typed numbers like "75." aren't wiped by the parsed value. */
@Composable
private fun NumberField(label: String, initial: String, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(label: String, value: T, options: List<T>, text: (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text(value), onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { onPick(o); expanded = false })
            }
        }
    }
}
