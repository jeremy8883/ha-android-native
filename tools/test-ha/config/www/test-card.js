// A minimal custom card for the native dashboard's web fallback: the entity's name and state, its details on tap.
class TestCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity) throw new Error("entity required");
    this._config = config;
  }

  set hass(hass) {
    const state = hass.states[this._config.entity];
    if (!this._card) {
      this._card = document.createElement("ha-card");
      this._card.header = this._config.title || "Custom card";
      this._content = document.createElement("div");
      this._content.style.padding = "0 16px 16px";
      this._content.style.cursor = "pointer";
      this._content.addEventListener("click", () => {
        this.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: this._config.entity }, bubbles: true, composed: true }));
      });
      this._card.appendChild(this._content);
      this.appendChild(this._card);
    }
    this._content.textContent = state ? `${state.attributes.friendly_name}: ${state.state}` : "Entity not found";
  }

  getCardSize() {
    return 2;
  }
}
customElements.define("test-card", TestCard);
