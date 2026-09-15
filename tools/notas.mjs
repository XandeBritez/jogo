// Preenche as notas da versao (pt-BR) e confere o estado do pacote.
import { chromium } from 'playwright';

const NOTAS = `<pt-BR>
Primeira versão do Fodinha!

• Jogue contra bots, ou com amigos por WiFi, Bluetooth ou pela internet (sala por código)
• Chat de voz nas salas WiFi e internet
• Personalize baralho, cores, tema claro/escuro e tamanho do texto
</pt-BR>`;

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

// A caixa de notas e um editor grande; o campo de nome da versao e um input.
const area = page.locator('textarea').last();
if (await area.count()) {
  await area.click();
  await area.fill(NOTAS);
  console.log('notas preenchidas');
} else {
  console.log('nao achei a caixa de notas');
}

await page.waitForTimeout(2000);
console.log('---');
console.log(await page.evaluate(() => document.body.innerText.slice(0, 4000)));
await browser.close();
