package io.homeassistant.companion.android.dashboard.assist

import io.homeassistant.companion.android.dashboard.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssistChatTest {
    private val chat = AssistChat.start("How can I assist?").send("Turn on the light")

    @Test
    fun `Given a sent message when the answer streams in then it grows until the spoken response replaces it`() {
        val streamed = chat
            .apply(json("""{"type": "intent-progress", "data": {"chat_log_delta": {"role": "assistant"}}}"""))
            .apply(json("""{"type": "intent-progress", "data": {"chat_log_delta": {"content": "Turning"}}}"""))
        assertEquals("Turning…", streamed.messages.last().text)
        assertEquals(true, streamed.processing)
        val ended = streamed.apply(
            json(
                """{"type": "intent-end", "data": {"intent_output": {"conversation_id": "c1",
                "response": {"response_type": "action_done", "speech": {"plain": {"speech": "Turned on the light"}}}}}}""",
            ),
        )
        assertEquals(ChatMessage(fromUser = false, "Turned on the light"), ended.messages.last())
        assertEquals("c1", ended.conversationId)
        assertEquals(false, ended.processing)
    }

    @Test
    fun `Given an answer without a spoken response when it ends then it keeps upstream's ellipsis`() {
        val ended = chat.apply(
            json("""{"type": "intent-end", "data": {"intent_output": {"response": {"response_type": "action_done"}}}}"""),
        )
        assertEquals(ChatMessage(fromUser = false, "…"), ended.messages.last())
        assertEquals(false, ended.processing)
    }

    @Test
    fun `Given a sent message when the run fails then the answer shows the error`() {
        val failed = chat.apply(json("""{"type": "error", "data": {"code": "x", "message": "No agent"}}"""))
        assertEquals(ChatMessage(fromUser = false, "No agent", error = true), failed.messages.last())
        assertEquals(listOf(true, false), failed.messages.drop(1).map { it.fromUser })
    }

    @Test
    fun `Given thinking and a tool call when the answer streams in then they fill its details, the result once it returns`() {
        val streamed = chat
            .apply(progress("""{"role": "assistant", "thinking_content": "Let me "}"""))
            .apply(progress("""{"thinking_content": "check."}"""))
            .apply(progress("""{"tool_calls": [{"id": "t1", "tool_name": "HassTurnOn", "tool_args": {"name": "light"}}]}"""))
            .apply(progress("""{"role": "tool_result", "tool_call_id": "t1", "tool_result": {"success": true}}"""))
            .apply(progress("""{"role": "assistant", "content": "Done"}"""))
        val answer = streamed.messages.last()
        assertEquals("Let me check.", answer.thinking)
        assertEquals("Done…", answer.text)
        val call = answer.toolCalls.single()
        assertEquals("HassTurnOn", call.name)
        assertEquals("{\n  \"name\": \"light\"\n}", call.argsText)
        assertEquals("{\n  \"success\": true\n}", call.resultText)
    }

    @Test
    fun `Given an answer with details when the run fails then the error follows it in a message of its own`() {
        val failed = chat
            .apply(progress("""{"role": "assistant", "thinking_content": "Hmm"}"""))
            .apply(json("""{"type": "error", "data": {"code": "x", "message": "Timeout"}}"""))
        assertEquals(listOf("", "Timeout"), failed.messages.takeLast(2).map { it.text })
        assertEquals(ChatMessage(fromUser = false, "Timeout", error = true), failed.messages.last())
    }

    @Test
    fun `Given content before any role when it streams in then it is ignored, as upstream's`() {
        assertEquals("…", chat.apply(progress("""{"content": "Hi"}""")).messages.last().text)
    }

    @Test
    fun `Given a conversation when the next message is sent then it continues it`() {
        assertEquals("c1", assistRunParams("p", "Hi", "c1")["conversation_id"])
        assertEquals(false, "conversation_id" in assistRunParams("p", "Hi", null))
    }

    private fun progress(delta: String) = json("""{"type": "intent-progress", "data": {"chat_log_delta": $delta}}""")
}
