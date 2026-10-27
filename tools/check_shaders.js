#!/usr/bin/env node
// Compiles and links WireRenderer's GLSL ES 3.00 shaders with a real compiler (Chromium's WebGL2,
// ANGLE + SwiftShader), which the JVM tests' fake GL cannot do. Usage: node tools/check_shaders.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const java = fs.readFileSync(path.join(__dirname, '../app/src/main/java/ai/dvine/render/WireRenderer.java'), 'utf8');
const src = {};
for (const m of java.matchAll(/static final String (\w+_[VF]S) = ([\s\S]*?);\n/g)) {
  const parts = [...m[2].replace(/\/\/[^\n]*/g, '').matchAll(/"((?:[^"\\]|\\.)*)"/g)].map(p => JSON.parse('"' + p[1] + '"'));
  src[m[1]] = parts.join('');
}
// the programs WireRenderer links (see onCreated)
const programs = [['FILL_VS', 'FILL_FS'], ['PARTICLE_VS', 'PARTICLE_FS'], ['INNER_VS', 'PARTICLE_FS'], ['GRID_VS', 'GRID_FS']];

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
