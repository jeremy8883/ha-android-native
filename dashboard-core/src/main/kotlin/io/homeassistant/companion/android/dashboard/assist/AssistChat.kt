package io.homeassistant.companion.android.dashboard.assist

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.jsonStringify
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Port of `ha-assist-chat`'s text conversation (frontend@20260624.6 src/components/ha-assist-chat.ts): the messages,
// and how a pipeline run's intent events fill in the answer, its thinking and tool calls.

/**
 * A chat message: the user's or the agent's [text] ("…" while it comes), or an [error]; an answer may carry the
 * agent's [thinking] and the [toolCalls] it made, shown under "Show details".
 */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val error: Boolean = false,
    val thinking: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
) {
    /** Whether the message has details to show: thinking or tool calls. */
    val hasDetails: Boolean get() = thinking.isNotEmpty() || toolCalls.isNotEmpty()
}

/**
 * A tool the agent called: its [name], arguments and, once it returned, its result; as `ha-assist-chat` shows them,
 * the arguments ([argsText]) and a truthy result ([resultText]) as indented JSON.
 */
data class ToolCall(val id: String, val name: String, val args: JsonElement?, val result: JsonElement? = null) {
    /** The arguments as `JSON.stringify(args, null, 2)`, `null` without any. */
    val argsText: String? get() = args?.let(::jsonStringify)

    /** The result as `JSON.stringify(result, null, 2)`, `null` while there is none or it is falsy. */
    val resultText: String? get() = result?.takeIf(::jsTruthy)?.let(::jsonStringify)
}

/**
 * A conversation with an agent through an Assist pipeline: the [messages] (opening with "How can I assist?"), the
 * [conversationId] the next message continues, and whether an answer is under way ([processing]). The run's deltas
 * belong to the role the last one named ([deltaRole]).
 */
data class AssistChat(
    val messages: List<ChatMessage>,
    val conversationId: String?,
    val processing: Boolean,
    private val deltaRole: String = "",
) {
    /** The chat with the user's [text] and the agent's answer to come. */
    fun send(text: String): AssistChat = copy(
        messages = messages + ChatMessage(fromUser = true, text) + ChatMessage(fromUser = false, ELLIPSIS),
        processing = true,
        deltaRole = "",
    )

    /**
     * The chat after a pipeline run [event]: the answer streamed in (`intent-progress` deltas: the assistant's text,
     * thinking and tool calls, then the tools' results), then the spoken response at `intent-end` (an error response
     * shown as one), or the run's error.
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
                    // Without a spoken response the answer stays as it streamed, "…" and all
                    speech.isNullOrEmpty() -> ended
                    response.string("response_type") == "error" -> ended.withError(speech)
                    else -> ended.withAnswer { it.copy(text = speech) }
                }
            }
            "error" -> failed(data?.string("message").orEmpty())
            else -> this
        }
    }

    /** The chat with the answer under way failing with [message]. */
    fun failed(message: String): AssistChat = copy(processing = false).withError(message)

    /** Port of `setError`: the error in the answer still empty, else in a new message after the answer so far. */
    private fun withError(message: String): AssistChat {
        val last = messages.lastOrNull()?.takeIf { !it.fromUser } ?: return this
        val untouched = last.text == ELLIPSIS && !last.hasDetails
        val error = ChatMessage(false, message, error = true)
        return if (untouched) {
            copy(messages = messages.dropLast(1) + last.copy(text = message, error = true))
        } else {
            copy(messages = messages.dropLast(1) + last.copy(text = last.text.removeSuffix(ELLIPSIS)) + error)
        }
    }

    /** Port of `processEvent`'s deltas: those of the assistant fill the answer, those of a tool give its result. */
    private fun applyDelta(delta: JsonObject): AssistChat {
        val role = delta.string("role") ?: deltaRole
        val next = copy(deltaRole = role)
        return when (role) {
            ASSISTANT -> next.withAnswer { answer -> answer.withAssistantDelta(delta) }
            TOOL_RESULT -> {
                val id = delta.string("tool_call_id")
                next.withAnswer { answer ->
                    answer.copy(
                        toolCalls = answer.toolCalls.map {
                            if (it.id ==
                                id
                            ) {
                                it.copy(result = delta["tool_result"])
                            } else {
                                it
                            }
                        },
                    )
                }
            }
            else -> next
        }
    }

    private fun withAnswer(change: (ChatMessage) -> ChatMessage): AssistChat {
        val last = messages.lastOrNull()?.takeIf { !it.fromUser } ?: return this
        return copy(messages = messages.dropLast(1) + change(last))
    }

    companion object {
        private const val ELLIPSIS = "…"
        private const val ASSISTANT = "assistant"
        private const val TOOL_RESULT = "tool_result"

        /** A new chat, greeting with [greeting]. */
        fun start(greeting: String) = AssistChat(listOf(ChatMessage(fromUser = false, greeting)), null, false)

        /** The answer with an assistant [delta]: its text before the ellipsis, its thinking and tool calls added. */
        private fun ChatMessage.withAssistantDelta(delta: JsonObject): ChatMessage {
            val content = delta.string("content")?.takeIf { it.isNotEmpty() }
            val text = when {
                content == null -> text
                text.endsWith(ELLIPSIS) -> text.removeSuffix(ELLIPSIS) + content + ELLIPSIS
                else -> text + content + ELLIPSIS
            }
            val calls = (delta["tool_calls"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toToolCall() }
            return copy(
                text = text,
                thinking = thinking + delta.string("thinking_content").orEmpty(),
                toolCalls = calls.fold(toolCalls) { all, call ->
                    if (all.any { it.id == call.id }) all.map { if (it.id == call.id) call else it } else all + call
                },
            )
        }

        private fun JsonObject.toToolCall(): ToolCall? {
            val id = string("id") ?: return null
            return ToolCall(id, string("tool_name").orEmpty(), this["tool_args"])
        }
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
