// Marca a linha do versionCode 3 na biblioteca e anexa a versao.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

// A tabela tem uma caixa de selecao por linha; a do codigo 3 e a primeira.
const linha = page.locator('tr', { hasText: 'App bundle' }).filter({ hasText: '3' }).first();
const caixa = linha.locator('input[type="checkbox"], [role="checkbox"]').first();
await caixa.click({ timeout: 15000 });
await page.waitForTimeout(2000);

await page.getByRole('button', { name: /Adicionar à versão/i }).click({ timeout: 15000 });
await page.waitForTimeout(8000);
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 4000));
await browser.close();
