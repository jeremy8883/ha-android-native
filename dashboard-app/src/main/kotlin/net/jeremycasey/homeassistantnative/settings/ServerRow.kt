package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A server with its user's avatar, name and user, like the companion app's server chooser (`ServerChooserRow`),
 * plus the address, with [trailing] content such as a log out button.
 */
@Composable
internal fun ServerRow(
    server: ServerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HADimens.SPACE18)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
    ) {
        ServerAvatar(server)
        Spacer(modifier = Modifier.width(HADimens.SPACE3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = HATextStyle.Body.copy(
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            server.userName?.takeIf { it != server.name }?.let {
                Text(
                    text = it,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The address tells apart servers with the same name (the server's own name, often "Home")
            if (server.address.isNotBlank()) {
                Text(
                    text = server.address,
                    style = HATextStyle.BodyMedium.copy(
                        color = LocalHAColorScheme.current.colorTextSecondary,
                        textAlign = TextAlign.Start,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}
