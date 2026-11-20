const test = require('node:test');
const assert = require('node:assert/strict');
const core = require('../ide-core.js');
test('detects Java, Kotlin, Kotlin script and HTML', () => {
  assert.equal(core.detectLanguage('Main.java'), 'java');
  assert.equal(core.detectLanguage('main.KT'), 'kotlin');
  assert.equal(core.detectLanguage('demo.kts'), 'kotlin-script');
  assert.equal(core.detectLanguage('index.html'), 'html');
});
test('creates useful Java and Kotlin starter templates', () => {
  assert.match(core.starterFor('Main.java'), /public class Main/);
  assert.match(core.starterFor('Main.kt'), /fun main\(\)/);
  assert.match(core.starterFor('Main.kts'), /println/);
});
test('counts lines and finds all occurrences', () => {
  assert.equal(core.countLines('a\nb\nc'), 3);
  assert.deepEqual(core.findMatches('one two one', 'one'), [0, 8]);
  assert.deepEqual(core.findMatches('abc', 'z'), []);
});
test('replaces every occurrence without treating replacement as regex', () => {
  assert.equal(core.replaceAll('a.a.a', '.', '-'), 'a-a-a');
  assert.equal(core.replaceAll('abc', '', 'x'), 'abc');
});

test('bundles CodeMirror 5.65.16 core, CSS, themes, modes and addons locally', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const vendor = path.join(__dirname, '..', 'vendor', 'codemirror');
  const pkg = JSON.parse(fs.readFileSync(path.join(vendor, 'package.json'), 'utf8'));
  assert.equal(pkg.version, '5.65.16');
  for (const file of [
    'lib/codemirror.js', 'lib/codemirror.css',
    'mode/clike/clike.js', 'mode/css/css.js',
    'mode/javascript/javascript.js', 'mode/xml/xml.js',
    'mode/htmlmixed/htmlmixed.js', 'mode/markdown/markdown.js',
    'mode/python/python.js', 'addon/edit/matchbrackets.js',
    'addon/edit/closebrackets.js', 'addon/selection/active-line.js',
    'theme/material-darker.css', 'LICENSE'
  ]) assert.ok(fs.existsSync(path.join(vendor, file)), 'Missing CodeMirror asset: ' + file);
});
test('web preview loads CodeMirror from local assets and keeps native app separate', () => {
  const fs = require('node:fs');
  const html = fs.readFileSync(require('node:path').join(__dirname, '..', 'index.html'), 'utf8');
  assert.match(html, /vendor\/codemirror\/lib\/codemirror\.js/);
  assert.match(html, /vendor\/codemirror\/lib\/codemirror\.css/);
  assert.match(html, /CodeMirror\.fromTextArea/);
  assert.match(html, /CODEMIRROR 5\.65\.16/i);
  assert.doesNotMatch(html, /https?:\/\/(cdn\.|cdnjs|unpkg)/i);
});
