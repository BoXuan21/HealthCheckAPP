package com.healthcheck.buddy.data

import android.content.Context
import androidx.room.Room
import com.healthcheck.buddy.ai.Buddy
import com.healthcheck.buddy.ai.BuddyContext
import com.healthcheck.buddy.ai.ClaudeClient
import com.healthcheck.buddy.domain.Nutrition
import com.healthcheck.buddy.domain.Profile
import kotlinx.coroutines.flow.first
import java.time.LocalDate

fun today(): String = LocalDate.now().toString()

/** App-wide dependencies, created once in [com.healthcheck.buddy.HealthCheckApp]. */
class Repository(context: Context) {
    val appContext: Context = context.applicationContext
    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "healthcheck.db").build()
    val settings = SettingsStore(appContext)

    private var cachedBuddy: Pair<String, Buddy>? = null

    /** The AI buddy, or null when no API key is set yet. */
    suspend fun buddy(): Buddy? {
        val key = settings.apiKey.first() ?: return null
        cachedBuddy?.let { (k, b) -> if (k == key) return b }
        return Buddy(ClaudeClient(key)).also { cachedBuddy = key to it }
    }

    suspend fun profile(): Profile = settings.profile.first() ?: Profile()

    suspend fun context(): BuddyContext {
        val profile = profile()
        val targets = Nutrition.targets(profile)
        val weekAgo = LocalDate.now().minusDays(6).toString()
        val meals = db.meals().since(weekAgo)
        val todayMeals = meals.filter { it.day == today() }

        val todaySummary = if (todayMeals.isEmpty()) "nothing logged yet" else
            "${todayMeals.sumOf { it.calories }} kcal, ${todayMeals.sumOf { it.proteinG }} g protein from " +
                todayMeals.joinToString { it.name }

        val byDay = meals.groupBy { it.day }.toSortedMap()
        val recent = if (byDay.isEmpty()) "no meals logged" else
            byDay.entries.joinToString("; ") { (day, list) -> "$day: ${list.sumOf { it.calories }} kcal" }

        val weights = db.weights().latest(10).reversed()
        val trend = if (weights.isEmpty()) "no weigh-ins yet" else
            weights.joinToString(", ") { "${it.day}: ${it.weightKg} kg" }

        return BuddyContext(profile, targets.calories, targets.proteinG, todaySummary, recent, trend)
    }
}
