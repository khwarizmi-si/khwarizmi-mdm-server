const fs = require('fs');
const path = require('path');

fs.rmSync(path.resolve(__dirname, '..', 'node_modules'), { recursive: true, force: true });
