const { chromium } = require('playwright');
(async () => {
  const [, , url, dir, out] = process.argv;
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1080, height: 2400 } });
  await p.goto(url);
  const errs = await p.evaluate((d) => run(d), dir);
  console.log(errs.length ? errs.join('\n') : 'no GL errors');
  await (await p.$('#c')).screenshot({ path: out });
  await b.close();
})();
