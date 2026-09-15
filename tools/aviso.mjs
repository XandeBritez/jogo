import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.getByText('Mostrar mais', { exact: false }).first().click({ timeout: 12000 }).catch((e) => console.log('nao abriu:', e.message.split('\n')[0]));
await page.waitForTimeout(5000);
const t = await page.evaluate(() => document.body.innerText);
const i = t.indexOf('Erros, alertas e mensagens');
console.log(t.slice(i, i + 1800));
await browser.close();
