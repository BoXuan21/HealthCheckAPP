package com.healthcheck.buddy.ai

import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.healthcheck.buddy.data.ChatMessage
import com.healthcheck.buddy.domain.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FoodItem(val name: String, val portion: String, val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

@Serializable
data class FoodAnalysis(
    val mealName: String,
    val items: List<FoodItem>,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** low | medium | high */
    val confidence: String,
    val buddyNote: String,
)

@Serializable
data class ProgressReview(
    /** on_track | slow | off_track | too_early */
    val verdict: String,
    val summary: String,
    val observations: List<String>,
    val nextSteps: List<String>,
    val buddyNote: String,
)

@Serializable
data class PlanMeal(val name: String, val time: String, val description: String, val calories: Int, val proteinG: Int)

@Serializable
data class MealPlan(
    val trainingDayCalories: Int,
    val restDayCalories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val rationale: String,
    val trainingDayMeals: List<PlanMeal>,
    val tips: List<String>,
)

/** Everything the buddy knows about the user right now, rendered into prompts. */
data class BuddyContext(
    val profile: Profile,
    val targetCalories: Int,
    val targetProteinG: Int,
    val todaySummary: String,
    val recentDaysSummary: String,
    val weightTrend: String,
) {
    fun render(): String = """
        |About the user: ${profile.describe()}
        |Daily target: $targetCalories kcal, ${targetProteinG} g protein.
        |Today so far: $todaySummary
        |Last 7 days: $recentDaysSummary
        |Weight trend: $weightTrend
    """.trimMargin()
}

/** All AI features. Each one only needs the user's API key. */
class Buddy(private val claude: ClaudeClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun persona(p: Profile) = """
        You are ${p.buddyName}, a friendly health buddy inside the HealthCheck app — not a clinical tracker.
        Talk like a supportive friend who knows sports nutrition and strength training well: warm, direct,
        a bit of humor, never preachy or guilt-tripping. Celebrate effort, be honest when something is off,
        and always give one concrete next step. Keep it short — this is a phone screen.
        Use metric units. You are not a doctor: if the user mentions symptoms, disordered eating, or
        very aggressive targets, gently suggest a professional.
    """.trimIndent()

    suspend fun analyzeFood(photo: File, note: String, ctx: BuddyContext): FoodAnalysis {
        val system = persona(ctx.profile) + """

            Task: estimate the nutrition of the meal in the photo. Identify each component, estimate the portion
            from visual cues (plate size, utensils, hands), and give calories and macros per item and in total.
            Be realistic about hidden calories (oil, sauces, dressings). Totals must equal the sum of items.
            buddyNote: one or two friendly sentences relating this meal to the user's day and goal.

            ${ctx.render()}
        """.trimIndent()
        val prompt = if (note.isBlank()) "What's in this meal?" else "What's in this meal? Extra info from me: $note"
        val raw = claude.json(system, listOf(ClaudeClient.jpeg(photo), ClaudeClient.text(prompt)), Schemas.food)
        return json.decodeFromString(raw)
    }

    suspend fun reviewProgress(first: File?, latest: File, ctx: BuddyContext): ProgressReview {
        val system = persona(ctx.profile) + """

            Task: judge whether the user's diet and training are actually producing progress toward their goal.
            Combine the weight trend, recent intake, and the progress photos. When two photos are given, the
            first is the starting photo and the second is the latest. Comment on visible changes in body
            composition only when they are genuinely visible; lighting, pump and posture differ between photos,
            so say so rather than over-reading them. Never comment on attractiveness — only progress.
            verdict: on_track, slow, off_track, or too_early (under ~2 weeks of data).

            ${ctx.render()}
        """.trimIndent()
        val content = buildList {
            if (first != null && first != latest) {
                add(ClaudeClient.text("Starting photo:"))
                add(ClaudeClient.jpeg(first))
            }
            add(ClaudeClient.text("Latest photo:"))
            add(ClaudeClient.jpeg(latest))
            add(ClaudeClient.text("Am I actually making progress?"))
        }
        return json.decodeFromString(claude.json(system, content, Schemas.progress, effort = OutputConfig.Effort.HIGH))
    }

    suspend fun generatePlan(ctx: BuddyContext): MealPlan {
        val system = persona(ctx.profile) + """

            Task: build the optimal daily calorie and macro plan for this user's goal and training, plus a sample
            training-day meal plan they can actually cook. Base it on current sports-nutrition evidence
            (e.g. 1.6–2.2 g/kg protein for strength athletes, carbs around training). Use the app's baseline
            target as a starting point but adjust it if the user's training warrants it, and explain why in
            the rationale (2–4 sentences). Meals across the day must add up to the training-day calories.

            ${ctx.render()}
        """.trimIndent()
        val raw = claude.json(
            system,
            listOf(ClaudeClient.text("Give me my optimal plan.")),
            Schemas.plan,
            effort = OutputConfig.Effort.HIGH,
        )
        return json.decodeFromString(raw)
    }

    suspend fun chat(history: List<ChatMessage>, ctx: BuddyContext): String {
        val system = persona(ctx.profile) + """

            You are chatting with the user. Reply in a few short sentences unless they ask for detail.
            Use what you know about their day below — reference it naturally, like a friend who was paying attention.

            ${ctx.render()}
        """.trimIndent()
        return claude.text(system, toMessages(history))
    }

    suspend fun dailyCheckIn(ctx: BuddyContext): String {
        val system = persona(ctx.profile) + "\n\n" + ctx.render()
        val ask = "Write today's check-in for the home screen: max 2 sentences, personal, based on where I am today and this week."
        return claude.text(system, listOf(ClaudeClient.userMessage(ask)), maxTokens = 1000)
    }

    /** The API needs alternating turns starting with the user; merge and trim our stored log to fit. */
    private fun toMessages(history: List<ChatMessage>): List<MessageParam> {
        val merged = mutableListOf<Pair<Boolean, String>>()
        for (m in history.dropWhile { !it.fromUser }) {
            if (merged.isNotEmpty() && merged.last().first == m.fromUser) {
                merged[merged.lastIndex] = m.fromUser to merged.last().second + "\n\n" + m.text
            } else {
                merged += m.fromUser to m.text
            }
        }
        return merged.map { (user, text) ->
            if (user) ClaudeClient.userMessage(text) else ClaudeClient.assistantMessage(text)
        }
    }
}

/** JSON Schemas for structured outputs. Every object lists all properties as required, no extras. */
private object Schemas {
    private fun obj(vararg props: Pair<String, Map<String, Any>>) = mapOf(
        "type" to "object",
        "properties" to props.toMap(),
        "required" to props.map { it.first },
        "additionalProperties" to false,
    )

    private val int = mapOf("type" to "integer")
    private val str = mapOf("type" to "string")
    private fun arr(items: Map<String, Any>) = mapOf("type" to "array", "items" to items)
    private fun enum(vararg values: String) = mapOf("type" to "string", "enum" to values.toList())

    val food = obj(
        "mealName" to str,
        "items" to arr(obj("name" to str, "portion" to str, "calories" to int, "proteinG" to int, "carbsG" to int, "fatG" to int)),
        "calories" to int, "proteinG" to int, "carbsG" to int, "fatG" to int,
        "confidence" to enum("low", "medium", "high"),
        "buddyNote" to str,
    )

    val progress = obj(
        "verdict" to enum("on_track", "slow", "off_track", "too_early"),
        "summary" to str,
        "observations" to arr(str),
        "nextSteps" to arr(str),
        "buddyNote" to str,
    )

    val plan = obj(
        "trainingDayCalories" to int, "restDayCalories" to int,
        "proteinG" to int, "carbsG" to int, "fatG" to int,
        "rationale" to str,
        "trainingDayMeals" to arr(obj("name" to str, "time" to str, "description" to str, "calories" to int, "proteinG" to int)),
        "tips" to arr(str),
    )
}
