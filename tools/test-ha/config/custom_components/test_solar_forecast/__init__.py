"""A solar forecast for the native dashboard's test instance: a fixed daily curve, no outbound calls.

The energy dashboard reads it through the energy platform (energy.py), like forecast_solar's.
"""

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    return True
