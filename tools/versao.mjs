// Monta a versao de rascunho inteira numa passada: sobe o pacote, espera o
// processamento, escreve as notas e salva. Sem reload no meio - recarregar a
// pagina antes de salvar apaga tudo o que foi preenchido.
import { chromium } from 'playwright';

const AAB = process.argv[2];
const NOTAS = `<pt-BR>
Primeira versão do Fodinha!

• Jogue contra bots, ou com amigos por WiFi, Bluetooth ou pela internet (sala por código)
• Chat de voz nas salas WiFi e internet
• Personalize baralho, cores, tema claro/escuro e tamanho do texto
</pt-BR>`;

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
const texto = () => page.evaluate(() => document.body.innerText);

await page.locator('input[type="file"]').first().setInputFiles(AAB, { timeout: 30000 });
console.log('pacote entregue, esperando o processamento...');

// Espera ate 4 min: some o "otimizado para distribuicao" e aparece o tamanho
// do pacote (ou um erro, que tambem encerra a espera).
let t = await texto();
for (let i = 0; i < 48 && /otimizado para distribui|Fazendo upload|Solte os pacotes/.test(t); i++) {
  await page.waitForTimeout(5000);
  t = await texto();
}
const erro = t.match(/O código de versão[^\n]*|erro[^\n]{0,80}/i);
console.log(erro ? `ATENCAO: ${erro[0]}` : 'pacote aceito');

const area = page.locator('textarea').last();
await area.click();
await area.fill(NOTAS);
await page.waitForTimeout(1500);

await page.getByRole('button', { name: /Salvar como rascunho/i }).click({ timeout: 15000 });
await page.waitForTimeout(8000);
console.log('--- estado apos salvar ---');
console.log((await texto()).slice(0, 4000));
await browser.close();
