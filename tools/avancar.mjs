// Vai para a tela "Visualizar e confirmar", que lista os avisos e o que falta.
// Para aqui: o botao de enviar para revisao e do dono da conta.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
await page.getByRole('button', { name: /^Avançar$/i }).click({ timeout: 15000 });
await page.waitForTimeout(9000);
console.log('URL:', page.url());
console.log('---');
console.log((await page.evaluate(() => document.body.innerText)).slice(0, 5000));
await browser.close();
