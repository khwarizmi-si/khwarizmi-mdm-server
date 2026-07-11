const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const root = path.resolve(__dirname, '..');
const webapp = path.resolve(root, '..', 'src', 'main', 'webapp');
const bowerComponents = path.join(root, 'bower_components');
const lib = path.join(root, 'lib');
const webappLib = path.join(webapp, 'lib');

function remove(target) {
  fs.rmSync(target, { recursive: true, force: true });
}

function ensureDir(target) {
  fs.mkdirSync(target, { recursive: true });
}

function copyDir(source, target, filter) {
  if (!fs.existsSync(source)) {
    return;
  }

  const stats = fs.statSync(source);
  if (stats.isDirectory()) {
    ensureDir(target);
    for (const entry of fs.readdirSync(source)) {
      if (filter && !filter(path.join(source, entry))) {
        continue;
      }
      copyDir(path.join(source, entry), path.join(target, entry), filter);
    }
    return;
  }

  ensureDir(path.dirname(target));
  fs.copyFileSync(source, target);
}

function run(command, args) {
  const cacheRoot = path.join(root, '.cache');
  ensureDir(cacheRoot);

  const result = spawnSync(command, args, {
    cwd: root,
    env: {
      ...process.env,
      BOWER_STORAGE__CACHE: path.join(cacheRoot, 'bower-cache'),
      BOWER_STORAGE__PACKAGES: path.join(cacheRoot, 'bower-packages'),
      BOWER_STORAGE__REGISTRY: path.join(cacheRoot, 'bower-registry'),
      XDG_CONFIG_HOME: path.join(cacheRoot, 'config')
    },
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });

  if (result.status !== 0) {
    process.exit(result.status || 1);
  }
}

remove(bowerComponents);
remove(lib);
remove(webappLib);

run(path.join(root, 'node_modules', '.bin', process.platform === 'win32' ? 'bower.cmd' : 'bower'), ['install']);

copyDir(bowerComponents, lib);
copyDir(lib, webappLib, (source) => !source.includes(`${path.sep}bootstrap-css-only${path.sep}`));

const bootstrapSource = path.join(lib, 'bootstrap-css-only');
copyDir(path.join(bootstrapSource, 'bootstrap.css'), path.join(webappLib, 'bootstrap-css-only', 'css', 'bootstrap.css'));

for (const entry of fs.readdirSync(bootstrapSource)) {
  if (entry.startsWith('glyphicons')) {
    copyDir(path.join(bootstrapSource, entry), path.join(webappLib, 'bootstrap-css-only', 'fonts', entry));
  }
}
