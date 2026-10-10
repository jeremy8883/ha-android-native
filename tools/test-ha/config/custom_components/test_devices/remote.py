"""A TV remote with activities, which turning on with an activity switches to."""

from homeassistant.components.remote import RemoteEntity, RemoteEntityFeature
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

ACTIVITIES = ["Watch TV", "Play games", "Listen to music"]


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    async_add_entities([TestRemote()])


class TestRemote(RemoteEntity):
    _attr_unique_id = "tv_remote"
    _attr_name = "TV remote"
    _attr_supported_features = RemoteEntityFeature.ACTIVITY
    _attr_activity_list = ACTIVITIES
    _attr_current_activity = ACTIVITIES[0]
    _attr_is_on = True

    async def async_turn_on(self, activity: str | None = None, **kwargs) -> None:
        self._attr_is_on = True
        if activity in ACTIVITIES:
            self._attr_current_activity = activity
        self.async_write_ha_state()

    async def async_turn_off(self, **kwargs) -> None:
        self._attr_is_on = False
        self.async_write_ha_state()
