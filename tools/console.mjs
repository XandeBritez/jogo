// Liga no Chrome que ja esta aberto (porta de debug 9222) e usa a aba logada
// do Play Console. Nao abre navegador novo: a sessao do Google e a que vale.
//
//   node tools/console.mjs abas                 lista as abas abertas
//   node tools/console.mjs ver                  descreve a aba do Console
//   node tools/console.mjs print <arquivo.png>  screenshot da aba
//   node tools/console.mjs campos               lista os campos do formulario
//
// Publicar (o botao final) nao esta aqui de proposito: quem clica e o dono
// da conta.

import { chromium } from 'playwright';

const CDP = 'http://127.0.0.1:9222';

async function conectar() {
  const browser = await chromium.connectOverCDP(CDP);
  const contexts = browser.contexts();
  const pages = contexts.flatMap((c) => c.pages());
  return { browser, pages };
}

/** A aba do Play Console, ou a primeira que houver. */
function abaDoConsole(pages) {
  return pages.find((p) => p.url().includes('play.google.com/console')) ?? pages[0];
}

const [cmd, arg] = process.argv.slice(2);
const { browser, pages } = await conectar();

try {
  if (cmd === 'abas') {
    for (const p of pages) console.log(`${await p.title()}\n  ${p.url()}\n`);
  } else if (cmd === 'ver') {
    const p = abaDoConsole(pages);
    console.log('URL:', p.url());
    console.log('Titulo:', await p.title());
    const texto = await p.evaluate(() => document.body.innerText.slice(0, 4000));
    console.log('---\n' + texto);
  } else if (cmd === 'print') {
    const p = abaDoConsole(pages);
    await p.screenshot({ path: arg ?? 'console.png', fullPage: true });
    console.log('salvo em', arg ?? 'console.png');
  } else if (cmd === 'campos') {
    const p = abaDoConsole(pages);
    const campos = await p.evaluate(() =>
      [...document.querySelectorAll('input, textarea, select, button')]
        .filter((el) => el.offsetParent !== null)
        .map((el) => ({
          tag: el.tagName.toLowerCase(),
          tipo: el.type ?? '',
          nome: el.name || el.getAttribute('aria-label') || el.id || '',
          texto: (el.innerText ?? '').trim().slice(0, 60),
          valor: (el.value ?? '').slice(0, 60),
        })),
    );
    console.table(campos);
  } else if (cmd === 'ir') {
    const p = abaDoConsole(pages);
    await p.goto(arg, { waitUntil: 'domcontentloaded' });
    await p.waitForTimeout(4000);
    console.log('URL:', p.url());
    console.log('---');
    console.log(await p.evaluate(() => document.body.innerText.slice(0, 5000)));
  } else if (cmd === 'clicar') {
    const p = abaDoConsole(pages);
    const alvo = p.getByText(arg, { exact: false }).first();
    await alvo.click({ timeout: 10000 });
    await p.waitForTimeout(4000);
    console.log('URL:', p.url());
    console.log('---');
    console.log(await p.evaluate(() => document.body.innerText.slice(0, 5000)));
  } else {
    console.log('comandos: abas | ver | print <arquivo> | campos | ir <url> | clicar <texto>');
  }
} finally {
  // Nao fecha o browser: ele e do usuario, nao meu.
  await browser.close();
}
