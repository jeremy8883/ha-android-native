package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.assist.AssistChat
import io.homeassistant.companion.android.dashboard.assist.ChatMessage
import io.homeassistant.companion.android.dashboard.assist.ToolCall
import io.homeassistant.companion.android.dashboard.assist.assistControlsHome
import io.homeassistant.companion.android.dashboard.assist.assistRunParams
import io.homeassistant.companion.android.dashboard.data.AssistRepository
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.HaAlert
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.MarkdownText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * Port of `more-info-conversation`: a text chat with the agent through its pipeline (`ha-assist-chat` without
 * speech), a warning when the agent can't control Home Assistant, or why the pipeline couldn't be loaded.
 *
 * Upstream: `more-info-conversation`, `ha-assist-chat` (src/dialogs/more-info/controls/more-info-conversation.ts;
 * src/components/ha-assist-chat.ts)
 */
@Composable
internal fun MoreInfoConversation(agentId: String, hass: HassSnapshot, modifier: Modifier = Modifier) {
    val chat = hiltViewModel<ConversationChatViewModel>(key = "chat-$agentId")
    val greeting = hass.localize("ui.dialogs.voice_command.how_can_i_help")
    LaunchedEffect(agentId) { chat.show(agentId, greeting, hass.localize("ui.dialogs.voice_command.error")) }
    val state by chat.state.collectAsStateWithLifecycle()
    val colors = LocalHAColorScheme.current
    when (val shown = state) {
        ConversationState.Loading -> CircularProgressIndicator(Modifier.size(HASize.X3L))
        is ConversationState.Failed -> Text(
            hass.localize(
                "ui.dialogs.voice_command.${if (shown.notFound) "not_found" else "unknown"}_error_load_assist",
            ),
            style = HATextStyle.Body,
            color = colors.colorOnDangerNormal,
        )
        is ConversationState.Ready -> Column(
            modifier.imePadding(),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            if (!hass.assistControlsHome(shown.pipeline)) {
                HaAlert(hass.localize("ui.dialogs.voice_command.conversation_no_control"))
            }
            // Newest at the bottom, the list kept scrolled to it; messages with nothing to show are left out
            val messages = shown.chat.messages.filter { it.text.isNotEmpty() || it.error || it.hasDetails }.asReversed()
            val showDetails = hass.localize("ui.dialogs.voice_command.show_details")
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.Bottom),
            ) {
                items(messages.size, key = { messages.size - 1 - it }) { index ->
                    MessageBubble(messages[index], showDetails)
                }
            }
            ChatInput(hass, shown.chat.processing, chat::send)
        }
    }
}

/**
 * A message: the user's on the right, the agent's on the left, an error in red; an answer's thinking and tool calls
 * under a collapsed "Show details" ([showDetails]) above its text.
 *
 * Upstream: part of `ha-assist-chat` (src/components/ha-assist-chat.ts)
 */
@Composable
private fun MessageBubble(message: ChatMessage, showDetails: String) {
    val colors = LocalHAColorScheme.current
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val background = when {
            message.error -> colors.colorFillDangerLoudResting
            message.fromUser -> colors.colorFillPrimaryLoudResting
            else -> colors.colorFillDisabledLoudResting.copy(alpha = HASS_BUBBLE_ALPHA)
        }
        val shape = RoundedCornerShape(
            topStart = HARadius.XL,
            topEnd = HARadius.XL,
            bottomStart = if (message.fromUser) HARadius.XL else 0.dp,
            bottomEnd = if (message.fromUser) 0.dp else HARadius.XL,
        )
        Column(Modifier.widthIn(max = BUBBLE_MAX).background(background, shape).padding(HADimens.SPACE2)) {
            if (message.hasDetails) MessageDetails(message, showDetails)
            if (message.text.isNotEmpty()) MarkdownText(message.text)
        }
    }
}

/**
 * The answer's details: the header expanding them, then the agent's thinking and each tool call with its arguments
 * and result.
 *
 * Upstream: part of `ha-assist-chat` (src/components/ha-assist-chat.ts)
 */
@Composable
private fun MessageDetails(message: ChatMessage, label: String) {
    val colors = LocalHAColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = if (message.text.isEmpty()) 0.dp else HADimens.SPACE2)) {
        Row(
            Modifier
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = HADimens.SPACE1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            DashboardIcon(DETAILS_ICON, colors.colorTextSecondary, Modifier.size(HASize.M))
            Text(label, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
            DashboardIcon(
                if (expanded) "mdi:chevron-up" else "mdi:chevron-down",
                colors.colorTextSecondary,
                Modifier.size(HASize.M),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(top = HADimens.SPACE2).heightIn(max = DETAILS_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                if (message.thinking.isNotEmpty()) MarkdownText(message.thinking)
                message.toolCalls.forEach { ToolCallView(it) }
            }
        }
    }
}

/**
 * A tool call: its name in bold beside a divider line, then its arguments and its result as JSON.
 *
 * Upstream: part of `ha-assist-chat` (src/components/ha-assist-chat.ts)
 */
@Composable
private fun ToolCallView(call: ToolCall) {
    val colors = LocalHAColorScheme.current
    val divider = colors.colorBorderNeutralQuiet
    Column(
        Modifier
            .drawBehind { drawRect(divider, size = size.copy(width = TOOL_BORDER.toPx())) }
            .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
    ) {
        Text(call.name, style = HATextStyle.BodyMedium, fontWeight = FontWeight.Bold, color = colors.colorTextSecondary)
        listOfNotNull(call.argsText, call.resultText).forEach { json ->
            Text(
                json,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = TOOL_DATA_SIZE),
                color = colors.colorTextSecondary,
                modifier = Modifier
                    .padding(top = HADimens.SPACE1)
                    .fillMaxWidth()
                    .background(colors.colorSurfaceDefault, RoundedCornerShape(HARadius.S))
                    .padding(HADimens.SPACE1),
            )
        }
    }
}

/**
 * The message field ("Enter a request") with its send button, sending on enter too.
 *
 * Upstream: part of `ha-assist-chat` (src/components/ha-assist-chat.ts)
 */
@Composable
private fun ChatInput(hass: HassSnapshot, processing: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = {
        text.trim().takeIf { it.isNotEmpty() && !processing }?.let {
            onSend(it)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HATextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(hass.localize("ui.dialogs.voice_command.input_label")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        val label = hass.localize("ui.dialogs.voice_command.send_text")
        IconButton(
            onClick = { send() },
            enabled = text.isNotBlank() && !processing,
            modifier = Modifier.semantics {
                contentDescription =
                    label
            },
        ) {
            DashboardIcon(SEND_ICON, LocalHAColorScheme.current.colorOnPrimaryNormal, Modifier.size(HASize.X2L))
        }
    }
}

/** The chat with a conversation agent, or why it can't start. */
internal sealed interface ConversationState {
    data object Loading : ConversationState

    /** The pipeline couldn't be loaded; [notFound] when the agent has none. */
    data class Failed(val notFound: Boolean) : ConversationState

    data class Ready(val pipeline: JsonObject, val chat: AssistChat) : ConversationState
}

/** The chat with an agent: its pipeline (the agent's entity ID), and the messages sent through it. */
@HiltViewModel
internal class ConversationChatViewModel @Inject constructor(private val repository: AssistRepository) : ViewModel() {
    private val _state = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    private var agentId: String? = null
    private var errorText = ""

    /** Load [agent]'s pipeline and greet with [greeting]; [error] is shown when a message can't be processed. */
    fun show(agent: String, greeting: String, error: String) {
        if (agentId == agent) return
        agentId = agent
        errorText = error
        viewModelScope.launch {
            _state.value = when (val pipeline = repository.pipeline(agent)) {
                is Fetched.Success -> ConversationState.Ready(pipeline.value, AssistChat.start(greeting))
                is Fetched.Failure -> {
                    Timber.w("Couldn't load the pipeline of $agent: ${pipeline.error}")
                    ConversationState.Failed((pipeline.error as? LoadError.Server)?.code == "not_found")
                }
            }
        }
    }

    /** Send [text] and stream the answer in. */
    fun send(text: String) {
        val ready = _state.value as? ConversationState.Ready ?: return
        val pipelineId = (ready.pipeline["id"] as? JsonPrimitive)?.content ?: return
        _state.value = ready.copy(chat = ready.chat.send(text))
        viewModelScope.launch {
            repository.run(assistRunParams(pipelineId, text, ready.chat.conversationId))
                .takeWhile { event ->
                    updateChat { it.apply(event) }
                    val type = (event["type"] as? JsonPrimitive)?.content
                    type != "intent-end" && type != "error"
                }
                .catch { e ->
                    Timber.w(e, "The pipeline run failed")
                    updateChat { it.failed(errorText) }
                }
                .collect {}
        }
    }

    private fun updateChat(change: (AssistChat) -> AssistChat) = _state.update { current ->
        (current as? ConversationState.Ready)?.let { it.copy(chat = change(it.chat)) } ?: current
    }
}

private const val SEND_ICON = "mdi:send"
private const val DETAILS_ICON = "mdi:comment-processing-outline"
private const val HASS_BUBBLE_ALPHA = 0.2f
private val BUBBLE_MAX = 280.dp
private val DETAILS_MAX_HEIGHT = 500.dp
private val TOOL_BORDER = 2.dp

/** `.tool-data`'s 0.9em of the details' 14px. */
private val TOOL_DATA_SIZE = 12.6.sp
