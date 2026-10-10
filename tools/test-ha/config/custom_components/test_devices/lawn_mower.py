"""Lawn mowers: one that does everything (on a device with a battery), one that only starts, one heading home."""

from homeassistant.components.lawn_mower import LawnMowerActivity, LawnMowerEntity, LawnMowerEntityFeature
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

DOMAIN = "test_devices"
ALL = LawnMowerEntityFeature.START_MOWING | LawnMowerEntityFeature.PAUSE | LawnMowerEntityFeature.DOCK

GARDEN_MOWER = DeviceInfo(identifiers={(DOMAIN, "garden_mower")}, name="Garden mower", manufacturer="Test")


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    async_add_entities(
        [
            TestLawnMower("garden_mower", "Garden mower", LawnMowerActivity.DOCKED, ALL, GARDEN_MOWER),
            TestLawnMower("simple_mower", "Simple mower", LawnMowerActivity.MOWING, LawnMowerEntityFeature.START_MOWING),
            TestLawnMower("returning_mower", "Returning mower", LawnMowerActivity.RETURNING, ALL),
        ]
    )


class TestLawnMower(LawnMowerEntity):
    def __init__(self, unique_id, name, activity, features, device=None) -> None:
        self._attr_unique_id = unique_id
        self._attr_supported_features = features
        self._attr_activity = activity
        if device:
            self._attr_device_info = device
            self._attr_has_entity_name = True
            self._attr_name = None
        else:
            self._attr_name = name

    async def async_start_mowing(self) -> None:
        self._attr_activity = LawnMowerActivity.MOWING
        self.async_write_ha_state()

    async def async_dock(self) -> None:
        self._attr_activity = LawnMowerActivity.DOCKED
        self.async_write_ha_state()

    async def async_pause(self) -> None:
        self._attr_activity = LawnMowerActivity.PAUSED
        self.async_write_ha_state()
