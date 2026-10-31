#!/usr/bin/env node
/**
 * Builds the LG webOS app (.ipk): the TV app in webos/app (its own screens, built for
 * the TV) plus the data layer and player it shares with the website and Windows app.
 *
 *   node webos/build.js            → webos/dist/com.luminastreams.tv_<version>_all.ipk
 *   node webos/build.js --install  → also installs it on the default TV (ares-setup-device)
 *   node webos/build.js --launch   → install and start it
 *   ... --device LuminaTV --device LuminaTV2   → those TVs instead of the default one
 *   ... --all-tvs                              → every TV registered with ares-setup-device
 *   ... --no-package                           → only stage webos/build (for a browser preview)
 *
 * Needs the LG webOS CLI: npm install -g @webos-tools/cli
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const root = __dirname;
const webApp = path.join(root, '..', 'web', 'public', 'app');
const tvApp = path.join(root, 'app');
const stage = path.join(root, 'build');
const dist = path.join(root, 'dist');
const appinfo = JSON.parse(fs.readFileSync(path.join(root, 'appinfo.json'), 'utf8'));
const argv = process.argv.slice(2);

// What the TV app takes from the shared web code: [from (in web/public/app), to (in the package)].
const SHARED = [
  ['js/utils', 'js/utils'], ['js/core', 'js/core'], ['js/api', 'js/api'], ['js/player', 'js/player'],
  ['js/tv/flexGap.js', 'js/shared/flexGap.js'], ['css/android-player.css', 'css/android-player.css'],
];

function ares(cmd, args) {
  // The CLI is a .cmd shim on Windows, which needs a shell: quote every argument.
  const compat = `--require ${JSON.stringify(path.join(root, 'node-compat.js'))}`;
  const env = { ...process.env, NODE_OPTIONS: [process.env.NODE_OPTIONS, compat].filter(Boolean).join(' ') };
  execSync([cmd, ...args.map(a => JSON.stringify(a))].join(' '), { stdio: 'inherit', env });
}

/** TypeScript (any installed copy) transpiles the JS for older TV engines. */
function loadTypeScript() {
  try { return require('typescript'); } catch {}
  const globalRoot = execSync('npm root -g', { encoding: 'utf8' }).trim();
  const queue = [globalRoot];
  for (let depth = 0; depth < 4 && queue.length; depth++) {
    for (const dir of queue.splice(0)) {
      const candidate = path.join(dir, 'typescript');
      if (fs.existsSync(path.join(candidate, 'package.json'))) return require(candidate);
      let entries = [];
      try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch {}
      for (const e of entries) {
        if (!e.isDirectory()) continue;
        const sub = path.join(dir, e.name);
        queue.push(e.name === 'node_modules' || e.name.startsWith('@') ? sub : path.join(sub, 'node_modules'));
      }
    }
  }
  throw new Error('TypeScript not found: run "npm install -g typescript" once.');
}

/** 2021 TVs (webOS 6) run Chromium 79: lower the JS to ES2019 and expand the CSS `inset` shorthand. */
function makeLegacyCompatible(dir) {
  const ts = loadTypeScript();
  const walk = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => {
    const p = path.join(d, e.name);
    return e.isDirectory() ? walk(p) : [p];
  });
  const sides = v => {
    const [t, r = t, b = t, l = r] = v.trim().split(/\s+/);
    return [t, r, b, l];
  };
  for (const file of walk(dir)) {
    if (file.endsWith('.js')) {
      const out = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
        compilerOptions: { target: ts.ScriptTarget.ES2019, module: ts.ModuleKind.None, allowJs: true, removeComments: false },
        reportDiagnostics: false,
      }).outputText;
      fs.writeFileSync(file, out);
    } else if (file.endsWith('.css')) {
      const css = fs.readFileSync(file, 'utf8').replace(
        /(^|[{;\s])inset:\s*([^;}!]+?)\s*(!important)?\s*(?=[;}])/g,
        (_m, pre, value, imp) => {
          const [t, r, b, l] = sides(value);
          const i = imp ? ' !important' : '';
          return `${pre}top: ${t}${i}; right: ${r}${i}; bottom: ${b}${i}; left: ${l}${i}`;
        });
      fs.writeFileSync(file, css);
    }
  }
}

fs.rmSync(stage, { recursive: true, force: true });
fs.mkdirSync(stage, { recursive: true });
for (const [from, to] of SHARED) {
  fs.mkdirSync(path.dirname(path.join(stage, to)), { recursive: true });
  fs.cpSync(path.join(webApp, from), path.join(stage, to), { recursive: true });
}
// The website's router is not part of the TV app (it has its own screen stack).
fs.rmSync(path.join(stage, 'js', 'core', 'router.js'), { force: true });
fs.copyFileSync(path.join(tvApp, 'index.html'), path.join(stage, 'index.html'));
fs.cpSync(path.join(tvApp, 'css'), path.join(stage, 'css'), { recursive: true });
fs.cpSync(path.join(tvApp, 'js'), path.join(stage, 'js', 'tv'), { recursive: true });
fs.copyFileSync(path.join(root, 'legacy.css'), path.join(stage, 'css', 'legacy.css'));
fs.copyFileSync(path.join(root, 'appinfo.json'), path.join(stage, 'appinfo.json'));
for (const f of ['icon.png', 'largeIcon.png', 'splash.png']) {
  fs.copyFileSync(path.join(root, 'assets', f), path.join(stage, f));
}
makeLegacyCompatible(stage);

if (argv.includes('--no-package')) { console.log(`Staged ${stage}`); process.exit(0); }

fs.mkdirSync(dist, { recursive: true });
ares('ares-package', [stage, '-o', dist, '--no-minify']);
const ipk = path.join(dist, `${appinfo.id}_${appinfo.version}_all.ipk`);
console.log(`\nBuilt ${ipk}`);

let devices = argv.flatMap((a, i) => (a === '--device' && argv[i + 1] ? [argv[i + 1]] : []));
if (argv.includes('--all-tvs')) {
  const compat = `--require ${JSON.stringify(path.join(root, 'node-compat.js'))}`;
  const list = JSON.parse(execSync('ares-setup-device -F', { encoding: 'utf8', env: { ...process.env, NODE_OPTIONS: compat } }));
  devices = list.filter(d => d.profile === 'tv' && d.deviceinfo && d.deviceinfo.ip !== '127.0.0.1').map(d => d.name);
}
const failed = [];
if (argv.includes('--install') || argv.includes('--launch')) {
  for (const device of devices.length ? devices : [null]) {
    const target = device ? ['-d', device] : [];
    console.log(`\nInstalling on ${device || 'the default TV'}…`);
    try {
      ares('ares-install', [...target, ipk]);
      if (argv.includes('--launch')) ares('ares-launch', [...target, appinfo.id]);
    } catch {
      failed.push(device || 'default TV');
    }
  }
  if (failed.length) {
    console.error(`\nNot installed on: ${failed.join(', ')} (TV off, Developer Mode not running, or no key yet).`);
    process.exit(1);
  }
}
