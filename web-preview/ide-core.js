function detectLanguage(filename) {
  const name = String(filename || '').toLowerCase();
  if (name.endsWith('.kt')) return 'kotlin';
  if (name.endsWith('.kts')) return 'kotlin-script';
  if (name.endsWith('.java')) return 'java';
  if (name.endsWith('.html') || name.endsWith('.htm')) return 'html';
  if (name.endsWith('.css')) return 'css';
  if (name.endsWith('.js')) return 'javascript';
  return 'text';
}
function starterFor(filename) {
  const lang = detectLanguage(filename);
  if (lang === 'java') return 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello from Nice Java IDE!");\n    }\n}\n';
  if (lang === 'kotlin') return 'fun main() {\n    println("Hello from Nice Java IDE!")\n}\n';
  if (lang === 'kotlin-script') return 'println("Hello from Nice Java IDE!")\n';
  if (lang === 'html') return '<!doctype html>\n<html><head><meta charset="utf-8"><title>Preview</title></head><body><h1>Hello from Nice Java IDE!</h1></body></html>\n';
  return '';
}
function countLines(source) { return String(source ?? '').split(/\r\n|\r|\n/).length; }
function findMatches(source, query) {
  if (!query) return [];
  const text = String(source ?? ''), q = String(query);
  const out = []; let at = 0;
  while ((at = text.indexOf(q, at)) !== -1) { out.push(at); at += q.length || 1; }
  return out;
}
function replaceAll(source, query, replacement) {
  if (!query) return String(source ?? '');
  return String(source ?? '').split(String(query)).join(String(replacement ?? ''));
}
if (typeof module !== 'undefined') module.exports = { detectLanguage, starterFor, countLines, findMatches, replaceAll };
