// "Salvar" aqui apenas deixa a versao pronta em "Visao geral da publicacao".
// O envio para revisao continua sendo um clique do dono da conta.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.getByRole('button', { name: /^Salvar$/i }).click({ timeout: 15000 });
await page.waitForTimeout(10000);
console.log('URL:', page.url());
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 3000));
await browser.close();
