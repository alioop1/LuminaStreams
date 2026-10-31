#!/usr/bin/env node
/**
 * Builds the LG webOS app (.ipk) from the shared web app.
 *
 *   node webos/build.js            → webos/dist/com.luminastreams.tv_<version>_all.ipk
 *   node webos/build.js --install  → also installs it on the default TV (ares-setup-device)
 *   node webos/build.js --launch   → install and start it
 *
 * Needs the LG webOS CLI: npm install -g @webos-tools/cli
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const root = __dirname;
const webApp = path.join(root, '..', 'web', 'public', 'app');
const stage = path.join(root, 'build');
const dist = path.join(root, 'dist');
const appinfo = JSON.parse(fs.readFileSync(path.join(root, 'appinfo.json'), 'utf8'));

// Windows-only pieces of the web app that the TV never loads.
const SKIP = new Set(['player-overlay.html', 'manifest.json']);

function ares(cmd, args) {
  // The CLI is a .cmd shim on Windows, which needs a shell: quote every argument.
  execSync([cmd, ...args.map(a => JSON.stringify(a))].join(' '), { stdio: 'inherit' });
}

fs.rmSync(stage, { recursive: true, force: true });
fs.mkdirSync(stage, { recursive: true });
for (const entry of fs.readdirSync(webApp)) {
  if (SKIP.has(entry)) continue;
  fs.cpSync(path.join(webApp, entry), path.join(stage, entry), { recursive: true });
}
fs.copyFileSync(path.join(root, 'appinfo.json'), path.join(stage, 'appinfo.json'));
for (const f of ['icon.png', 'largeIcon.png', 'splash.png']) {
  fs.copyFileSync(path.join(root, 'assets', f), path.join(stage, f));
}

fs.mkdirSync(dist, { recursive: true });
ares('ares-package', [stage, '-o', dist, '--no-minify']);
const ipk = path.join(dist, `${appinfo.id}_${appinfo.version}_all.ipk`);
console.log(`\nBuilt ${ipk}`);

const argv = process.argv.slice(2);
if (argv.includes('--install') || argv.includes('--launch')) {
  ares('ares-install', [ipk]);
  if (argv.includes('--launch')) ares('ares-launch', [appinfo.id]);
}
