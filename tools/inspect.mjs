// O Console monta a tabela dentro de shadow DOM: document.querySelector nao
// enxerga, mas os locators do Playwright atravessam.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

for (const sel of ['input[type=checkbox]', '[role=checkbox]', 'material-checkbox', 'tr', '[role=row]']) {
  const n = await page.locator(sel).count();
  console.log(`${sel}: ${n}`);
}
console.log('--- linhas visiveis ---');
const linhas = page.locator('[role=row]');
const n = await linhas.count();
for (let i = 0; i < Math.min(n, 8); i++) {
  const t = (await linhas.nth(i).innerText().catch(() => '')).replace(/\n/g, ' | ');
  console.log(i, t.slice(0, 90));
}
await browser.close();
