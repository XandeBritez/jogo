// Escreve as notas da versao e salva o rascunho. Nao envia para revisao.
import { chromium } from 'playwright';

const NOTAS = `<pt-BR>
Primeira versão do Fodinha!

• Jogue contra bots, ou com amigos por WiFi, Bluetooth ou pela internet (sala por código)
• Chat de voz nas salas WiFi e internet
• Personalize baralho, cores, tema claro/escuro e tamanho do texto
</pt-BR>`;

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

const area = page.locator('textarea').last();
await area.click();
await area.fill(NOTAS);
await page.waitForTimeout(2000);

await page.getByRole('button', { name: /Salvar como rascunho/i }).click({ timeout: 15000 });
await page.waitForTimeout(10000);

const t = await page.evaluate(() => document.body.innerText);
console.log('idiomas com notas:', (t.match(/notas da versão em (\d+) idioma/) ?? [])[1] ?? '?');
console.log('erro na tela:', /já foi usado|error/i.test(t) ? 'SIM' : 'nao');
console.log('---');
console.log(t.slice(0, 3500));
await browser.close();
