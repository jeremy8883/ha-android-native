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

/** The capture proper. Runs in the page with one consistent `hass` snapshot. */
async function captureInPage() {
  const g = window.__golden;

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
  const prefixes = ["ui.panel.lovelace.strategy.", "ui.panel.home.", "panel."];
  // The frontend keeps its merged translation bundle on <home-assistant> (translations-mixin `__resources`).
  const resources = ha.__resources?.[hass.language] ?? hass.resources?.[hass.language] ?? {};
  const translations = {};
  for (const k of Object.keys(resources).sort()) {
    if (prefixes.some((p) => k.startsWith(p))) translations[k] = resources[k];
  }

  // ---- per-entity display: the icon (ha-state-icon), names (formatEntityName) and the tile's secondary
  // line (state-display, default content), all from the same hass snapshot
  const display = await captureEntityDisplay(hass, ha);
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
    expanded,
    display,
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
  await page.evaluate(() => window.__golden.navigate("/home/overview"));
  await page.waitForTimeout(500);

  const cap = await page.evaluate(captureInPage);
  await context.close();

  // ---- write files
  rmSync(outDir, { recursive: true, force: true });
  const files = [];
  const write = (rel, data) => {
    const p = join(outDir, rel);
    mkdirSync(dirname(p), { recursive: true });
    const text = stableStringify(data);
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
  write("outputs/expanded.json", cap.expanded);
  write("outputs/entity-display.json", cap.display);

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
