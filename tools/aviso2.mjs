import { chromium } from 'playwright';
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts().flatMap((c) => c.pages()).find((p) => p.url().includes('play.google.com'));
// O expansor do aviso e um botao com o rotulo "1 aviso" ou a setinha ao lado.
for (const alvo of [page.getByText('1 aviso', { exact: false }).first(), page.getByText('expand_more', { exact: true }).first()]) {
  await alvo.click({ timeout: 8000 }).then(() => console.log('cliquei')).catch((e) => console.log('nao:', e.message.split('\n')[0]));
  await page.waitForTimeout(4000);
  const t = await page.evaluate(() => document.body.innerText);
  const i = t.indexOf('aviso');
  const trecho = t.slice(Math.max(0, i - 100), i + 900);
  if (trecho.length > 200) { console.log('---'); console.log(trecho); break; }
}
await browser.close();
