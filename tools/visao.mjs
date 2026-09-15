import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.getByText('Ir para a visão geral', { exact: false }).first().click({ timeout: 15000 });
await page.waitForTimeout(10000);
console.log('URL:', page.url());
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 5000));
await browser.close();
