package io.homeassistant.companion.android.dashboard.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.todo.TodoFeatures
import io.homeassistant.companion.android.dashboard.todo.todoLists
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.todo.TodoListCard
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of `ha-panel-todo` on a phone: the list picker (each list with its icon), the list's menu (its details), the
 * list as its to-do list card, and "Add item" for lists that can add.
 *
 * Upstream: `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
internal fun TodoPanel(hass: State<HassSnapshot?>, now: State<ZonedDateTime?>, interactions: CardInteractions) {
    val snapshot = hass.value ?: return
    val lists by remember { derivedStateOf { hass.value?.todoLists().orEmpty() } }
    // UPSTREAM-DIFF: the chosen list is remembered while the app runs; upstream keeps it in the browser's storage
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val entityId = chosen?.takeIf { id -> lists.any { it.entityId == id } } ?: lists.firstOrNull()?.entityId
    var addRequests by remember { mutableIntStateOf(0) }
    val manage = rememberTodoListManagement(snapshot, lists.map { it.entityId }, onChoose = { chosen = it })
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HADimens.SPACE4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListPicker(
                    lists.map {
                        it.entityId to it.name
                    },
                    entityId,
                    snapshot,
                    Modifier.weight(1f),
                    onCreate = manage.onCreate.takeIf { snapshot.user?.isAdmin == true },
                ) { chosen = it }
                entityId?.let { ListMenu(it, snapshot, interactions.onAction, manage.deleter(it)) }
            }
            entityId?.let { id ->
                val card = remember(id) {
                    CardConfig(
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("todo-list"),
                                "entity" to JsonPrimitive(id),
                            ),
                        ),
                    )
                }
                TodoListCard(card, hass, now, interactions.onAction, Modifier.fillMaxWidth(), addRequests)
            }
        }
        val canCreate = entityId?.let { TodoFeatures(snapshot.states[it]).create } == true
        if (canCreate) {
            HAAccentButton(
                snapshot.localize("ui.panel.todo.add_item"),
                { addRequests++ },
                modifier = Modifier.align(Alignment.BottomEnd).padding(HADimens.SPACE4),
                size = ButtonSize.LARGE,
                prefix = {
                    DashboardIcon("mdi:plus", LocalHAColorScheme.current.colorOnPrimaryLoud, Modifier.size(HASize.X2L))
                },
            )
        }
    }
}

/**
 * The list picker: the chosen list's name with a chevron, opening the lists.
 *
 * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
private fun ListPicker(
    lists: List<Pair<String, String>>,
    chosen: String?,
    hass: HassSnapshot,
    modifier: Modifier,
    onCreate: (() -> Unit)?,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    Box(modifier) {
        // As the panel's title on a phone: a filled pill with a chevron
        HAAccentButton(
            lists.firstOrNull { it.first == chosen }?.second ?: hass.localize("panel.todo"),
            { open = true },
            suffix = { DashboardIcon("mdi:chevron-down", colors.colorOnPrimaryLoud, Modifier.size(HASize.X2L)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lists.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, style = HATextStyle.Body, color = colors.colorTextPrimary) },
                    leadingIcon = {
                        DashboardIcon(hass.entityIcon(id), colors.colorTextSecondary, Modifier.size(HASize.X2L))
                    },
                    onClick = {
                        open = false
                        onChoose(id)
                    },
                )
            }
            // For admins, after the lists
            onCreate?.let { create ->
                if (lists.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            hass.localize("ui.panel.todo.create_list"),
                            style = HATextStyle.Body,
                            color = colors.colorTextPrimary,
                        )
                    },
                    leadingIcon = { DashboardIcon("mdi:plus", colors.colorTextSecondary, Modifier.size(HASize.X2L)) },
                    onClick = {
                        open = false
                        create()
                    },
                )
            }
        }
    }
}

/**
 * The list's menu: its details (`more-info`, only when Assist can use the list: the `conversation` integration is
 * loaded), and "Delete list" for a local list ([onDelete]); no button when it has neither.
 *
 * UPSTREAM-DIFF: no "Assist" (the app has no Assist).
 *
 * Upstream: part of `ha-panel-todo` (src/panels/todo/ha-panel-todo.ts)
 */
@Composable
private fun ListMenu(entityId: String, hass: HassSnapshot, onAction: (CardAction) -> Unit, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    val menu = hass.localize("ui.common.menu")
    val information = CONVERSATION in hass.config.components
    if (!information && onDelete == null) return
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = menu }) {
            DashboardIcon("mdi:dots-vertical", colors.colorTextPrimary, Modifier.size(HASize.X2L))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (information) {
                DropdownMenuItem(
                    text = { Text(hass.localize("ui.panel.todo.information"), color = colors.colorTextPrimary) },
                    leadingIcon = {
                        DashboardIcon("mdi:information-outline", colors.colorTextSecondary, Modifier.size(HASize.X2L))
                    },
                    onClick = {
                        open = false
                        onAction(CardAction.MoreInfo(entityId))
                    },
                )
            }
            onDelete?.let { delete ->
                if (information) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(hass.localize("ui.panel.todo.delete_list"), color = colors.colorOnDangerNormal) },
                    leadingIcon = {
                        DashboardIcon("mdi:delete", colors.colorOnDangerNormal, Modifier.size(HASize.X2L))
                    },
                    onClick = {
                        open = false
                        delete()
                    },
                )
            }
        }
    }
}

/** The integration whose loading shows the list's "Information" (`_conversation`). */
private const val CONVERSATION = "conversation"
