#!/usr/bin/env python3
"""Seed the local HA test instance's energy dashboard. Idempotent.

Imports 60 days of hourly history and saves energy preferences using every source type:
- grid import/export with cost and compensation, solar, a battery, gas and water as external
  statistics (`test:*`, they have no entity);
- devices, with one included in another (office computer in the office circuit), and water devices;
- the power and flow template sensors of config/configuration.yaml as `stat_rate`, with their hourly
  mean history imported so the power charts have data before the recorder compiles its own.

The energy follows a deterministic daily profile (seeded random weather and noise) so the frontend and
the native app can be compared on the same numbers. Only ever connects to localhost.
"""

import argparse
import asyncio
import math
import random
import sys
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

import aiohttp

from bootstrap import ENV_FILE, Ws, log

DAYS = 60
SEED = 42
TZ_OFFSET = timedelta(hours=1)  # The test instance is in Europe/Amsterdam; close enough for a daily profile.

GRID_PRICE = 0.30  # EUR per kWh
EXPORT_PRICE = 0.08
GAS_PRICE = 1.20  # EUR per m³
WATER_PRICE = 0.002  # EUR per L

BATTERY_CHARGE_MAX = 2.0  # kWh per hour
BATTERY_DISCHARGE_MAX = 1.0
BATTERY_CAPACITY = 4.0  # kWh


def solar_kw(hour: float, weather: float) -> float:
    return 2.5 * weather * math.sin(math.pi * (hour - 6) / 12) if 6 < hour < 18 else 0.0


def load_kw(hour: float, rng: random.Random) -> float:
    peak = (0.9 if 17 <= hour < 21 else 0) + (0.4 if 7 <= hour < 9 else 0)
    return 0.35 + peak + rng.uniform(-0.05, 0.15)


def hourly_profile(start: datetime, hours: int) -> list[dict[str, float]]:
    """Energy (kWh, m³, L) used or produced in each hour from [start]."""
    rng = random.Random(SEED)
    weather = {}
    rows = []
    stored = 0.0
    for i in range(hours):
        utc = start + timedelta(hours=i)
        local = utc + TZ_OFFSET
        day = local.date()
        weather.setdefault(day, rng.uniform(0.25, 1.0))
        h = local.hour + 0.5
        solar = solar_kw(h, weather[day])
        fridge = 0.05 + rng.uniform(0, 0.02)
        washer = 1.8 if local.hour == 10 and day.weekday() in (1, 5) else 0.002
        office_computer = 0.12 if 9 <= local.hour < 17 and day.weekday() < 5 else 0.003
        office_circuit = office_computer + (0.04 if 9 <= local.hour < 17 else 0.012)
        load = load_kw(h, rng) + fridge + washer + office_circuit
        net = load - solar
        charge = discharge = 0.0
        if net < 0:
            charge = min(-net, BATTERY_CHARGE_MAX, BATTERY_CAPACITY - stored)
        elif local.hour >= 17 or local.hour < 6:
            discharge = min(net, BATTERY_DISCHARGE_MAX, stored)
        stored += charge - discharge
        grid = net + charge - discharge
        heating = 0.6 if 6 <= local.hour < 9 or 17 <= local.hour < 22 else 0.05
        shower = 80.0 if local.hour in (7, 19) else 0.0
        garden_tap = 240.0 if local.hour == 18 and rng.random() < 0.5 else 0.0
        rows.append(
            {
                "grid_import": max(grid, 0.0),
                "grid_export": max(-grid, 0.0),
                "solar": solar,
                "battery_in": charge,
                "battery_out": discharge,
                "gas": heating * rng.uniform(0.8, 1.2),
                "water": shower + garden_tap + rng.uniform(0, 5),
                "fridge": fridge,
                "washer": washer,
                "office_circuit": office_circuit,
                "office_computer": office_computer,
                "shower": shower,
                "garden_tap": garden_tap,
            }
        )
    for row in rows:
        row["grid_cost"] = row["grid_import"] * GRID_PRICE
        row["grid_compensation"] = row["grid_export"] * EXPORT_PRICE
        row["gas_cost"] = row["gas"] * GAS_PRICE
        row["water_cost"] = row["water"] * WATER_PRICE
    return rows


# External statistic -> (name, unit, unit_class)
METERS: dict[str, tuple[str, str | None, str | None]] = {
    "grid_import": ("Grid import", "kWh", "energy"),
    "grid_export": ("Grid export", "kWh", "energy"),
    "grid_cost": ("Grid import cost", "EUR", None),
    "grid_compensation": ("Grid export compensation", "EUR", None),
    "solar": ("Solar production", "kWh", "energy"),
    "battery_in": ("Battery charged", "kWh", "energy"),
    "battery_out": ("Battery discharged", "kWh", "energy"),
    "gas": ("Gas", "m³", "volume"),
    "gas_cost": ("Gas cost", "EUR", None),
    "water": ("Water", "L", "volume"),
    "water_cost": ("Water cost", "EUR", None),
    "fridge": ("Fridge", "kWh", "energy"),
    "washer": ("Washer", "kWh", "energy"),
    "office_circuit": ("Office circuit", "kWh", "energy"),
    "office_computer": ("Office computer", "kWh", "energy"),
    "shower": ("Shower", "L", "volume"),
    "garden_tap": ("Garden tap", "L", "volume"),
}

# Template sensor -> (unit, unit_class, mean from an hour's row)
RATES: dict[str, tuple[str, str, object]] = {
    "sensor.solar_power": ("W", "power", lambda r: r["solar"] * 1000),
    "sensor.grid_power": ("W", "power", lambda r: (r["grid_import"] - r["grid_export"]) * 1000),
    "sensor.home_battery_power": ("W", "power", lambda r: (r["battery_out"] - r["battery_in"]) * 1000),
    "sensor.fridge_power": ("W", "power", lambda r: r["fridge"] * 1000),
    "sensor.washer_power": ("W", "power", lambda r: r["washer"] * 1000),
    "sensor.office_circuit_power": ("W", "power", lambda r: r["office_circuit"] * 1000),
    "sensor.office_computer_power": ("W", "power", lambda r: r["office_computer"] * 1000),
    "sensor.gas_flow": ("m³/h", "volume_flow_rate", lambda r: r["gas"]),
    "sensor.water_flow": ("L/min", "volume_flow_rate", lambda r: r["water"] / 60),
    "sensor.shower_flow": ("L/min", "volume_flow_rate", lambda r: r["shower"] / 60),
    "sensor.garden_tap_flow": ("L/min", "volume_flow_rate", lambda r: r["garden_tap"] / 60),
}

ENERGY_PREFS = {
    "energy_sources": [
        {
            "type": "grid",
            "stat_energy_from": "test:grid_import",
            "stat_energy_to": "test:grid_export",
            "stat_cost": "test:grid_cost",
            "stat_compensation": "test:grid_compensation",
            "stat_rate": "sensor.grid_power",
            "cost_adjustment_day": 0,
        },
        {"type": "solar", "stat_energy_from": "test:solar", "stat_rate": "sensor.solar_power", "config_entry_solar_forecast": None},
        {
            "type": "battery",
            "stat_energy_from": "test:battery_out",
            "stat_energy_to": "test:battery_in",
            "stat_rate": "sensor.home_battery_power",
            "stat_soc": "sensor.home_battery_charge",
        },
        {"type": "gas", "stat_energy_from": "test:gas", "stat_cost": "test:gas_cost", "stat_rate": "sensor.gas_flow"},
        {"type": "water", "stat_energy_from": "test:water", "stat_cost": "test:water_cost", "stat_rate": "sensor.water_flow"},
    ],
    "device_consumption": [
        {"stat_consumption": "test:fridge", "stat_rate": "sensor.fridge_power"},
        {"stat_consumption": "test:washer", "stat_rate": "sensor.washer_power", "name": "Washing machine"},
        {"stat_consumption": "test:office_circuit", "stat_rate": "sensor.office_circuit_power"},
        {
            "stat_consumption": "test:office_computer",
            "stat_rate": "sensor.office_computer_power",
            "included_in_stat": "test:office_circuit",
        },
    ],
    "device_consumption_water": [
        {"stat_consumption": "test:shower", "stat_rate": "sensor.shower_flow"},
        {"stat_consumption": "test:garden_tap", "stat_rate": "sensor.garden_tap_flow"},
    ],
}


async def import_history(ws: Ws) -> None:
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    # Up to the last full hour: the recorder compiles the current one for the sensors
    start = now - timedelta(days=DAYS)
    hours = DAYS * 24
    rows = hourly_profile(start, hours)
    starts = [(start + timedelta(hours=i)).isoformat() for i in range(hours)]
    for key, (name, unit, unit_class) in METERS.items():
        total = 0.0
        stats = []
        for row, at in zip(rows, starts):
            total += row[key]
            stats.append({"start": at, "state": round(total, 4), "sum": round(total, 4)})
        await ws.call(
            "recorder/import_statistics",
            metadata={
                "has_mean": False,
                "mean_type": 0,
                "has_sum": True,
                "name": name,
                "source": "test",
                "statistic_id": f"test:{key}",
                "unit_class": unit_class,
                "unit_of_measurement": unit,
            },
            stats=stats,
        )
    for statistic_id, (unit, unit_class, mean) in RATES.items():
        stats = []
        for row, at in zip(rows, starts):
            value = round(mean(row), 3)
            stats.append({"start": at, "mean": value, "min": value, "max": value})
        await ws.call(
            "recorder/import_statistics",
            metadata={
                "has_mean": True,
                "mean_type": 1,
                "has_sum": False,
                "name": None,
                "source": "recorder",
                "statistic_id": statistic_id,
                "unit_class": unit_class,
                "unit_of_measurement": unit,
            },
            stats=stats,
        )
    log(f"imported {hours} hours of {len(METERS)} meters and {len(RATES)} rates")


async def main(base: str) -> None:
    if urlparse(base).hostname not in {"localhost", "127.0.0.1", "::1"}:
        sys.exit("refused: seed_energy only targets localhost")
    token = next(
        line.split("=", 1)[1].strip() for line in ENV_FILE.read_text().splitlines() if line.startswith("TEST_HA_TOKEN=")
    )
    async with aiohttp.ClientSession() as session:
        ws = await Ws.connect(session, base, token)
        await import_history(ws)
        await ws.call("energy/save_prefs", **ENERGY_PREFS)
        log("saved energy preferences")
        await ws.ws.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8124")
    args = parser.parse_args()
    asyncio.run(main(args.url.rstrip("/")))
