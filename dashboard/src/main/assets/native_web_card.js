// Draws one Lovelace card of the frontend for the native dashboard, which can't draw it itself (a custom card, or
// a built-in one not ported yet). Runs in the frontend's page once it has loaded: it loads the dashboard
// resources (custom cards' scripts, as `loadLovelaceResources` does), mounts `hui-card` with the card's config
// over the hidden app, keeps it on the latest `hass`, and tells the app its height, the details it asks for and
// the pages it opens (window.nativeCard).
(async (config) => {
  const bridge = window.nativeCard;
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const fail = (reason) => bridge.failed(String(reason));
  try {
    let ha = null;
    for (let i = 0; i < 300; i++) {
      ha = document.querySelector("home-assistant");
      if (ha?.hass?.states && customElements.get("hui-card")) break;
      await sleep(100);
    }
    if (!ha?.hass?.states || !customElements.get("hui-card")) return fail("frontend not loaded");

    // The dashboards' resources: custom cards register themselves when their script runs
    try {
      const resources = await ha.hass.callWS({ type: "lovelace/resources" });
      for (const resource of resources) {
        const url = new URL(resource.url, location.href).toString();
        if (resource.type === "css") {
          const link = document.createElement("link");
          link.rel = "stylesheet";
          link.href = url;
          document.head.appendChild(link);
        } else if (resource.type === "module") {
          import(url).catch((e) => console.warn("resource failed", url, e));
        } else {
          const script = document.createElement("script");
          script.src = url;
          document.head.appendChild(script);
        }
      }
    } catch (e) {
      console.warn("lovelace/resources failed", e);
    }
    const type = String(config.type || "");
    if (type.startsWith("custom:")) {
      const tag = type.slice("custom:".length);
      await Promise.race([customElements.whenDefined(tag), sleep(10000)]);
    }

    // The card alone, over the transparent page
    document.documentElement.style.background = "transparent";
    document.body.style.background = "transparent";
    ha.style.background = "transparent";
    const box = document.createElement("div");
    box.id = "native-card";
    box.style.cssText = "position:absolute;top:0;left:0;right:0;z-index:10000;background:transparent;padding:1px;";
    const card = document.createElement("hui-card");
    card.hass = ha.hass;
    card.config = config;
    card.load();
    box.appendChild(card);
    for (const child of [...ha.shadowRoot.children]) {
      if (child.localName !== "style") child.style.display = "none";
    }
    ha.shadowRoot.appendChild(box);

    // Live: the card follows the frontend's hass
    setInterval(() => {
      if (card.hass !== ha.hass) card.hass = ha.hass;
    }, 250);
    new ResizeObserver(() => bridge.height(Math.ceil(box.getBoundingClientRect().height))).observe(box);

    // The details and pages the card opens are the app's
    box.addEventListener("hass-more-info", (ev) => {
      ev.stopPropagation();
      if (ev.detail?.entityId) bridge.moreInfo(ev.detail.entityId);
    }, true);
    // Only a page the card opens on a tap is the app's; the hidden frontend's own routing stays its own
    let tappedAt = 0;
    box.addEventListener("pointerdown", () => { tappedAt = Date.now(); }, true);
    box.addEventListener("click", () => { tappedAt = Date.now(); }, true);
    const fromCard = () => Date.now() - tappedAt < 2000;
    for (const method of ["pushState", "replaceState"]) {
      const original = history[method].bind(history);
      history[method] = (state, title, url) => {
        if (url && fromCard()) {
          const target = new URL(String(url), location.href);
          bridge.navigate(target.pathname + target.search);
          return;
        }
        original(state, title, url);
      };
    }
    bridge.loaded();
  } catch (e) {
    fail(e);
  }
})(__CONFIG__);
