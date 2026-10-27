const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const PORT = process.env.PORT || 5000;
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
    '.html': 'text/html; charset=UTF-8',
    '.css': 'text/css; charset=UTF-8',
    '.js': 'application/javascript; charset=UTF-8',
    '.json': 'application/json; charset=UTF-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf'
};

const server = http.createServer((req, res) => {
    let reqPath = decodeURI(req.url.split('?')[0]);
    if (reqPath === '/' || reqPath === '/index.html') {
        reqPath = '/app/index.html';
    }

    let filePath = path.join(PUBLIC_DIR, reqPath);

    // If directory, look for index.html inside
    if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
        filePath = path.join(filePath, 'index.html');
    }

    // Rewrite rules
    if (!fs.existsSync(filePath)) {
        if (reqPath.startsWith('/app')) {
            filePath = path.join(PUBLIC_DIR, 'app', 'index.html');
        } else {
            filePath = path.join(PUBLIC_DIR, 'index.html');
        }
    }

    fs.readFile(filePath, (err, content) => {
        if (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end('500 Server Error');
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';

        res.writeHead(200, {
            'Content-Type': contentType,
            'Access-Control-Allow-Origin': '*',
            'Cache-Control': 'no-cache'
        });
        res.end(content);
    });
});

const os = require('os');
function getLocalIp() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal && !iface.address.startsWith('169.254')) {
                return iface.address;
            }
        }
    }
    return 'localhost';
}

server.listen(PORT, '0.0.0.0', () => {
    const localIp = getLocalIp();
    const localUrl = `http://localhost:${PORT}`;
    const networkAppUrl = `http://${localIp}:${PORT}/app/`;

    console.log(`\n======================================================`);
    console.log(`  🌟 LuminaStreams Web Portal is Running!`);
    console.log(`  🔗 PC Local URL:       ${localUrl}`);
    console.log(`  📱 iPhone / iPad (Wi-Fi): ${networkAppUrl}`);
    console.log(`======================================================\n`);

    // Open browser on Windows
    exec(`start ${localUrl}`);
});
