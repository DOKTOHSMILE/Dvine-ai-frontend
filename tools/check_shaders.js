#!/usr/bin/env node
// Compiles and links the stage's GLSL ES 3.00 shaders (StageRenderer's particle display, PodRenderer's pod
// scene) with a real compiler (Chromium's WebGL2, ANGLE + SwiftShader), which the JVM tests' fake GL cannot do.
// Usage: node tools/check_shaders.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const src = {};
for (const file of ['render/StageRenderer.java', 'render/pod/PodRenderer.java']) {
  const java = fs.readFileSync(path.join(__dirname, '../app/src/main/java/ai/dvine/', file), 'utf8');
  // each constant is string literals and earlier constants joined with +
  for (const m of java.matchAll(/static final String (\w+) = ([\s\S]*?);\n/g)) {
    const body = m[2].replace(/\/\/[^\n]*/g, '');
    let out = '';
    for (const t of body.matchAll(/"((?:[^"\\]|\\.)*)"|\b([A-Z][A-Z_]+)\b/g)) {
      out += t[1] !== undefined ? JSON.parse('"' + t[1] + '"') : (src[t[2]] || '');
    }
    src[m[1]] = out;
  }
}
// the programs they link (see onCreated)
const programs = [['FIELD_VS', 'FIELD_FS'], ['SOLID_VS', 'SOLID_FS'], ['SKY_VS', 'SKY_FS'], ['STAR_VS', 'STAR_FS'], ['METEOR_VS', 'METEOR_FS'],
  ['POD_VS', 'POD_FS'], ['GLOW_VS', 'GLOW_FS'], ['BEAM_VS', 'BEAM_FS'], ['DOT_VS', 'DOT_FS']];

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  const failures = await page.evaluate(({ src, programs }) => {
    const gl = document.createElement('canvas').getContext('webgl2');
    if (!gl) return ['no WebGL2 in this browser'];
    const out = [];
    const compile = (type, name) => {
      const s = gl.createShader(type);
      gl.shaderSource(s, src[name]);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) out.push(name + ': ' + gl.getShaderInfoLog(s).trim());
      return s;
    };
    for (const [v, f] of programs) {
      if (!src[v] || !src[f]) { out.push('missing ' + (src[v] ? f : v)); continue; }
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl.VERTEX_SHADER, v));
      gl.attachShader(p, compile(gl.FRAGMENT_SHADER, f));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) out.push(v + ' + ' + f + ' link: ' + gl.getProgramInfoLog(p).trim());
    }
    return out;
  }, { src, programs });
  await browser.close();
  if (failures.length) {
    console.error(failures.join('\n'));
    process.exit(1);
  }
  console.log(programs.length + ' shader programs compile and link.');
})();
