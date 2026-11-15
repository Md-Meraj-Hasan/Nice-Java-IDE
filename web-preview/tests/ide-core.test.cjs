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
