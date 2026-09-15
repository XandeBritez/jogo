import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
console.log('URL:', page.url());
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 4500));
await browser.close();
