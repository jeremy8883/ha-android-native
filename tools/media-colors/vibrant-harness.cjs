// Writes dashboard-core/src/test/resources/fixtures/media-colors/vibrant.json: runs node-vibrant 4.0.4's default filter
// and MMCQ (colorCount 16, as the frontend's extractColors) with the frontend's customGenerator on deterministic pixel
// sets. Usage: node tools/media-colors/vibrant-harness.cjs <dir with node_modules/@vibrant/{color,image,quantizer,
// quantizer-mmcq}@4.0.4> > dashboard-core/src/test/resources/fixtures/media-colors/vibrant.json
const { createRequire } = require("module");
const req = createRequire(process.argv[2] + "/node_modules/x.js");
const { MMCQ } = req("@vibrant/quantizer-mmcq");

const lum = ([r, g, b]) => {
  const f = (c) => { c /= 255; return Math.abs(c) <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
};
const contrast = (a, b) => { const l1 = lum(a), l2 = lum(b); return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05); };
const ratio = (a, b) => Math.round((contrast(a, b) + Number.EPSILON) * 100) / 100;

function generate(colors) {
  colors.sort((a, b) => b.population - a.population);
  const bg = colors[0];
  let fg;
  for (let i = 1; i < colors.length && fg === undefined; i++) {
    if (ratio(bg.rgb, colors[i].rgb) > 4.5) { fg = colors[i].rgb; break; }
    for (let j = i + 1; j < colors.length; j++) {
      const c = colors[i], d = colors[j];
      const diff = Math.abs(c.rgb[0] - d.rgb[0]) + Math.abs(c.rgb[1] - d.rgb[1]) + Math.abs(c.rgb[2] - d.rgb[2]);
      if (diff > 150) continue;
      if (ratio(bg.rgb, d.rgb) > 4.5) { fg = d.rgb; break; }
    }
  }
  if (fg === undefined) fg = bg.getYiq() < 200 ? [255, 255, 255] : [0, 0, 0];
  const hex = (rgb) => "#" + rgb.map((c) => c.toString(16).padStart(2, "0")).join("");
  return { fg: hex(fg), bg: hex(bg.rgb) };
}

function pixels(seed) {
  let s = seed >>> 0;
  const next = () => { s = (s * 1664525 + 1013904223) % 4294967296; return s; };
  const k = 2 + (next() % 6);
  const base = [];
  for (let i = 0; i < k; i++) base.push([next() % 256, next() % 256, next() % 256]);
  const n = 3000;
  const data = new Uint8ClampedArray(n * 4);
  for (let i = 0; i < n; i++) {
    const c = base[next() % k];
    for (let ch = 0; ch < 3; ch++) data[i * 4 + ch] = Math.min(255, Math.max(0, c[ch] + (next() % 41) - 20));
    data[i * 4 + 3] = next() % 10 === 0 ? 100 : 255;
  }
  return data;
}

const out = [];
for (let seed = 1; seed <= 30; seed++) {
  const data = pixels(seed);
  for (let i = 0; i < data.length; i += 4) {
    const [r, g, b, a] = [data[i], data[i + 1], data[i + 2], data[i + 3]];
    if (!(a >= 125 && !(r > 250 && g > 250 && b > 250))) data[i + 3] = 0;
  }
  out.push({ seed, ...generate(MMCQ(data, { colorCount: 16 })) });
}
console.log(JSON.stringify(out, null, 1));
