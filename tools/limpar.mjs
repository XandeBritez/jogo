// Tira do formulario o upload que falhou (o "clear" ao lado do nome do
// arquivo), sem mexer no pacote que veio da biblioteca.
import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

const candidatos = [
  page.getByRole('button', { name: /^clear$/i }),
  page.locator('[aria-label*="Remover"], [aria-label*="remover"], [aria-label*="Excluir"]'),
  page.getByText('clear', { exact: true }),
];
for (const loc of candidatos) {
  const n = await loc.count();
  console.log('candidato com', n, 'elemento(s)');
  if (n) {
    await loc.first().click({ timeout: 8000 }).then(() => console.log('cliquei')).catch((e) => console.log('falhou:', e.message.split('\n')[0]));
    await page.waitForTimeout(4000);
    break;
  }
}
const t = await page.evaluate(() => document.body.innerText);
console.log('--- ainda tem erro? ---');
console.log(/já foi usado/.test(t) ? 'SIM, erro ainda na tela' : 'NAO, erro sumiu');
await browser.close();
