"""The energy platform: the forecast production by hour, from yesterday to the day after tomorrow.

A sine from 06:00 to 18:00 local time peaking at 2.6 kWh, slightly above the seeded solar so the line shows
apart from the bars; the same every day, so captures don't depend on the weather of the seed.
"""

import math
from datetime import timedelta

from homeassistant.core import HomeAssistant
from homeassistant.util import dt as dt_util

PEAK_WH = 2600
FIRST_HOUR = 6
LAST_HOUR = 18


async def async_get_solar_forecast(hass: HomeAssistant, config_entry_id: str) -> dict | None:
    start = dt_util.start_of_local_day() - timedelta(days=1)
    wh_hours = {}
    for hour in range(4 * 24):
        time = start + timedelta(hours=hour)
        local = time.hour
        if FIRST_HOUR <= local < LAST_HOUR:
            # The middle of the hour, so the morning and evening hours aren't 0
            value = PEAK_WH * math.sin(math.pi * (local + 0.5 - FIRST_HOUR) / (LAST_HOUR - FIRST_HOUR))
            wh_hours[time.isoformat()] = round(value)
    return {"wh_hours": wh_hours}
