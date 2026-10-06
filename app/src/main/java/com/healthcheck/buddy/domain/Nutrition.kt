package com.healthcheck.buddy.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class Sex { MALE, FEMALE }

@Serializable
enum class Goal(val label: String, val calorieDelta: Int) {
    LOSE_FAT("Lose fat", -450),
    RECOMP("Recomp (lean & strong)", -150),
    MAINTAIN("Maintain", 0),
    BUILD_MUSCLE("Build muscle", 300),
}

@Serializable
enum class ActivityLevel(val label: String, val multiplier: Double) {
    SEDENTARY("Mostly sitting", 1.2),
    LIGHT("Light (1–2 workouts/week)", 1.375),
    MODERATE("Moderate (3–4 workouts/week)", 1.55),
    HIGH("High (4–5 hard sessions/week)", 1.725),
    ATHLETE("Athlete (daily hard training)", 1.9),
}

@Serializable
data class Profile(
    val name: String = "",
    val buddyName: String = "Kai",
    val age: Int = 23,
    val sex: Sex = Sex.MALE,
    val heightCm: Int = 180,
    val weightKg: Double = 75.0,
    val goalWeightKg: Double? = null,
    val goal: Goal = Goal.RECOMP,
    val activity: ActivityLevel = ActivityLevel.HIGH,
    /** Free text like "high-level calisthenics, 4x/week". Fed to the AI as-is. */
    val training: String = "",
    val dietNotes: String = "",
) {
    fun describe(): String = buildString {
        append("${if (name.isBlank()) "User" else name}, $age y, ${sex.name.lowercase()}, ")
        append("$heightCm cm, ${weightKg} kg. ")
        append("Goal: ${goal.label}")
        goalWeightKg?.let { append(" (target $it kg)") }
        append(". Activity: ${activity.label}.")
        if (training.isNotBlank()) append(" Training: $training.")
        if (dietNotes.isNotBlank()) append(" Diet notes: $dietNotes.")
    }
}

data class Targets(val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

object Nutrition {
    /** Mifflin–St Jeor basal metabolic rate. */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun tdee(p: Profile): Int = (bmr(p) * p.activity.multiplier).roundToInt()

    /**
     * Local baseline targets so tracking works without an API key.
     * Protein 2.0 g/kg (strength athletes), fat 0.9 g/kg, carbs fill the rest.
     */
    fun targets(p: Profile): Targets {
        val kcal = (tdee(p) + p.goal.calorieDelta).coerceAtLeast(1200)
        val protein = (p.weightKg * 2.0).roundToInt()
        val fat = (p.weightKg * 0.9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(50)
        return Targets(kcal, protein, carbs, fat)
    }
}
