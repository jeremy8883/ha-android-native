#!/usr/bin/env node
// Golden-fixture capture for the HA frontend `home` dashboard strategy.
//
// Drives the real frontend served by the LOCAL test instance (tools/test-ha, http://localhost:8124)
// in headless Chromium, then dumps:
//   - the inputs the strategies read (the frontend's processed `hass` view),
//   - the raw WebSocket responses the native app starts from,
//   - the generated dashboard / view / section configs, produced by calling the strategy
//     classes' `generate()` exactly like get-strategy.ts does.
//
// Safety: only ever talks to localhost. It never reads HASS_SERVER / HASS_TOKEN, refuses any
// non-localhost base URL, and aborts every browser request that leaves localhost.
//
// Usage: node capture.mjs [--variant admin|nonadmin|all] [--out <dir>]

import { chromium } from "playwright";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, rmSync, writeFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, "..", "..");
const ENV_FILE = join(REPO, "tools", "test-ha", ".env");
const FIXTURES_ROOT = join(REPO, "dashboard-core", "src", "test", "resources", "fixtures", "home");

const NONADMIN = { name: "Golden Viewer", username: "golden-viewer", password: "golden-viewer" };

// Values frozen in states so fixtures don't churn on every capture.
const FROZEN_TIME = "2026-01-01T00:00:00+00:00";
const FROZEN_CONTEXT = { id: "01JGOLDENCONTEXT000000000000", parent_id: null, user_id: null };

// --------------------------------------------------------------------------------------------
// Args, env and safety

function parseArgs(argv) {
  const args = { variant: "all", out: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--variant") args.variant = argv[++i];
    else if (a === "--out") args.out = argv[++i];
    else if (a === "-h" || a === "--help") {
      console.log("node capture.mjs [--variant admin|nonadmin|all] [--out <fixtures/home dir>]");
      process.exit(0);
    } else throw new Error(`Unknown argument: ${a}`);
  }
  if (!["admin", "nonadmin", "all"].includes(args.variant)) {
    throw new Error(`--variant must be admin, nonadmin or all`);
  }
  return args;
}

function readTestEnv() {
  // Only the test instance's own .env. Deliberately never looks at process.env.
  const env = {};
  for (const line of readFileSync(ENV_FILE, "utf8").split("\n")) {
    const m = line.match(/^\s*(TEST_HA_URL|TEST_HA_TOKEN)\s*=\s*(.*?)\s*$/);
    if (m) env[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
  if (!env.TEST_HA_TOKEN) throw new Error(`TEST_HA_TOKEN missing in ${ENV_FILE}; run tools/test-ha/up.sh`);
  return env;
}

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

function isLocalUrl(url) {
  try {
    const u = new URL(url);
    return LOCAL_HOSTS.has(u.hostname);
  } catch {
    return false;
  }
}

function assertLocal(url) {
  if (!isLocalUrl(url)) throw new Error(`Refusing non-localhost URL: ${url}`);
}

// --------------------------------------------------------------------------------------------
// Small HTTP / WS helpers (Node side, localhost only)

async function wsSession(baseUrl, accessToken) {
  assertLocal(baseUrl);
  const ws = new WebSocket(baseUrl.replace(/^http/, "ws") + "/api/websocket");
  let nextId = 1;
  const pending = new Map();
  let ready;
  const readyPromise = new Promise((res, rej) => (ready = { res, rej }));
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.type === "auth_required") ws.send(JSON.stringify({ type: "auth", access_token: accessToken }));
    else if (msg.type === "auth_ok") ready.res();
    else if (msg.type === "auth_invalid") ready.rej(new Error("auth_invalid"));
    else if (msg.type === "result" && pending.has(msg.id)) {
      const p = pending.get(msg.id);
      pending.delete(msg.id);
      msg.success ? p.res(msg.result) : p.rej(Object.assign(new Error(msg.error?.message), msg.error));
    }
  };
  ws.onerror = (e) => ready.rej(e);
  await readyPromise;
  return {
    call(msg) {
      const id = nextId++;
      ws.send(JSON.stringify({ ...msg, id }));
      return new Promise((res, rej) => pending.set(id, { res, rej }));
    },
    close() {
      ws.close();
    },
  };
}

async function postJson(url, body, form = false) {
  assertLocal(url);
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": form ? "application/x-www-form-urlencoded" : "application/json" },
    body: form ? new URLSearchParams(body).toString() : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`POST ${url} -> ${res.status}: ${text}`);
  return JSON.parse(text);
}

/** Ensures the non-admin user exists and returns a fresh access/refresh token pair for it. */
async function nonAdminTokens(baseUrl, adminToken) {
  const ws = await wsSession(baseUrl, adminToken);
  try {
    const users = await ws.call({ type: "config/auth/list" });
    let user = users.find((u) => u.name === NONADMIN.name);
    if (!user) {
      ({ user } = await ws.call({
        type: "config/auth/create",
        name: NONADMIN.name,
        group_ids: ["system-users"],
        local_only: false,
      }));
      await ws.call({
        type: "config/auth_provider/homeassistant/create",
        user_id: user.id,
        username: NONADMIN.username,
        password: NONADMIN.password,
      });
      console.log(`  created non-admin user ${NONADMIN.username}`);
    }
  } finally {
    ws.close();
  }
  const clientId = `${baseUrl}/`;
  const flow = await postJson(`${baseUrl}/auth/login_flow`, {
    client_id: clientId,
    handler: ["homeassistant", null],
    redirect_uri: `${baseUrl}/?auth_callback=1`,
  });
  const step = await postJson(`${baseUrl}/auth/login_flow/${flow.flow_id}`, {
    client_id: clientId,
    username: NONADMIN.username,
    password: NONADMIN.password,
  });
  if (step.type !== "create_entry") throw new Error(`login flow failed: ${JSON.stringify(step)}`);
  const tok = await postJson(
    `${baseUrl}/auth/token`,
    { grant_type: "authorization_code", code: step.result, client_id: clientId },
    true
  );
  return { access_token: tok.access_token, refresh_token: tok.refresh_token, expires_in: tok.expires_in };
}

// --------------------------------------------------------------------------------------------
// Code evaluated inside the page

// Installed in every page via addInitScript; defines window.__golden helpers.
function pageHelpers() {
  const deepAll = (selector, root = document) => {
    const out = [];
    const walk = (node) => {
      if (!node) return;
      if (node.querySelectorAll) {
        for (const el of node.querySelectorAll(selector)) out.push(el);
        for (const el of node.querySelectorAll("*")) if (el.shadowRoot) walk(el.shadowRoot);
      }
    };
    walk(root);
    return out;
  };
  const clone = (v) => (v === undefined ? null : JSON.parse(JSON.stringify(v)));
  const navigate = (path) => {
    history.pushState(null, "", path);
    window.dispatchEvent(new CustomEvent("location-changed", { detail: { replace: false } }));
  };
  window.__golden = { deepAll, clone, navigate };
}

async function waitForHomePanel(page) {
  await page.waitForFunction(
    () => {
      const ha = document.querySelector("home-assistant");
      const h = ha && ha.hass;
      if (!h || !h.states || !h.entities || !h.devices || !h.areas || !h.floors || !h.panels || !h.user) {
        return false;
      }
      if (h.config?.state !== "RUNNING") return false;
      const panel = window.__golden.deepAll("ha-panel-home")[0];
      return !!(panel && panel._lovelace);
    },
    null,
    { timeout: 60000, polling: 250 }
  );
}

/** Navigates in-app to /home/<path> and waits for its hui-view to hold a generated config. */
async function visitView(page, path, strategyType) {
  await page.evaluate((p) => window.__golden.navigate(`/home/${p}`), path);
  await page.waitForFunction(
    ([p, type]) => {
      if (type && !customElements.get(`${type}-view-strategy`)) return false;
      const root = window.__golden.deepAll("hui-root")[0];
      if (!root || !location.pathname.endsWith(`/home/${p}`)) return false;
      const view = window.__golden.deepAll("hui-view", root.shadowRoot)[0];
      // hui-view is reused across routes: wait until it holds *this* view's generated config.
      return !!(view && view._config && view._config.path === p);
    },
    [path, strategyType],
    { timeout: 30000, polling: 250 }
  );
  // Let section strategies (common-controls) settle.
  await page.waitForFunction(
    () => {
      const root = window.__golden.deepAll("hui-root")[0];
      const view = window.__golden.deepAll("hui-view", root.shadowRoot)[0];
      const wanted = (view._config.sections ?? []).filter((s) => s.strategy).length;
      // hui-section elements are created after the view config lands, and the section strategy
      // resolves asynchronously after that: wait for every strategy section to be expanded.
      const done = window.__golden
        .deepAll("hui-section", root.shadowRoot)
        .filter((s) => s.config?.strategy && s._config && !s._config.strategy);
      return done.length === wanted;
    },
    null,
    { timeout: 30000, polling: 250 }
  );
  return page.evaluate(() => {
    const g = window.__golden;
    const root = g.deepAll("hui-root")[0];
    const view = g.deepAll("hui-view", root.shadowRoot)[0];
    const sections = g
      .deepAll("hui-section", root.shadowRoot)
      .filter((s) => s.config?.strategy)
      .map((s) => ({ index: s.index ?? null, raw: g.clone(s.config), expanded: g.clone(s._config) }));
    return { view: g.clone(view._config), sections };
  });
}

// The built-in panels that show one generated view (src/panels/{light,climate,security,maintenance}).
const SUMMARY_PANELS = ["light", "climate", "security", "maintenance"];

/**
 * Navigates in-app to the summary panel /<panel> (loading its view strategy chunk) and returns the view the panel
 * generated itself (`ha-panel-<panel>._lovelace.config.views[0]`).
 */
async function visitSummaryPanel(page, panel) {
  await page.evaluate((p) => window.__golden.navigate(`/${p}`), panel);
  await page.waitForFunction(
    (p) => {
      if (!customElements.get(`${p}-view-strategy`)) return false;
      const el = window.__golden.deepAll(`ha-panel-${p}`)[0];
      return !!el?._lovelace?.config?.views?.[0];
    },
    panel,
    { timeout: 30000, polling: 250 }
  );
  return page.evaluate((p) => window.__golden.clone(window.__golden.deepAll(`ha-panel-${p}`)[0]._lovelace.config.views[0]), panel);
}

/**
 * Navigates in-app to the energy panel (loading its dashboard and view strategy chunks) and returns the dashboard it
 * generated itself, then each view as hui-view resolved it (`/energy/<path>`).
 */
async function visitEnergyPanel(page) {
  const panelConfig = async (path) => {
    await page.evaluate((p) => window.__golden.navigate(p), path);
    await page.waitForFunction(
      () => !!window.__golden.deepAll("ha-panel-energy")[0]?._lovelace?.config?.views,
      null,
      { timeout: 30000, polling: 250 }
    );
    return page.evaluate(() => window.__golden.clone(window.__golden.deepAll("ha-panel-energy")[0]._lovelace.config));
  };
  const dashboard = await panelConfig("/energy");
  const views = {};
  for (const v of dashboard.views) {
    await panelConfig(`/energy/${v.path}`);
    await page.waitForFunction(
      (path) => {
        const panel = window.__golden.deepAll("ha-panel-energy")[0];
        const view = window.__golden.deepAll("hui-view", panel.shadowRoot)[0];
        // The previous view's element can still be there right after navigating
        return view?._config?.path === path && !view._config.strategy;
      },
      v.path,
      { timeout: 30000, polling: 250 }
    );
    views[v.path] = await page.evaluate(() => {
      const panel = window.__golden.deepAll("ha-panel-energy")[0];
      return window.__golden.clone(window.__golden.deepAll("hui-view", panel.shadowRoot)[0]._config);
    });
  }
  return { dashboard, views };
}

/**
 * Fixed periods to load the energy data of, in the browser's time zone (UTC): whole days, as the period selector
 * sets them, within the test instance's 60 days of seeded statistics.
 */
function energyPeriods(now = new Date()) {
  const DAY = 86400000;
  const midnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const days = (startMs, count) => ({ start: new Date(startMs).toISOString(), end: new Date(startMs + count * DAY - 1).toISOString() });
  // The week before last, starting on Sunday (en-US)
  const thisWeek = midnight - new Date(midnight).getUTCDay() * DAY;
  const monthStart = Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1);
  const monthEnd = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1) - 1;
  return [
    { name: "today", ...days(midnight, 1), compare: "" },
    { name: "day", ...days(midnight - 3 * DAY, 1), compare: "" },
    { name: "week-compare", ...days(thisWeek - 14 * DAY, 7), compare: "previous" },
    { name: "month", start: new Date(monthStart).toISOString(), end: new Date(monthEnd).toISOString(), compare: "previous" },
    { name: "year", start: new Date(Date.UTC(now.getUTCFullYear(), 0, 1)).toISOString(), end: new Date(Date.UTC(now.getUTCFullYear() + 1, 0, 1) - 1).toISOString(), compare: "" },
    { name: "ten-days", ...days(midnight - 10 * DAY, 10), compare: "previous" },
  ];
}

/** The energy cards whose displayed values are recorded for each period. */
const ENERGY_CARD_CAPTURES = [
  { name: "energy-distribution", config: { type: "energy-distribution" } },
  { name: "energy-usage-graph", config: { type: "energy-usage-graph" } },
  { name: "energy-sources-table", config: { type: "energy-sources-table" } },
  { name: "energy-sources-table-totals", config: { type: "energy-sources-table", show_only_totals: true } },
  { name: "energy-sources-table-electricity", config: { type: "energy-sources-table", types: ["grid", "solar", "battery"] } },
  { name: "energy-sources-table-gas", config: { type: "energy-sources-table", types: ["gas"] } },
  { name: "energy-sources-table-water", config: { type: "energy-sources-table", types: ["water"] } },
  { name: "energy-gas-graph", config: { type: "energy-gas-graph" } },
  { name: "energy-water-graph", config: { type: "energy-water-graph" } },
  { name: "energy-solar-graph", config: { type: "energy-solar-graph" } },
  { name: "energy-grid-balance", config: { type: "energy-grid-balance" } },
  { name: "energy-devices-detail-graph", config: { type: "energy-devices-detail-graph" } },
  { name: "energy-devices-graph", config: { type: "energy-devices-graph", modes: ["bar"] } },
  { name: "energy-devices-graph-pie", config: { type: "energy-devices-graph", modes: ["pie"] } },
  { name: "energy-sankey", config: { type: "energy-sankey", group_by_floor: true, group_by_area: true } },
  { name: "energy-sankey-flat", config: { type: "energy-sankey", group_by_floor: false, group_by_area: false } },
  { name: "water-sankey", config: { type: "water-sankey", group_by_floor: true, group_by_area: true } },
  { name: "power-sankey", config: { type: "power-sankey", group_by_floor: true, group_by_area: true } },
  { name: "water-flow-sankey", config: { type: "water-flow-sankey", group_by_floor: true, group_by_area: true } },
  { name: "power-sources-graph", config: { type: "power-sources-graph" } },
  // The live cards again with states set in the page only (nothing is written to Home Assistant), so the
  // fixtures cover what the time of the capture doesn't: discharging, grid charging, small consumers, water flowing
  {
    name: "power-sankey-evening",
    config: { type: "power-sankey", group_by_floor: false, group_by_area: false },
    states: { "sensor.solar_power": "0", "sensor.grid_power": "400", "sensor.home_battery_power": "900", "sensor.fridge_power": "0.5", "sensor.washer_power": "0.4", "sensor.office_circuit_power": "15", "sensor.office_computer_power": "1" },
  },
  {
    name: "power-sankey-charging",
    config: { type: "power-sankey", group_by_floor: true, group_by_area: true },
    states: { "sensor.solar_power": "200", "sensor.grid_power": "2.5", "sensor.home_battery_power": "-1500", "sensor.fridge_power": "120", "sensor.washer_power": "1800", "sensor.office_circuit_power": "160", "sensor.office_computer_power": "120" },
    units: { "sensor.grid_power": "kW" },
  },
  {
    name: "water-flow-sankey-flowing",
    config: { type: "water-flow-sankey", group_by_floor: true, group_by_area: true },
    states: { "sensor.water_flow": "20", "sensor.shower_flow": "8", "sensor.garden_tap_flow": "12" },
  },
  {
    name: "water-flow-sankey-small",
    config: { type: "water-flow-sankey" },
    states: { "sensor.water_flow": "0.6", "sensor.shower_flow": "0.59", "sensor.garden_tap_flow": "0.0001" },
    units: { "sensor.water_flow": "m³/h" },
  },
  { name: "energy-self-sufficiency-gauge", config: { type: "energy-self-sufficiency-gauge" } },
  { name: "energy-grid-neutrality-gauge", config: { type: "energy-grid-neutrality-gauge" } },
  { name: "energy-solar-consumed-gauge", config: { type: "energy-solar-consumed-gauge" } },
  { name: "energy-carbon-consumed-gauge", config: { type: "energy-carbon-consumed-gauge" } },
];

/**
 * Renders the energy card [type] on the default collection and returns what it shows. Runs in the page, where it
 * is installed as a global so that `captureEnergyData` can call it.
 */
async function captureEnergyCard(config, states, units) {
  const g = window.__golden;
  const type = config.type;
  const real = document.querySelector("home-assistant").hass;
  const overridden = Object.fromEntries(
    [...new Set([...Object.keys(states ?? {}), ...Object.keys(units ?? {})])].map((id) => {
      const stateObj = real.states[id];
      const attributes = units?.[id] ? { ...stateObj.attributes, unit_of_measurement: units[id] } : stateObj.attributes;
      return [id, { ...stateObj, state: states?.[id] ?? stateObj.state, attributes }];
    }),
  );
  const hass = { ...real, states: { ...real.states, ...overridden } };
  const el = document.createElement(`hui-${type}-card`);
  el.hass = hass;
  el.setConfig({ ...config, collection_key: "energy_dashboard" });
  // Inside <home-assistant>, which provides the contexts cards read (the theme for charts)
  document.querySelector("home-assistant").shadowRoot.appendChild(el);
  const root = () => el.shadowRoot;
  for (let i = 0; i < 100 && !root()?.querySelector(".card-content, ha-chart-base, table, ha-card, .content, ha-sankey-chart"); i++) await new Promise((r) => setTimeout(r, 100));
  await new Promise((r) => setTimeout(r, 300));
  const text = (sel) => {
    const n = root().querySelector(sel);
    return n ? n.textContent.replace(/\s+/g, " ").trim() : null;
  };
  let result;
  if (type === "energy-distribution") {
    result = {
      lowCarbon: text(".low-carbon .circle"),
      solar: text(".solar .circle"),
      gas: text(".gas .circle"),
      water: text(".water .circle"),
      gridReturn: text(".grid .return"),
      gridConsumption: text(".grid .consumption"),
      home: text(".home .circle"),
      homeLabel: text(".home .label"),
      batteryIn: text(".battery-in"),
      batteryOut: text(".battery-out"),
      batterySoc: text(".battery-soc"),
      batteryIcon: root().querySelector(".battery-soc ha-svg-icon")?.path ?? null,
      ring: [...(root().querySelector(".home svg")?.querySelectorAll("circle") ?? [])].map((c) => ({
        class: c.getAttribute("class"),
        dasharray: c.getAttribute("stroke-dasharray"),
        dashoffset: c.getAttribute("stroke-dashoffset"),
      })),
      flows: [...root().querySelectorAll(".lines circle")].map((c) => ({
        class: c.getAttribute("class"),
        dur: c.querySelector("animateMotion")?.getAttribute("dur") ?? null,
      })),
      waterBelow: !!root().querySelector(".water.bottom"),
    };
  }
  if (type === "energy-sources-table") {
    result = {
      rows: [...root().querySelectorAll("tr")].map((tr) => ({
        total: tr.classList.contains("total"),
        bullet: !!tr.querySelector(".bullet"),
        cells: [...tr.children].map((c) => c.textContent.replace(/\s+/g, " ").trim()),
      })),
    };
  }
  if (type === "energy-grid-balance") {
    const width = (sel) => root().querySelector(sel)?.style.width ?? null;
    result = {
      title: text("[slot=primary]"),
      imported: text(".imported"),
      exported: text(".exported"),
      net: text(".net"),
      netClass: root().querySelector(".net")?.classList.contains("consumption") ? "consumption" : "return",
      left: width("#bar-exported"),
      right: width("#bar-imported"),
      netLeft: width("#bar-net-left"),
      netRight: width("#bar-net-right"),
    };
  }
  if (type.endsWith("-gauge")) {
    const gauge = root().querySelector("ha-gauge");
    if (gauge) await gauge.updateComplete;
    result = {
      value: gauge?.value ?? null,
      text: gauge?.shadowRoot?.querySelector(".value-text")?.textContent.replace(/\s+/g, " ").trim() ?? null,
      color: gauge?.style.getPropertyValue("--gauge-color") || null,
      needle: gauge?.needle ?? null,
      name: text(".name"),
      message: gauge ? null : root().querySelector("ha-card")?.textContent.replace(/\s+/g, " ").trim() ?? null,
    };
  }
  if (["energy-sankey", "water-sankey", "power-sankey", "water-flow-sankey"].includes(type)) {
    const chart = root().querySelector("ha-sankey-chart");
    const processed = chart ? chart._createData(chart.data, 400) : null;
    result = {
      data: chart ? g.clone(chart.data) : null,
      message: chart ? null : text(".card-content"),
      processed: processed
        ? {
            nodes: processed.nodes.map((n) => ({ id: n.id, value: n.value, depth: n.depth, color: n.itemStyle?.color ?? null })),
            links: processed.links.map((l) => ({ source: l.source, target: l.target, value: l.value })),
          }
        : null,
    };
  }
  if (type === "energy-devices-graph") {
    for (let i = 0; i < 50 && !el._chartData?.length; i++) await new Promise((r) => setTimeout(r, 100));
    const item = (d) =>
      d && typeof d === "object" && !Array.isArray(d)
        ? { id: d.id ?? null, value: Array.isArray(d.value) ? d.value[0] : d.value ?? null, name: d.name ?? null }
        : { id: null, value: d ?? null, name: null };
    result = {
      chartType: el._chartType ?? null,
      series: (el._chartData ?? []).map((s) => ({ name: s.name ?? null, type: s.type, data: (s.data ?? []).map(item) })),
      legend: (el._legendData ?? []).map((l) => ({ id: l.id ?? null, name: l.name ?? null, value: l.value ?? null })),
    };
  } else if (type.endsWith("-graph")) {
    // The chart's series as the card built them, and its axes
    for (let i = 0; i < 50 && !el._chartData?.length; i++) await new Promise((r) => setTimeout(r, 100));
    const options = root().querySelector("ha-chart-base")?.options ?? {};
    const date = (d) => (d instanceof Date ? d.toISOString() : d ?? null);
    result = {
      series: (el._chartData ?? []).map((s) => ({
        id: s.id ?? null,
        name: s.name ?? null,
        stack: s.stack ?? null,
        color: s.color ?? null,
        borderColor: s.itemStyle?.borderColor ?? null,
        data: (s.data ?? []).map((d) => (d && typeof d === "object" && "value" in d ? d.value : d)),
      })),
      total: el._total ?? null,
      unit: el._unit ?? null,
      yAxisFractionDigits: el._yAxisFractionDigits ?? null,
      xMin: date(options.xAxis?.min),
      xMax: date(options.xAxis?.max),
    };
  }
  // The forecasts the solar graph fetched itself (energy/solar_forecast, not part of the collection's requests)
  if (type === "energy-solar-graph") {
    result = { ...result, forecasts: g.clone(await hass.callWS({ type: "energy/solar_forecast" })) };
  }
  // The live states the card read, for the cards built from them
  if (["power-sankey", "water-flow-sankey", "power-sources-graph"].includes(type)) {
    const ids = Object.keys(hass.states).filter((id) => /_(power|flow)$/.test(id));
    result = { ...result, states: Object.fromEntries(ids.map((id) => [id, g.clone(hass.states[id])])), now: Date.now() };
  }
  el.remove();
  return g.clone(result);
}

/** The energy badges whose text is recorded for each period. */
const ENERGY_BADGES = ["power-total", "gas-total", "water-total"];

/** Renders the energy badge [type] on the default collection and returns its label, text and the states it read. */
async function captureEnergyBadge(type) {
  const g = window.__golden;
  const ha = document.querySelector("home-assistant");
  const el = document.createElement(`hui-${type}-badge`);
  el.hass = ha.hass;
  el.setConfig({ type, collection_key: "energy_dashboard" });
  ha.shadowRoot.appendChild(el);
  for (let i = 0; i < 50 && !el.shadowRoot?.querySelector("ha-badge"); i++) await new Promise((r) => setTimeout(r, 100));
  const badge = el.shadowRoot?.querySelector("ha-badge");
  const ids = Object.keys(ha.hass.states).filter((id) => /_(power|flow)$/.test(id));
  const result = {
    label: badge?.label ?? null,
    text: badge ? badge.textContent.replace(/\s+/g, " ").trim() : null,
    states: Object.fromEntries(ids.map((id) => [id, g.clone(ha.hass.states[id])])),
  };
  el.remove();
  return g.clone(result);
}

/** Renders the home dashboard's energy summary tile (always today, on its own collection) and returns its text. */
async function captureHomeEnergySummary() {
  const g = window.__golden;
  const ha = document.querySelector("home-assistant");
  const el = document.createElement("hui-home-summary-card");
  el.hass = ha.hass;
  el.setConfig({ type: "home-summary", summary: "energy" });
  ha.shadowRoot.appendChild(el);
  const info = () => el.shadowRoot?.querySelector("ha-tile-info");
  for (let i = 0; i < 100 && !(el._energyData && info()); i++) await new Promise((r) => setTimeout(r, 100));
  await new Promise((r) => setTimeout(r, 300));
  const tile = info();
  const result = {
    primary: tile?.primary ?? null,
    secondary: tile?.secondary ?? null,
    period: el._energyData ? { start: el._energyData.start?.toISOString() ?? null, end: el._energyData.end?.toISOString() ?? null } : null,
  };
  el.remove();
  return g.clone(result);
}

/** The entities whose more-info history is recorded: a mix of timelines, history lines and statistics. */
const HISTORY_ENTITIES = [
  "light.ceiling_lights",
  "binary_sensor.office_occupancy",
  "sensor.living_room_temperature",
  "sensor.house_power",
  "sensor.washer_power",
  "sensor.lights_on",
  "sensor.backup_backup_manager_state",
  "sensor.sun_next_dawn",
  "climate.hvac",
  "climate.ecobee",
  "humidifier.humidifier",
  "input_number.bedroom_brightness",
  "counter.coffee_cups",
  "lock.front_door_deadbolt",
  "cover.hall_window",
  "device_tracker.demo_paulus",
  "person.dev",
];

/**
 * Renders the more-info dialog's history section (`ha-more-info-history`) for each of [entityIds] and returns what
 * it fetched (the history stream's messages, or the statistics) and what it computed from them. Runs in the page.
 */
async function captureMoreInfoHistory(entityIds) {
  const g = window.__golden;
  const ha = document.querySelector("home-assistant");
  // The section's code loads with the dialog's: open one once
  if (!customElements.get("ha-more-info-history")) {
    ha.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: entityIds[0] }, bubbles: true, composed: true }));
    for (let i = 0; i < 100 && !customElements.get("ha-more-info-history"); i++) await new Promise((r) => setTimeout(r, 100));
    ha.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: "" }, bubbles: true, composed: true }));
    await new Promise((r) => setTimeout(r, 500));
  }
  const conn = ha.hass.connection;
  const out = {};
  for (const entityId of entityIds) {
    const messages = [];
    const requests = [];
    const origSubscribe = conn.subscribeMessage;
    const origSend = conn.sendMessagePromise;
    conn.subscribeMessage = function (callback, params, options) {
      requests.push(g.clone(params));
      // When it came, which the stream's purge of old states counts back from
      return origSubscribe.call(this, (m) => { messages.push({ ...g.clone(m), receivedAt: Date.now() }); callback(m); }, params, options);
    };
    conn.sendMessagePromise = async function (msg) {
      const entry = { request: g.clone(msg) };
      requests.push(entry.request);
      const result = await origSend.call(this, msg);
      entry.result = g.clone(result);
      messages.push(entry);
      return result;
    };
    const el = document.createElement("ha-more-info-history");
    el.hass = ha.hass;
    el.entityId = entityId;
    ha.shadowRoot.appendChild(el);
    for (let i = 0; i < 100 && !(el._stateHistory || el._statistics || el._error); i++) await new Promise((r) => setTimeout(r, 100));
    await new Promise((r) => setTimeout(r, 500));
    conn.subscribeMessage = origSubscribe;
    conn.sendMessagePromise = origSend;
    // The charts drawn from it, with their ECharts series
    const charts = [];
    const visit = (root) => {
      root?.querySelectorAll("*").forEach((n) => {
        if (["state-history-chart-timeline", "state-history-chart-line", "statistics-chart"].includes(n.localName)) {
          const time = (t) => (t instanceof Date ? t.getTime() : t ?? null);
          const point = (d) => (d && typeof d === "object" && !Array.isArray(d) ? (d.value ?? null) : d);
          charts.push({
            tag: n.localName,
            startTime: time(n.startTime),
            endTime: time(n.endTime),
            yAxisFractionDigits: n._yAxisFractionDigits ?? null,
            series: (n._chartData ?? []).map((s) => ({
              id: s.id ?? null,
              name: s.name ?? null,
              type: s.type ?? null,
              color: typeof s.color === "string" ? s.color : null,
              stack: s.stack ?? null,
              stackOrder: s.stackOrder ?? null,
              area: s.areaStyle?.color ?? null,
              lineWidth: s.lineStyle?.width ?? null,
              step: s.step ?? null,
              smooth: s.smooth ?? null,
              data: (s.data ?? []).map((d) => {
                const v = point(d);
                return Array.isArray(v) ? v.map((x) => (x instanceof Date ? x.getTime() : x)) : v;
              }),
            })),
          });
        }
        visit(n.shadowRoot);
      });
    };
    visit(el.shadowRoot);
    out[entityId] = {
      requests,
      messages,
      state: g.clone(ha.hass.states[entityId] ?? null),
      stateHistory: el._stateHistory ? g.clone(el._stateHistory) : null,
      statistics: el._statistics ? g.clone(el._statistics) : null,
      metadata: el._metadata ? g.clone(el._metadata) : null,
      error: el._error ? g.clone(el._error) : null,
      showMoreHref: el._showMoreHref ?? null,
      charts: g.clone(charts),
    };
    el.remove();
  }
  return { capturedAt: new Date().toISOString(), unitSystem: g.clone(ha.hass.config.unit_system), entities: out };
}

/** The entities whose more-info logbook is recorded: state changes with their causes, runs, presses and events. */
const LOGBOOK_ENTITIES = [
  "light.bed_light",
  "light.ceiling_lights",
  "input_boolean.guest_mode",
  "lock.back_door_lock",
  "input_select.house_mode",
  "automation.guest_welcome",
  "automation.bed_light_schedule",
  "script.lock_up",
  "button.push",
  "input_button.doorbell_test",
  "event.push_button_press",
  "binary_sensor.office_occupancy",
  "cover.hall_window",
  "person.dev",
  "alarm_control_panel.security",
  "select.speed",
  "sensor.backup_backup_manager_state",
  "climate.hvac",
];

/**
 * Makes logbook entries of every kind as the user: a state change that runs an automation, which runs a script and
 * changes a select; direct changes; presses; an automation run by hand. Runs in the page.
 */
async function seedLogbook() {
  const hass = document.querySelector("home-assistant").hass;
  const call = (domain, service, entityId) => hass.callService(domain, service, {}, { entity_id: entityId });
  await call("input_boolean", "turn_on", "input_boolean.guest_mode");
  await new Promise((r) => setTimeout(r, 1000));
  await call("input_boolean", "turn_off", "input_boolean.guest_mode");
  await call("lock", "unlock", "lock.back_door_lock");
  await call("button", "press", "button.push");
  await call("input_button", "press", "input_button.doorbell_test");
  await call("automation", "trigger", "automation.bed_light_schedule");
  await call("light", "toggle", "light.ceiling_lights");
  await call("light", "toggle", "light.ceiling_lights");
  // Past the recorder's commit interval, so the stream's history has them
  await new Promise((r) => setTimeout(r, 6000));
}

/**
 * Renders the more-info dialog's logbook section (`ha-more-info-logbook`) for each of [entityIds] and returns what
 * it fetched (the logbook stream's messages, the users, the traces) and each row it drew. Runs in the page.
 */
async function captureMoreInfoLogbook(entityIds) {
  const g = window.__golden;
  const ha = document.querySelector("home-assistant");
  // The section's code loads with the dialog's: open one once
  if (!customElements.get("ha-more-info-logbook")) {
    ha.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: entityIds[0] }, bubbles: true, composed: true }));
    for (let i = 0; i < 100 && !customElements.get("ha-more-info-logbook"); i++) await new Promise((r) => setTimeout(r, 100));
    ha.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: "" }, bubbles: true, composed: true }));
    await new Promise((r) => setTimeout(r, 500));
  }
  const conn = ha.hass.connection;
  const deep = (root, selector) => {
    const found = [];
    const visit = (r) => r?.querySelectorAll("*").forEach((n) => {
      if (n.matches(selector)) found.push(n);
      visit(n.shadowRoot);
    });
    visit(root);
    return found;
  };
  const text = (n) => n?.textContent.replace(/\s+/g, " ").trim() ?? null;
  const out = {};
  for (const entityId of entityIds) {
    const messages = [];
    const requests = [];
    const origSubscribe = conn.subscribeMessage;
    const origSend = conn.sendMessagePromise;
    conn.subscribeMessage = function (callback, params, options) {
      requests.push(g.clone(params));
      // When it came, which the purge of old entries counts back from
      return origSubscribe.call(this, (m) => { messages.push({ ...g.clone(m), receivedAt: Date.now() }); callback(m); }, params, options);
    };
    conn.sendMessagePromise = async function (msg) {
      const entry = { request: g.clone(msg) };
      requests.push(entry.request);
      const result = await origSend.call(this, msg);
      entry.result = g.clone(result);
      messages.push(entry);
      return result;
    };
    const el = document.createElement("ha-more-info-logbook");
    el.hass = ha.hass;
    el.entityId = entityId;
    ha.shadowRoot.appendChild(el);
    await new Promise((r) => setTimeout(r, 300));
    const logbook = () => el.shadowRoot?.querySelector("ha-logbook");
    for (let i = 0; i < 100 && !(logbook()?._logbookEntries || logbook()?._error); i++) await new Promise((r) => setTimeout(r, 100));
    // The users and traces load alongside
    await new Promise((r) => setTimeout(r, 1500));
    conn.subscribeMessage = origSubscribe;
    conn.sendMessagePromise = origSend;
    const lb = logbook();
    const renderer = lb?.shadowRoot?.querySelector("ha-logbook-renderer");
    const rows = [];
    renderer?.shadowRoot?.querySelectorAll(".entry-container").forEach((container) => {
      const entry = container.querySelector("ha-logbook-entry");
      const root = entry?.shadowRoot;
      const div = root?.querySelector(".entry");
      const dot = root?.querySelector(".dot");
      const node = root?.querySelector(".node");
      const causeIcon = root?.querySelector(".cause-badge > *");
      rows.push({
        dateHeader: text(container.querySelector("h4.date")),
        classes: div ? [...div.classList].sort() : [],
        nodeClasses: node ? [...node.classList].sort() : [],
        dotColor: dot?.style.getPropertyValue("--node-color") || null,
        dotUnavailable: dot?.classList.contains("unavailable") ?? false,
        primary: text(root?.querySelector(".primary-text")),
        time: text(root?.querySelector(".time-chip")),
        cause: root?.querySelector(".cause-badge")
          ? {
              tooltip: text(root.querySelector("ha-tooltip")),
              icon: causeIcon?.localName ?? null,
              path: causeIcon?.path ?? null,
              domain: causeIcon?.domain ?? null,
              user: causeIcon?.user ? g.clone(causeIcon.user) : null,
            }
          : null,
        traceLink: root?.querySelector(".trace-link")?.getAttribute("href") ?? null,
      });
    });
    out[entityId] = {
      requests,
      messages,
      state: g.clone(ha.hass.states[entityId] ?? null),
      entries: lb?._logbookEntries ? g.clone(lb._logbookEntries) : null,
      userIdToName: lb ? g.clone(lb._userIdToName) : null,
      systemUserIds: lb ? [...lb._systemUserIds].sort() : null,
      traceContexts: lb ? g.clone(lb._traceContexts) : null,
      error: lb?._error ? String(lb._error) : null,
      showMoreHref: el._showMoreHref ?? null,
      noEntries: text(lb?.shadowRoot?.querySelector(".no-entries") ?? renderer?.shadowRoot?.querySelector(".no-entries")),
      rows,
    };
    el.remove();
  }
  return { capturedAt: new Date().toISOString(), entities: out };
}

/**
 * Loads the energy collection for each of [periods] and records the WS requests it made with their results (what
 * `getEnergyData` fetches), on the energy panel's page.
 */
async function captureEnergyData(periods) {
  const g = window.__golden;
  const conn = document.querySelector("home-assistant").hass.connection;
  const collection = conn._energy_dashboard;
  if (!collection) return { error: "no energy collection" };
  const log = [];
  const orig = conn.sendMessagePromise;
  conn.sendMessagePromise = async function (msg) {
    const entry = { request: g.clone(msg) };
    log.push(entry);
    try {
      const result = await orig.call(this, msg);
      entry.result = g.clone(result);
      return result;
    } catch (err) {
      entry.error = { code: err?.code ?? null, message: err?.message ?? String(err) };
      throw err;
    }
  };
  const out = [];
  try {
    for (const p of periods) {
      log.length = 0;
      collection.setPeriod(new Date(p.start), new Date(p.end));
      collection.setCompare(p.compare);
      await collection.refresh();
      const state = collection.state;
      // What the collection fetched; rendering the cards below must not add to it
      const requests = log.map((e) => g.clone(e));
      const cards = {};
      for (const { name, config, states, units } of ENERGY_CARD_CAPTURES) cards[name] = await captureEnergyCard(config, states, units);
      for (const type of ENERGY_BADGES) cards[type] = await captureEnergyBadge(type);
      cards["home-summary-energy"] = await captureHomeEnergySummary();
      out.push({
        ...p,
        cards,
        requests,
        data: {
          start: state.start?.toISOString() ?? null,
          end: state.end?.toISOString() ?? null,
          startCompare: state.startCompare?.toISOString() ?? null,
          endCompare: state.endCompare?.toISOString() ?? null,
          co2SignalEntity: state.co2SignalEntity ?? null,
          waterUnit: state.waterUnit,
          gasUnit: state.gasUnit,
          statIds: Object.keys(state.stats).sort(),
        },
      });
    }
  } finally {
    conn.sendMessagePromise = orig;
  }
  return { capturedAt: new Date().toISOString(), periods: out };
}

/** The capture proper. Runs in the page with one consistent `hass` snapshot. */
async function captureInPage() {
  const g = window.__golden;

  // Name and secondary line of every tile of the expanded dashboard, as hui-tile-card computes them
  async function captureTiles(hass, expanded) {
    const tiles = [];
    const walk = (node) => {
      if (Array.isArray(node)) return node.forEach(walk);
      if (!node || typeof node !== "object") return;
      if (node.type === "tile" && node.entity) tiles.push(node);
      Object.values(node).forEach(walk);
    };
    walk(expanded?.views ?? []);
    const seen = new Set();
    const out = [];
    for (const cfg of tiles) {
      const key = JSON.stringify([cfg.entity, cfg.name ?? null, cfg.state_content ?? null, cfg.time_format ?? null]);
      if (seen.has(key)) continue;
      seen.add(key);
      const stateObj = hass.states[cfg.entity];
      if (!stateObj) continue;
      const name = hass.formatEntityName(stateObj, cfg.name);
      const sd = document.createElement("state-display");
      sd.hass = hass;
      sd.stateObj = stateObj;
      sd.content = cfg.state_content;
      sd.timeFormat = cfg.time_format;
      // Inside <home-assistant>, whose contexts relative times read their language from
      document.querySelector("home-assistant").shadowRoot.appendChild(sd);
      await sd.updateComplete;
      await new Promise((r) => setTimeout(r, 50));
      // Timestamps are drawn in hui-timestamp-display's shadow root
      const deep = (node) => {
        if (node.nodeType === Node.TEXT_NODE) return node.textContent;
        if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) return "";
        if (node.localName === "style" || node.localName === "script") return "";
        return [...(node.shadowRoot ? node.shadowRoot.childNodes : node.childNodes)].map(deep).join("");
      };
      const text = [...sd.childNodes].map(deep).join("").replace(/\s+/g, " ").trim();
      sd.remove();
      // When it was drawn, which relative times count from
      out.push({ config: g.clone(cfg), name, secondary: text, renderedAt: Date.now() });
    }
    return out;
  }

  // What built-in cards show for given configs, read from their rendered elements
  async function captureCardContent(hass, ha) {
    const host = document.createElement("div");
    ha.shadowRoot.appendChild(host);
    const summaries = {};
    for (const summary of ["light", "climate", "security", "media_players", "maintenance", "energy", "persons"]) {
      const el = document.createElement("hui-home-summary-card");
      el.hass = hass;
      el.setConfig({ type: "home-summary", summary });
      host.appendChild(el);
      await el.updateComplete;
      const info = el.shadowRoot?.querySelector("ha-tile-info");
      summaries[summary] = {
        primary: info?.primary ?? null,
        secondary: info?.secondary ?? null,
        loading: Boolean(info?.secondaryLoading),
      };
    }
    // Cards that subscribe to their data and may hide themselves; with and without hide_empty
    const infoCards = {};
    for (const type of ["repairs", "updates", "discovered-devices"]) {
      for (const hideEmpty of [false, true]) {
        const el = document.createElement(`hui-${type}-card`);
        el.hass = hass;
        el.setConfig({ type, ...(hideEmpty ? { hide_empty: true } : {}) });
        host.appendChild(el);
        let info;
        for (let i = 0; i < 50; i++) {
          await el.updateComplete;
          info = el.shadowRoot?.querySelector("ha-tile-info");
          if (el.hidden || (info && !info.secondaryLoading)) break;
          await new Promise((r) => setTimeout(r, 100));
        }
        infoCards[`${type}${hideEmpty ? ":hide_empty" : ""}`] = {
          hidden: Boolean(el.hidden),
          primary: el.hidden ? null : (info?.primary ?? null),
          secondary: el.hidden ? null : (info?.secondary ?? null),
        };
      }
    }
    host.remove();
    // The panels ha-sidebar lists, in order (the fixed Settings/notifications/profile entries are separate)
    const sidebarEl = g.deepAll("ha-sidebar")[0];
    const sidebar = sidebarEl
      ? [...sidebarEl.shadowRoot.querySelectorAll('ha-list-item-button[id^="sidebar-panel-"]')].map((el) => ({
          url_path: el.id.replace("sidebar-panel-", ""),
          title: el.querySelector(".item-text")?.textContent?.trim() ?? null,
        }))
      : null;
    return { "home-summary": summaries, info: infoCards, sidebar };
  }

  async function captureEntityDisplay(hass, ha) {
    // Visible text of a node, through shadow roots, as a user would read it
    const deepText = (node) => {
      if (node.nodeType === Node.TEXT_NODE) return node.textContent;
      if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) return "";
      if (node.localName === "style" || node.localName === "script") return "";
      const children = node.shadowRoot ? node.shadowRoot.childNodes : node.childNodes;
      return [...children].map(deepText).join("");
    };
    const clean = (t) => t.replace(/\s+/g, " ").trim();
    // Icons consume the config/entities/connection contexts provided by <home-assistant>
    const host = document.createElement("div");
    ha.shadowRoot.appendChild(host);
    const items = Object.entries(hass.states).map(([entityId, stateObj]) => {
      const icon = document.createElement("ha-state-icon");
      icon.stateObj = stateObj;
      const sd = document.createElement("state-display");
      sd.hass = hass;
      sd.stateObj = stateObj;
      host.append(icon, sd);
      return { entityId, stateObj, icon, sd };
    });
    const iconOf = (el) => {
      const root = el.shadowRoot;
      const named = root?.querySelector("ha-icon");
      if (named) return named.icon;
      if (root?.querySelector("ha-svg-icon")) return { fallback: true };
      return undefined;
    };
    for (let i = 0; i < 100 && items.some((it) => iconOf(it.icon) === undefined); i++) {
      await new Promise((r) => setTimeout(r, 100));
    }
    await Promise.all(items.map((it) => it.sd.updateComplete));
    await new Promise((r) => setTimeout(r, 500)); // nested timestamp elements
    const now = Date.now();
    const names = {
      default: undefined,
      entity: { type: "entity" },
      device: { type: "device" },
      area: { type: "area" },
      floor: { type: "floor" },
      device_entity: [{ type: "device" }, { type: "entity" }],
    };
    const entities = {};
    for (const it of items) {
      entities[it.entityId] = {
        icon: iconOf(it.icon) ?? null,
        state: hass.formatEntityState(it.stateObj),
        secondary: clean(deepText(it.sd)),
        names: Object.fromEntries(
          Object.entries(names).map(([k, n]) => [k, hass.formatEntityName(it.stateObj, n)])
        ),
      };
    }
    host.remove();
    return { now: new Date(now).toISOString(), locale: g.clone(hass.locale), time_zone: hass.config.time_zone, entities };
  }
  const ha = document.querySelector("home-assistant");
  const hass = ha.hass; // hass is replaced (not mutated) on updates, so this is a stable snapshot
  const panel = g.deepAll("ha-panel-home")[0];

  // ---- raw WS (same connection the frontend uses)
  const wsCalls = {
    get_states: { type: "get_states" },
    get_config: { type: "get_config" },
    get_panels: { type: "get_panels" },
    "auth-current_user": { type: "auth/current_user" },
    "config-entity_registry-list_for_display": { type: "config/entity_registry/list_for_display" },
    "config-device_registry-list": { type: "config/device_registry/list" },
    "config-area_registry-list": { type: "config/area_registry/list" },
    "config-floor_registry-list": { type: "config/floor_registry/list" },
    "frontend-get_system_data-home": { type: "frontend/get_system_data", key: "home" },
    "frontend-get_system_data-core": { type: "frontend/get_system_data", key: "core" },
    "frontend-get_user_data-core": { type: "frontend/get_user_data", key: "core" },
    "usage_prediction-common_control": { type: "usage_prediction/common_control" },
    "energy-get_prefs": { type: "energy/get_prefs" },
    "frontend-get_system_data-energy": { type: "frontend/get_system_data", key: "energy" },
    "manifest-get-frontend": { type: "manifest/get", integration: "frontend" },
    // What the frontend loads to resolve entity icons and translate states (data/icons.ts,
    // layouts/home-assistant.ts hassConnected)
    "frontend-get_icons-entity_component": { type: "frontend/get_icons", category: "entity_component" },
    "frontend-get_icons-entity": { type: "frontend/get_icons", category: "entity" },
    "frontend-get_translations-entity_component": {
      type: "frontend/get_translations",
      language: hass.language,
      category: "entity_component",
    },
    "repairs-list_issues": { type: "repairs/list_issues" },
    "frontend-get_user_data-sidebar": { type: "frontend/get_user_data", key: "sidebar" },
    "config_entries-flow-progress": { type: "config_entries/flow/progress" },
    "frontend-get_translations-entity": { type: "frontend/get_translations", language: hass.language, category: "entity" },
  };
  const ws = {};
  for (const [name, msg] of Object.entries(wsCalls)) {
    try {
      // callWS stamps an `id` onto the message it is given, so send a copy.
      ws[name] = { request: msg, result: await hass.callWS({ ...msg }) };
    } catch (err) {
      ws[name] = { request: msg, error: { code: err?.code ?? null, message: err?.message ?? String(err) } };
    }
  }

  // ---- a recording proxy over hass: logs callWS and localize as the strategies use them
  const strategyWs = [];
  const localizeLog = new Map();
  let level = null;
  const proxy = new Proxy(hass, {
    get(target, prop) {
      if (prop === "callWS") {
        return async (msg) => {
          const entry = { level, request: g.clone(msg) };
          strategyWs.push(entry);
          try {
            const result = await target.callWS(msg);
            entry.result = g.clone(result);
            return result;
          } catch (err) {
            entry.error = { code: err?.code ?? null, message: err?.message ?? String(err) };
            throw err;
          }
        };
      }
      if (prop === "localize") {
        return (key, ...args) => {
          const result = target.localize(key, ...args);
          const k = JSON.stringify([key, ...args]);
          if (!localizeLog.has(k)) localizeLog.set(k, { key, args: g.clone(args), result });
          return result;
        };
      }
      return Reflect.get(target, prop);
    },
  });

  // Mirrors get-strategy.ts generateStrategy(): built-in tag naming, legacy-config cleanup.
  const cleanLegacy = (cfg) => {
    if (Object.keys(cfg).length === 2 && "options" in cfg && typeof cfg.options === "object") {
      const c = { ...cfg, ...cfg.options };
      delete c.options;
      return c;
    }
    return cfg;
  };
  const run = async (configType, config, lvl) => {
    const { strategy, ...base } = config;
    const tag = `${strategy.type}-${configType}-strategy`;
    const cls = customElements.get(tag);
    if (!cls) return { error: `custom element ${tag} is not defined (lazy chunk not loaded)` };
    level = lvl;
    try {
      const generated = await cls.generate(cleanLegacy(strategy), proxy);
      return { config: { ...base, ...g.clone(generated) } };
    } catch (err) {
      return { error: String(err?.stack || err) };
    }
  };

  const strategyConfig = g.clone(panel._strategyConfig);
  const dashboard = await run("dashboard", strategyConfig, "dashboard");
  const views = [];
  const sections = [];
  for (const [vi, view] of (dashboard.config?.views ?? []).entries()) {
    const path = view.path ?? String(vi);
    const res = view.strategy ? await run("view", view, `view:${path}`) : { config: g.clone(view) };
    views.push({ index: vi, path, strategyType: view.strategy?.type ?? null, ...res });
    for (const [si, section] of (res.config?.sections ?? []).entries()) {
      if (!section.strategy) continue;
      const sres = await run("section", section, `section:${path}:${si}`);
      sections.push({ view: path, index: si, strategyType: section.strategy.type, raw: section, ...sres });
    }
  }

  // The summary panels' views, generated like the panels do (generateLovelaceViewStrategy on {strategy: {type}})
  const panelViews = {};
  for (const p of ["light", "climate", "security", "maintenance"]) {
    if (!hass.panels[p] || !customElements.get(`${p}-view-strategy`)) continue;
    panelViews[p] = await run("view", { strategy: { type: p } }, `panel:${p}`);
  }

  // The energy panel's dashboard and views, generated like ha-panel-energy does
  let energy = null;
  if (hass.panels.energy && customElements.get("energy-dashboard-strategy")) {
    const energyData = ws["frontend-get_system_data-energy"]?.result?.value ?? {};
    const energyDashboard = await run(
      "dashboard",
      { strategy: { type: "energy", default_collection: undefined, hidden_cards: energyData.hidden_cards } },
      "energy"
    );
    const energyViews = [];
    for (const view of energyDashboard.config?.views ?? []) {
      energyViews.push({ path: view.path, ...(await run("view", view, `energy:${view.path}`)) });
    }
    energy = { dashboard: energyDashboard, views: energyViews };
  }

  // Fully expanded config, like expandLovelaceConfigStrategies() would produce.
  let expanded = null;
  if (dashboard.config) {
    expanded = g.clone(dashboard.config);
    expanded.views = views.map((v) => {
      const out = g.clone(v.config ?? { error: v.error });
      if (out.sections) {
        out.sections = out.sections.map((s, si) => {
          const sec = sections.find((x) => x.view === v.path && x.index === si);
          return sec ? (sec.config ?? { error: sec.error }) : s;
        });
      }
      return out;
    });
  }

  // Translations the strategies can reach (bundle strings, not backend).
  const prefixes = ["ui.panel.lovelace.strategy.", "ui.panel.home.", "ui.panel.energy.", "panel."];
  // The frontend keeps its merged translation bundle on <home-assistant> (translations-mixin `__resources`).
  const resources = ha.__resources?.[hass.language] ?? hass.resources?.[hass.language] ?? {};
  const translations = {};
  for (const k of Object.keys(resources).sort()) {
    if (prefixes.some((p) => k.startsWith(p))) translations[k] = resources[k];
  }

  // ---- per-entity display: the icon (ha-state-icon), names (formatEntityName) and the tile's secondary
  // line (state-display, default content), all from the same hass snapshot
  const display = await captureEntityDisplay(hass, ha);
  display.tiles = await captureTiles(hass, expanded);
  const cards = await captureCardContent(hass, ha);
  // Frontend bundle strings used to display states (state.default.unknown, ...)
  const stateStrings = {};
  for (const k of Object.keys(resources).sort()) {
    if (k.startsWith("state.") || k.startsWith("ui.common.") || k.startsWith("ui.components.relative_time.")) {
      stateStrings[k] = resources[k];
    }
  }

  const pick = (o, keys) => Object.fromEntries(keys.filter((k) => k in o).map((k) => [k, g.clone(o[k])]));

  return {
    inputs: {
      states: g.clone(hass.states),
      entities: g.clone(hass.entities),
      devices: g.clone(hass.devices),
      areas: g.clone(hass.areas),
      floors: g.clone(hass.floors),
      user: g.clone(hass.user),
      panels: g.clone(hass.panels),
      config: g.clone(hass.config),
      language: hass.language,
      selectedLanguage: hass.selectedLanguage ?? null,
      locale: g.clone(hass.locale),
      systemData: g.clone(hass.systemData ?? null),
      userData: g.clone(hass.userData ?? null),
      "home-system-data": g.clone(panel._config ?? null),
      "strategy-config": strategyConfig,
      translations: { language: hass.language, strings: translations },
      "state-translations": { language: hass.language, strings: stateStrings },
      localize: [...localizeLog.values()],
      hass_misc: pick(hass, ["kioskMode", "suspendWhenHidden", "enableShortcuts", "vibrate", "dockedSidebar", "selectedTheme", "debugConnection"]),
    },
    ws,
    strategyWs,
    dashboard,
    views,
    sections,
    panelViews,
    energy,
    expanded,
    display,
    cards,
    rendered: { dashboard: g.clone(panel._lovelace?.config ?? null) },
  };
}

// --------------------------------------------------------------------------------------------
// Post-processing

function freezeState(s) {
  if (!s || typeof s !== "object") return s;
  const out = { ...s };
  for (const k of ["last_changed", "last_updated", "last_reported"]) if (k in out) out[k] = FROZEN_TIME;
  if ("context" in out) out.context = { ...FROZEN_CONTEXT };
  return out;
}

function freezeStatesMap(states) {
  return Object.fromEntries(Object.entries(states).map(([k, v]) => [k, freezeState(v)]));
}

function stableStringify(v) {
  return JSON.stringify(v, null, 2) + "\n";
}

const sameJson = (a, b) => JSON.stringify(a) === JSON.stringify(b);

function sanitizeFile(name) {
  return name.replace(/[^A-Za-z0-9_.-]/g, "_");
}

// --------------------------------------------------------------------------------------------
// One variant

async function captureVariant(browser, { baseUrl, variant, tokens, outDir }) {
  console.log(`\n== variant ${variant} -> ${relative(REPO, outDir)}`);
  const context = await browser.newContext({
    locale: "en-US",
    timezoneId: "UTC",
    viewport: { width: 1400, height: 1000 },
    serviceWorkers: "block",
  });
  // Hard network fence: nothing leaves localhost.
  await context.route("**/*", (route) => {
    const url = route.request().url();
    if (url.startsWith("data:") || url.startsWith("blob:") || isLocalUrl(url)) return route.continue();
    return route.abort();
  });
  const hassTokens = {
    hassUrl: baseUrl,
    clientId: `${baseUrl}/`,
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    expires_in: tokens.expires_in,
    expires: tokens.expires,
  };
  await context.addInitScript(
    ([t]) => {
      if (location.hostname !== "localhost" && location.hostname !== "127.0.0.1") return;
      localStorage.setItem("hassTokens", JSON.stringify(t));
      localStorage.setItem("selectedLanguage", JSON.stringify("en"));
    },
    [hassTokens]
  );
  await context.addInitScript(pageHelpers);
  const page = await context.newPage();
  page.on("pageerror", (e) => console.warn(`  [pageerror] ${e.message}`));
  page.on("framenavigated", (f) => {
    if (f === page.mainFrame() && !isLocalUrl(f.url())) throw new Error(`navigated off localhost: ${f.url()}`);
  });

  await page.goto(`${baseUrl}/home`, { waitUntil: "domcontentloaded" });
  if (page.url().includes("/auth/authorize")) throw new Error("token injection failed: landed on the login page");
  await waitForHomePanel(page);
  console.log("  /home rendered");

  // Visit each view so every lazily loaded strategy chunk is defined, and record what the live
  // frontend itself rendered for each view (cross-check for our direct generate() calls).
  const dashCfg = await page.evaluate(() => window.__golden.clone(window.__golden.deepAll("ha-panel-home")[0]._lovelace.config));
  const renderedViews = {};
  for (const [i, v] of dashCfg.views.entries()) {
    const path = v.path ?? String(i);
    renderedViews[path] = await visitView(page, path, v.strategy?.type);
    console.log(`  visited /home/${path}`);
  }
  const renderedPanels = {};
  const availablePanels = await page.evaluate(() => Object.keys(document.querySelector("home-assistant").hass.panels));
  for (const p of SUMMARY_PANELS.filter((it) => availablePanels.includes(it))) {
    renderedPanels[p] = await visitSummaryPanel(page, p);
    console.log(`  visited /${p}`);
  }
  const renderedEnergy = availablePanels.includes("energy") ? await visitEnergyPanel(page) : null;
  if (renderedEnergy) console.log(`  visited /energy (${Object.keys(renderedEnergy.views).join(", ")})`);
  // The same for every user: recorded once
  if (renderedEnergy && variant === "admin") {
    await page.evaluate(`window.captureEnergyCard = ${captureEnergyCard.toString()}; window.ENERGY_CARD_CAPTURES = ${JSON.stringify(ENERGY_CARD_CAPTURES)}; window.captureEnergyBadge = ${captureEnergyBadge.toString()}; window.captureHomeEnergySummary = ${captureHomeEnergySummary.toString()}; window.ENERGY_BADGES = ${JSON.stringify(ENERGY_BADGES)};`);
  }
  const energyData = renderedEnergy && variant === "admin" ? await page.evaluate(captureEnergyData, energyPeriods()) : null;
  // The same for every user: recorded once
  const historyData = variant === "admin" ? await page.evaluate(captureMoreInfoHistory, HISTORY_ENTITIES) : null;
  if (historyData) console.log(`  recorded the history of ${Object.keys(historyData.entities).length} entities`);
  await page.evaluate(() => window.__golden.navigate("/home/overview"));
  await page.waitForTimeout(500);

  const cap = await page.evaluate(captureInPage);
  // After the rest, so that the entries it makes (recent presses and changes) don't show in it
  if (variant === "admin") await page.evaluate(seedLogbook);
  const logbookData = variant === "admin" ? await page.evaluate(captureMoreInfoLogbook, LOGBOOK_ENTITIES) : null;
  if (logbookData) console.log(`  recorded the logbook of ${Object.keys(logbookData.entities).length} entities`);
  await context.close();

  // ---- write files
  rmSync(outDir, { recursive: true, force: true });
  const files = [];
  const write = (rel, data, compact = false) => {
    const p = join(outDir, rel);
    mkdirSync(dirname(p), { recursive: true });
    const text = compact ? JSON.stringify(data) + "\n" : stableStringify(data);
    writeFileSync(p, text);
    files.push({ path: rel, bytes: Buffer.byteLength(text), sha256: createHash("sha256").update(text).digest("hex") });
  };

  const inputs = { ...cap.inputs, states: freezeStatesMap(cap.inputs.states) };
  for (const [k, v] of Object.entries(inputs)) write(`inputs/${sanitizeFile(k)}.json`, v);

  for (const [name, entry] of Object.entries(cap.ws)) {
    let e = entry;
    if (name === "get_states" && Array.isArray(e.result)) e = { ...e, result: e.result.map(freezeState) };
    write(`ws/${sanitizeFile(name)}.json`, e);
  }
  write("ws/strategy-calls.json", cap.strategyWs);

  const problems = [];
  if (cap.dashboard.error) problems.push(`dashboard: ${cap.dashboard.error}`);
  write("outputs/dashboard.json", cap.dashboard.config ?? { error: cap.dashboard.error });
  for (const v of cap.views) {
    write(`outputs/views/${sanitizeFile(v.path)}.json`, v.config ?? { error: v.error });
    if (v.error) problems.push(`view ${v.path}: ${v.error}`);
  }
  for (const s of cap.sections) {
    write(`outputs/sections/${sanitizeFile(s.view)}/${s.index}-${sanitizeFile(s.strategyType)}.json`, {
      input: s.raw,
      output: s.config ?? { error: s.error },
    });
    if (s.error) problems.push(`section ${s.view}#${s.index}: ${s.error}`);
  }
  for (const [p, res] of Object.entries(cap.panelViews)) {
    write(`outputs/panels/${sanitizeFile(p)}.json`, res.config ?? { error: res.error });
    if (res.error) problems.push(`panel ${p}: ${res.error}`);
  }
  if (cap.energy) {
    write("outputs/energy/dashboard.json", cap.energy.dashboard.config ?? { error: cap.energy.dashboard.error });
    if (cap.energy.dashboard.error) problems.push(`energy: ${cap.energy.dashboard.error}`);
    for (const v of cap.energy.views) {
      write(`outputs/energy/views/${sanitizeFile(v.path)}.json`, v.config ?? { error: v.error });
      if (v.error) problems.push(`energy view ${v.path}: ${v.error}`);
    }
  }
  if (energyData) {
    // Mostly statistics rows: one line keeps it small
    write("energy/data.json", energyData, true);
    if (energyData.error) problems.push(`energy data: ${energyData.error}`);
  }
  if (historyData) write("history/more-info.json", historyData, true);
  if (logbookData) write("logbook/more-info.json", logbookData, true);
  write("outputs/expanded.json", cap.expanded);
  write("outputs/entity-display.json", cap.display);
  write("outputs/cards.json", cap.cards);

  // ---- cross-checks against what the live frontend rendered
  const checks = [];
  checks.push({
    what: "dashboard == ha-panel-home._lovelace.config",
    ok: sameJson(cap.dashboard.config, cap.rendered.dashboard),
  });
  for (const v of cap.views) {
    const r = renderedViews[v.path];
    if (!r) {
      checks.push({ what: `view ${v.path} rendered`, ok: false });
      continue;
    }
    // hui-view adds `type: getViewType(cfg)` on top of generateLovelaceViewStrategy's output.
    const ours = v.config ? { ...v.config, type: v.config.type ?? "masonry" } : null;
    checks.push({ what: `view ${v.path} == hui-view._config (+type)`, ok: sameJson(ours, r.view), ours, theirs: r.view });
    for (const rs of r.sections) {
      const idx = r.view.sections?.findIndex((s) => sameJson(s, rs.raw)) ?? -1;
      const s = cap.sections.find((x) => x.view === v.path && x.index === idx);
      // hui-section adds `type: type || "grid"`.
      const oursS = s?.config ? { ...s.config, type: s.config.type || "grid" } : null;
      checks.push({
        what: `section ${v.path}#${idx} == hui-section._config (+type)`,
        ok: sameJson(oursS, rs.expanded),
        ours: oursS,
        theirs: rs.expanded,
      });
    }
  }

  for (const p of SUMMARY_PANELS) {
    const ours = cap.panelViews[p]?.config ?? null;
    const theirs = renderedPanels[p] ?? null;
    if (!ours && !theirs) continue;
    checks.push({ what: `panel ${p} == ha-panel-${p}._lovelace.config.views[0]`, ok: sameJson(ours, theirs), ours, theirs });
  }

  if (cap.energy || renderedEnergy) {
    checks.push({
      what: "energy dashboard == ha-panel-energy._lovelace.config",
      ok: sameJson(cap.energy?.dashboard.config ?? null, renderedEnergy?.dashboard ?? null),
    });
    for (const v of cap.energy?.views ?? []) {
      const ours = v.config ? { ...v.config, type: v.config.type ?? "masonry" } : null;
      const theirs = renderedEnergy?.views[v.path] ?? null;
      checks.push({ what: `energy view ${v.path} == hui-view._config (+type)`, ok: sameJson(ours, theirs), ours, theirs });
    }
  }

  const manifest = {
    description: "Golden fixtures for the HA frontend `home` dashboard strategy, captured by tools/golden/capture.mjs",
    variant,
    source: { url: baseUrl, note: "local podman test instance tools/test-ha (demo integration + seeded registries)" },
    ha_version: cap.inputs.config?.version ?? null,
    frontend_version:
      (cap.ws["manifest-get-frontend"]?.result?.requirements ?? [])
        .find((r) => r.startsWith("home-assistant-frontend=="))
        ?.split("==")[1] ?? null,
    capture_date: new Date().toISOString(),
    user: { name: cap.inputs.user?.name, is_admin: cap.inputs.user?.is_admin },
    language: cap.inputs.language,
    frozen: {
      states: { fields: ["last_changed", "last_updated", "last_reported"], value: FROZEN_TIME, context: FROZEN_CONTEXT },
      applies_to: ["inputs/states.json", "ws/get_states.json"],
    },
    generate: {
      dashboard: 'customElements.get("home-dashboard-strategy").generate(strategy, hass), merged as {...base, ...generated}',
      views: 'customElements.get(`${view.strategy.type}-view-strategy`).generate(view.strategy, hass), merged as {...viewWithoutStrategy, ...generated}',
      sections: 'customElements.get(`${section.strategy.type}-section-strategy`).generate(section.strategy, hass), merged as {...sectionWithoutStrategy, ...generated}',
      hass: "document.querySelector('home-assistant').hass (one snapshot), wrapped in a Proxy that records callWS/localize",
    },
    cross_checks: checks.map(({ what, ok }) => ({ what, ok })),
    problems,
    files: [],
  };
  manifest.files = files.sort((a, b) => a.path.localeCompare(b.path));
  writeFileSync(join(outDir, "manifest.json"), stableStringify(manifest));

  const debugDir = join(HERE, "out", variant);
  rmSync(debugDir, { recursive: true, force: true });
  for (const c of checks) {
    console.log(`  ${c.ok ? "ok  " : "FAIL"} ${c.what}`);
    if (!c.ok && "ours" in c) {
      mkdirSync(debugDir, { recursive: true });
      const base = join(debugDir, sanitizeFile(c.what.split(" ==")[0]));
      writeFileSync(`${base}.ours.json`, stableStringify(c.ours));
      writeFileSync(`${base}.theirs.json`, stableStringify(c.theirs));
      console.log(`       diff ${relative(REPO, base)}.{ours,theirs}.json`);
    }
  }
  for (const p of problems) console.log(`  PROBLEM ${p}`);
  console.log(`  wrote ${files.length + 1} files`);
  return { checks, problems };
}

// --------------------------------------------------------------------------------------------

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const env = readTestEnv();
  const baseUrl = (env.TEST_HA_URL || "http://localhost:8124").replace(/\/+$/, "");
  assertLocal(baseUrl);
  const outRoot = args.out ? resolve(args.out) : FIXTURES_ROOT;

  const variants = args.variant === "all" ? ["admin", "nonadmin"] : [args.variant];
  const browser = await chromium.launch({ headless: true });
  let failed = false;
  try {
    for (const variant of variants) {
      let tokens;
      if (variant === "admin") {
        // Long-lived token; never expires within the run, so the frontend never tries to refresh.
        tokens = {
          access_token: env.TEST_HA_TOKEN,
          refresh_token: "golden-capture-no-refresh",
          expires_in: 315360000,
          expires: Date.now() + 315360000 * 1000,
        };
      } else {
        const t = await nonAdminTokens(baseUrl, env.TEST_HA_TOKEN);
        tokens = { ...t, expires: Date.now() + t.expires_in * 1000 };
      }
      const dir = join(outRoot, variant === "admin" ? "test-instance" : "test-instance-nonadmin");
      const { checks, problems } = await captureVariant(browser, { baseUrl, variant, tokens, outDir: dir });
      if (problems.length || checks.some((c) => !c.ok)) failed = true;
    }
  } finally {
    await browser.close();
  }
  if (failed) {
    console.error("\nCapture finished with problems or failed cross-checks (see above / manifest.json).");
    process.exit(1);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
