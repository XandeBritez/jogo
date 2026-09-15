// Troca o pacote anexado a versao de rascunho: tira o que esta com erro e
// sobe o AAB novo. Nao envia para revisao - isso e do dono da conta.
import { chromium } from 'playwright';

const AAB = process.argv[2];
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));

// Remove o pacote com erro, se houver (o "x" ao lado do nome do arquivo).
const limpar = page.locator('button:has-text("clear"), [aria-label*="Remover"], [aria-label*="remover"]').first();
if (await limpar.count()) {
  await limpar.click({ timeout: 10000 }).catch(() => console.log('(nao consegui clicar no remover)'));
  await page.waitForTimeout(3000);
}

// O input de arquivo fica escondido atras do botao "Enviar".
const input = page.locator('input[type="file"]').first();
await input.setInputFiles(AAB, { timeout: 30000 });
console.log('arquivo entregue ao formulario:', AAB);

// O processamento no servidor demora; espera o nome aparecer sem erro.
await page.waitForTimeout(25000);
console.log('URL:', page.url());
console.log('---');
console.log(await page.evaluate(() => document.body.innerText.slice(0, 4000)));
await browser.close();
