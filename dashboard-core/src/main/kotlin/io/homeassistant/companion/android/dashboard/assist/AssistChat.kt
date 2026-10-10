package io.homeassistant.companion.android.dashboard.assist

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

// Port of `ha-assist-chat`'s text conversation (frontend@20260624.6 src/components/ha-assist-chat.ts): the messages,
// and how a pipeline run's intent events fill in the answer.

/** A chat message: the user's or the agent's [text] ("…" while it comes), or an [error]. */
data class ChatMessage(val fromUser: Boolean, val text: String, val error: Boolean = false)

/**
 * A conversation with an agent through an Assist pipeline: the [messages] (opening with "How can I assist?"), the
 * [conversationId] the next message continues, and whether an answer is under way ([processing]).
 */
data class AssistChat(val messages: List<ChatMessage>, val conversationId: String?, val processing: Boolean) {
    /** The chat with the user's [text] and the agent's answer to come. */
    fun send(text: String): AssistChat = copy(
        messages = messages + ChatMessage(fromUser = true, text) + ChatMessage(fromUser = false, ELLIPSIS),
        processing = true,
    )

    /**
     * The chat after a pipeline run [event]: the answer streamed in (`intent-progress` assistant deltas), then the
     * spoken response at `intent-end` (an error response shown as one), or the run's error.
     */
    fun apply(event: JsonObject): AssistChat {
        val data = event.obj("data")
        return when (event.string("type")) {
            "intent-progress" -> data?.obj("chat_log_delta")?.let(::applyDelta) ?: this
            "intent-end" -> {
                val output = data?.obj("intent_output")
                val response = output?.obj("response")
                val speech = response?.obj("speech")?.obj("plain")?.string("speech")
                val ended =
                    copy(conversationId = output?.string("conversation_id") ?: conversationId, processing = false)
                when {
                    speech.isNullOrEmpty() -> ended.withAnswer { it.copy(text = it.text.removeSuffix(ELLIPSIS)) }
                    response.string("response_type") == "error" -> ended.withAnswer {
                        ChatMessage(false, speech, error = true)
                    }
                    else -> ended.withAnswer { ChatMessage(false, speech) }
                }
            }
            "error" -> failed(data?.string("message").orEmpty())
            else -> this
        }
    }

    /** The chat with the answer under way failing with [message]. */
    fun failed(message: String): AssistChat =
        copy(processing = false).withAnswer { ChatMessage(false, message, error = true) }

    // UPSTREAM-DIFF: only the answer's text streams in; upstream also shows the agent's thinking and tool calls
    // under "Show details"
    private fun applyDelta(delta: JsonObject): AssistChat {
        val content = delta.string("content")?.takeIf { delta.string("role") in setOf(null, "assistant") }
        return content?.let {
            withAnswer { answer ->
                answer.copy(
                    text =
                    answer.text.removeSuffix(ELLIPSIS) + it + ELLIPSIS,
                )
            }
        }
            ?: this
    }

    private fun withAnswer(change: (ChatMessage) -> ChatMessage): AssistChat {
        val last = messages.lastOrNull()?.takeIf { !it.fromUser } ?: return this
        return copy(messages = messages.dropLast(1) + change(last))
    }

    companion object {
        private const val ELLIPSIS = "…"

        /** A new chat, greeting with [greeting]. */
        fun start(greeting: String) = AssistChat(listOf(ChatMessage(fromUser = false, greeting)), null, false)
    }
}

/**
 * Whether the [pipeline]'s agent can control Home Assistant, else the chat warns it can't: it prefers local
 * intents, or its conversation entity supports control (or isn't known).
 */
fun HassSnapshot.assistControlsHome(pipeline: JsonObject): Boolean {
    val engine = pipeline.string("conversation_engine")?.let(states::get)
    return pipeline.boolean("prefer_local_intents") == true ||
        engine == null ||
        engine.supportsFeature(CONVERSATION_CONTROL)
}

/** The command running [pipelineId] on [text] (`assist_pipeline/run`, from intent to intent). */
fun assistRunParams(pipelineId: String, text: String, conversationId: String?): Map<String, Any?> = buildMap {
    put("start_stage", "intent")
    put("end_stage", "intent")
    put("input", mapOf("text" to text))
    put("pipeline", pipelineId)
    conversationId?.let { put("conversation_id", it) }
}

private const val CONVERSATION_CONTROL = 1
