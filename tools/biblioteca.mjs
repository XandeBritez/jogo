// O pacote ja subiu numa tentativa anterior: em vez de gerar outro
// versionCode, anexa o que ja esta na biblioteca do Console.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.getByText('Adicionar da biblioteca', { exact: false }).first().click({ timeout: 15000 });
await page.waitForTimeout(6000);
console.log('URL:', page.url());
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 4000));
await browser.close();
