// Navegacao no Console: parte do painel do app e clica, em ordem, cada texto
// passado na linha de comando. Determinista e repetivel - se algo der errado,
// basta rodar a mesma sequencia de novo.
import { chromium } from 'playwright';

const PAINEL = 'https://play.google.com/console/u/0/developers/9031192918517313268/app/4974567809621659009/app-dashboard';

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.setViewportSize({ width: 1600, height: 1200 });

const passos = process.argv.slice(2);
if (passos[0] !== '--aqui') {
  await page.goto(PAINEL, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000);
}

for (const texto of passos.filter((t) => t !== '--aqui')) {
  await page.getByText(texto, { exact: false }).first().click({ timeout: 15000 });
  await page.waitForTimeout(6000);
  console.log(`>> cliquei em "${texto}"`);
}

console.log('URL:', page.url());
console.log('---');
console.log(await page.evaluate(() => document.body.innerText.slice(0, 6000)));
await browser.close();
