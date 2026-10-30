'use strict';
/**
 * Serves the shared web app (web/public/app) on 127.0.0.1.
 * An http://localhost origin keeps Firebase Auth, localStorage and the app's
 * relative asset paths working exactly as they do on the website. The port is
 * fixed so the origin (and therefore saved progress/settings) is stable.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PREFERRED_PORT = 41541;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function startServer(rootDir) {
  const root = path.resolve(rootDir);
  const server = http.createServer((req, res) => {
    let reqPath;
    try {
      reqPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    } catch {
      res.writeHead(400).end();
      return;
    }
    if (reqPath === '/') reqPath = '/index.html';
    const filePath = path.resolve(root, '.' + reqPath);
    // Never serve anything outside the web app folder.
    if (filePath !== root && !filePath.startsWith(root + path.sep)) {
      res.writeHead(403).end();
      return;
    }
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(404, { 'Content-Type': 'text/plain' }).end('Not found');
        return;
      }
      res.writeHead(200, {
        'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
        'Cache-Control': 'no-cache',
      });
      res.end(data);
    });
  });

  return new Promise((resolve, reject) => {
    const listen = port => server.listen(port, '127.0.0.1');
    server.once('listening', () => resolve({ server, port: server.address().port }));
    server.on('error', err => {
      // Port taken by something else: fall back to any free port.
      if (err.code === 'EADDRINUSE' && server.address() === null) {
        listen(0);
      } else {
        reject(err);
      }
    });
    listen(PREFERRED_PORT);
  });
}

module.exports = { startServer };
