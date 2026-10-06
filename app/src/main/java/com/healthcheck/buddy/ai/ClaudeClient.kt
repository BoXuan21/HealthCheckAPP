package com.healthcheck.buddy.ai

import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AiException(message: String) : Exception(message)

/**
 * Thin wrapper over the Anthropic Java SDK. The API key is the user's own, pasted in Settings.
 * Every call is blocking in the SDK, so we hop to Dispatchers.IO here.
 */
class ClaudeClient(apiKey: String) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /** Plain-text reply, e.g. buddy chat. */
    suspend fun text(
        system: String,
        messages: List<MessageParam>,
        maxTokens: Long = 4000,
        effort: OutputConfig.Effort = OutputConfig.Effort.LOW,
    ): String = call {
        base(system, maxTokens)
            .messages(messages)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .build()
    }

    /** Reply constrained to [schema] (JSON Schema as nested Maps/Lists). Returns the raw JSON string. */
    suspend fun json(
        system: String,
        content: List<ContentBlockParam>,
        schema: Map<String, Any>,
        maxTokens: Long = 16000,
        effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
    ): String = call {
        val format = JsonOutputFormat.builder()
            .schema(
                JsonOutputFormat.Schema.builder()
                    .additionalProperties(schema.mapValues { JsonValue.from(it.value) })
                    .build()
            )
            .build()
        base(system, maxTokens)
            .addUserMessageOfBlockParams(content)
            .outputConfig(OutputConfig.builder().effort(effort).format(format).build())
            .build()
    }

    private fun base(system: String, maxTokens: Long) = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(maxTokens)
        .system(system)
        // Server-side fallback: if a safety classifier declines, the API retries on a suitable model.
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    private suspend fun call(build: () -> MessageCreateParams): String = withContext(Dispatchers.IO) {
        val response: Message = try {
            client.messages().create(build())
        } catch (e: UnauthorizedException) {
            throw AiException("Your API key was rejected. Check it in Settings.")
        } catch (e: PermissionDeniedException) {
            throw AiException("This API key doesn't have access to the model.")
        } catch (e: RateLimitException) {
            throw AiException("Rate limited — give it a minute and try again.")
        } catch (e: AnthropicServiceException) {
            throw AiException("Claude API error (${e.statusCode()}). Try again shortly.")
        } catch (e: AnthropicIoException) {
            throw AiException("Couldn't reach Claude. Check your internet connection.")
        }

        when (response.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiException("Claude declined this request. Try rephrasing or another photo.")
            StopReason.MAX_TOKENS -> throw AiException("The answer was cut off. Try again.")
            else -> Unit
        }
        response.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }
            .joinToString("")
            .trim()
            .ifEmpty { throw AiException("Claude returned an empty answer.") }
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        fun jpeg(file: File): ContentBlockParam = ContentBlockParam.ofImage(
            ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                        .build()
                )
                .build()
        )

        fun text(s: String): ContentBlockParam = ContentBlockParam.ofText(s)

        fun userMessage(s: String): MessageParam =
            MessageParam.builder().role(MessageParam.Role.USER).content(s).build()

        fun assistantMessage(s: String): MessageParam =
            MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(s).build()
    }
}
