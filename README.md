# Fodinha

App Android (Kotlin + Jetpack Compose) do jogo de cartas **Fodinha**: baralho de 40 cartas,
manilha pela carta virada, previsão de vazas e 10 vidas por jogador.

Roda em três modos: **contra bots**, **sala WiFi** na rede local e **Bluetooth**.

## Estrutura

```
:engine   Kotlin puro, sem dependência de Android. Todas as regras moram aqui.
:app      Android: UI Compose, transportes (local/WiFi/Bluetooth/internet), bots no host.
:relay    JVM puro. Servidor burro para a sala pela internet: roda numa VPS, so repassa linhas.
```

O `:engine` é um redutor imutável: `Engine.reduce(state, action) -> state`, determinístico
dado o seed. É o único módulo com testes unitários, e é onde qualquer dúvida de regra se resolve.

## Regras implementadas

| Regra | Onde |
|---|---|
| Hierarquia `3 > 2 > A > K > J > Q > 7 > 6 > 5 > 4` | ordinal de `Rank` |
| Manilha = carta seguinte à virada (3 → 4) | `Rules.manilhaRank` |
| Entre manilhas: ♣ > ♥ > ♠ > ♦ | ordinal de `Suit` |
| Naipe **não** desempata carta normal; empate = primeira carta jogada | `Rules.trickWinnerIndex` |
| Sem obrigação de seguir naipe | `Engine.legalPlays` |
| Soma das previsões ≠ nº de cartas (trava só o último) | `Engine.legalBids` |
| Progressão 1→9→1, ciclo infinito | `Rules.scheduledCards` |
| Cartas por rodada = `min(agendado, 39 / jogadores vivos)` | `Rules.cardsThisRound` |
| `\|previsão − vazas\|` = vidas perdidas | `Engine.scoreRound` |
| Dealer rotaciona; primeiro a apostar é à esquerda dele | `Engine.nextRound` |

### Rodadas às cegas

- **1 carta**: você **não vê a sua** carta, mas **vê a de todos os outros** (carta virada para
  fora, como na mesa). Quem manda a carta é você — toca no verso ou no botão "Jogar minha
  carta". O cliente nunca recebe qual é a carta: a ação `GameAction.PlayBlind` só diz *quem*
  jogou, e a engine resolve para a única carta da mão.
- **9 cartas**: cega do começo ao fim. Você aposta sem olhar e **continua sem ver a mão na
  hora de jogar**: escolhe uma *posição* (1 a 9) e só descobre que carta era quando ela cai
  na mesa. A ação é `GameAction.PlayBlindAt`, que carrega o índice e nunca a carta; o
  `PlayerView` manda `myHand` vazio e `legalPlays` vazio a rodada inteira, enquanto a Engine
  segue validando contra a mão de verdade. O bot joga carta sorteada nessa rodada — ele vê a
  própria mão porque roda dentro do host, e escolher por força seria trapaça.
  Como as cartas por rodada são limitadas a `39 / jogadores vivos`, a rodada de 9 cartas
  **só acontece com até 4 jogadores**. Com 5 o teto é 7 cartas, com 6 é 6 — aí essa regra
  simplesmente não chega a valer.

A mão aparece em cinco cartas por linha, quebrando para baixo: com nove na mesma linha
só dava para ver as primeiras. A mesa quebra do mesmo jeito, em no máximo quatro cartas
por linha (`cartasPorLinha`, em `ui/PlayerLayout.kt`): com seis jogadores a sexta carta
ficava fora da tela.

A redação é feita em `GameState.viewFor(playerId)`, que devolve um `PlayerView`. É esse o objeto
que trafega na rede: **mão alheia nunca atravessa o socket**.

## Menu e opcoes

O menu de abertura (`ui/HomeScreen.kt`) e uma mesa verde com o nome escrito em cartas, a caixa
"escolha o modo" (quantos bots entram), o bloco **Jogar** e os atalhos **Internet**, **Bluetooth**,
**Ajuda**, **Sobre** e **Opcoes**. Sala WiFi/Bluetooth (nome da sala, procurar, lista de salas e
de aparelhos pareados) mora nos dialogos dos dois atalhos, nao mais inline na tela.

`ui/OptionsScreen.kt` e a lista escura de preferencias, gravada em `SharedPreferences`
(`ui/Settings.kt`) e distribuida por `LocalGameSettings`:

| Opcao | Efeito |
|---|---|
| Baralho | desenho do verso (`DeckBackArt`), 5 opcoes |
| Cor de fundo das cartas e baloes | paleta de 20; a tinta do naipe clareia sozinha em fundo escuro |
| Tamanho do Texto | escala 1.0 / 1.18 / 1.35 aplicada por `scaled()` |
| Tema | claro, escuro ou seguir o modo escuro do aparelho (padrão) |
| Alto contraste | fecha o fundo, tira a transparência do texto secundário, engrossa as bordas e **devolve a carta ao branco**, ignorando a cor escolhida |
| Animacao Rapida | encurta as transicoes da carta |
| Seu nome | nome do assento, tambem editavel pelo menu |

As cores das três telas saem de uma paleta só (`MenuPalette`, em `ui/MenuStyle.kt`): há uma
tabela clara e uma escura, e a versão de alto contraste é **derivada** da que estiver valendo,
em vez de uma quarta tabela para manter em sincronia. Os nomes antigos (`Slate`, `Ink`,
`MenuGold`…) continuam valendo como atalhos que leem a paleta em vigor, então as telas não
precisaram de uma troca de cor linha a linha.

A tela de opções é escura nos dois temas — é a identidade dela; o que muda é o quanto fecha.
As cartas também seguem claras no tema escuro: baralho de verdade é branco.

Nada disso toca a Engine: regra e a mesma para todo mundo na mesa.

## Pausa da vaza e virada de rodada

Quando o último jogador solta a carta, a mesa **não** é recolhida na hora: a engine entra em
`Phase.TRICK_REVEAL` com as cartas expostas e o vencedor já decidido (marcado com ✓ na tela).
Passados **5 segundos**, o host chama `Engine.closeTrick`, a mesa limpa e quem levou a vaza sai
na próxima. Ninguém joga durante a exibição — `legalPlays` fica vazio nessa fase.

Fechada a rodada, o resumo (previu/fez/vidas) fica **5 segundos** na tela e a próxima rodada
começa **sozinha**. Ninguém precisa apertar nada: antes só o dono da sala avançava, e se ele
largasse o celular a mesa inteira ficava presa. `ClientMsg.NextRound` deixou de existir.

As duas pausas vivem no host, não na UI, então valem igual contra bot, no WiFi e no Bluetooth:
todo mundo na mesa vê a mesma coisa pelo mesmo tempo. O contador na tela (`Proxima rodada em
Ns`) só mostra a espera — quem vira a rodada é o host.

## Relógio do turno

Turno de humano tem prazo, contado pelo host (vale igual em bot, WiFi e Bluetooth):

- **60s para prever**. Estourou, o host sorteia uma previsão entre as legais.
- **40s para jogar**. Estourou, o host sorteia uma carta da mão — na rodada cega, manda a única.

Bot não tem prazo, responde sozinho. O host manda em cada `PlayerView` quantos segundos
restavam no instante do envio, e o aparelho conta para baixo a partir daí — assim o relógio
não depende de os dois celulares estarem com a hora sincronizada.

## Rede

Host-autoritativo. O host roda a Engine, dirige os bots e transmite um `PlayerView` por assento.
Cliente só manda ação e renderiza o que recebe.

- **WiFi**: `ServerSocket` TCP + anúncio/descoberta por NSD (mDNS), serviço `_fodinha._tcp.`
- **Bluetooth**: RFCOMM com UUID fixo, host = server. Aparelhos precisam estar pareados.
- **Internet**: celular não aceita conexão de fora (NAT), então o host também **disca** para o
  relay (`:relay`, `java -jar` numa VPS). Uma conexão TCP do host carrega todos os clientes;
  cada linha vai embrulhada com o número da conexão no relay (`FROM n {...}` / `TO n {...}`).
  Entrada por **código de 5 letras** que o relay sorteia. Sem voz (PCM cru não serve na
  internet). Endereço do relay em Opções; deploy em `relay/deploy/INSTALAR.md`.
- Todos usam o mesmo enquadramento: **uma linha JSON por mensagem** (`SocketPipe`).

Bots rodam dentro do host como jogadores normais — a Engine não distingue bot de humano.

## Build

Precisa de **JDK 17** (o AGP não roda em JDK 8) e do Android SDK apontado em `local.properties`,
que não vai para o repositório porque o caminho muda em cada máquina:

```properties
sdk.dir=C:/caminho/para/Android/Sdk
```

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"

.\gradlew.bat :engine:test          # 25 testes de regra + 1000 partidas simuladas
.\gradlew.bat :app:testDebugUnitTest # 6 testes do host e do protocolo
.\gradlew.bat :app:assembleDebug  # APK em app\build\outputs\apk\debug\
```

Instalar num aparelho:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

## Estado da verificação

- ✅ `:engine:test` — 29 testes verdes (4 novos cobrem a rodada cega de 9: mão e `legalPlays`
  vazios também na fase de jogada, `PlayBlindAt` tirando a carta da posição pedida, e recusa
  de posição inválida ou de rodada que não seja a de 9), incluindo 1000 partidas completas (2 a 6 jogadores,
  200 sementes cada) checando: baralho nunca estoura, nenhuma carta duplicada, soma das
  previsões nunca iguala as cartas, vazas batem com as cartas, vidas só caem, jogo sempre
  termina com ranking completo, e `PlayerView` nunca vaza mão alheia.
- ✅ `:app:testDebugUnitTest` — 14 testes do host com transporte falso, exercitando o caminho
  remoto sem aparelho: entrada de cliente, `Welcome`, lobby, partida andando, redação da view
  enviada pela rede, rodada emendando sozinha depois do resumo, ida-e-volta da serialização e o
  plano de controle da voz (porta no `Welcome`, entrar/mic/sair refletidos na lista, queda do
  socket tirando da voz, bot não entra).
- ⚠️ **Áudio do chat de voz não rodou em aparelho** — o plano de controle está coberto por
  teste, mas captura, relay UDP e mistura só se provam com dois celulares na mesma rede, e
  não havia nenhum plugado na hora. Eco entre dois aparelhos na mesma mesa é o risco a
  observar: o cancelador do aparelho ajuda, mas não é garantia.
- ✅ `:relay:test` — 7 testes do relay (abrir sala, entrar, código errado, sala cheia, host sair, expulsar, keepalive PING/PONG).
- ✅ `RelayTransportTest` — 7 testes ponta a ponta na JVM: `GameHost` + transportes de relay reais
  + `RelayServer` real em 127.0.0.1 (entrar, dois clientes, cair e voltar no mesmo assento, host fechar, relay fora do ar).
- ⚠️ **Sala pela internet não rodou em aparelho** — nenhum device conectado. Falta: subir o
  relay na VPS, configurar o endereço em Opções e testar com dois celulares em redes diferentes.
- ✅ `:app:assembleDebug` — APK compila.
- ✅ **Rodado em aparelho real** (Xiaomi 22101320G / Redmi Note 12 Pro, via USB). Partida contra
  bot jogada de ponta a ponta, sem crash:
  - Rodada 1 (cega, 1 carta): carta do bot visível, a própria escondida, manilha 3 derivada da
    virada 2♥, botão "1" desabilitado no último a prever (a soma não pode dar 1), carta oculta
    jogada sozinha, A♣ venceu K♠ e o bot perdeu 1 vida por prever 0 e fazer 1.
  - Rodada 2 (normal, 2 cartas): mão visível, manilha K derivada da virada J♦, K♥ destacado
    como manilha, dealer rotacionado, previsões 0/1/2 todas liberadas para quem não é o último.
- ⚠️ **Menu novo e tela de opções**: compilam e passam nos testes, mas **não rodaram em aparelho** — nenhum device estava conectado. O relato de partida acima é do fluxo antigo de abertura; as regras e a mesa não mudaram, mas o menu, os diálogos de sala e as opções ainda precisam de um olhar num celular de verdade.
- ✅ **WiFi e Bluetooth** — rodados entre dois aparelhos físicos (Xiaomi 22101320G e Samsung
  SM-A146M): sala aberta num, cliente entrando pelo outro, partida andando nos dois modos.

## Chat de voz (só sala WiFi)

Quem abre a sala WiFi abre junto um **relay UDP** (`net/Voice.kt`, `VoiceRelay`): cada aparelho
manda um fluxo só, para o host, e o host devolve o que recebeu a todos os outros. Não há
mistura no host — quem mistura é cada ouvinte (`VoiceChat`), somando o PCM de cada vizinho.
Assim o host não vira gargalo de CPU, e **silenciar alguém é decisão local**: é o meu ouvido,
não precisa passar pela rede.

- Áudio: PCM 16 bits, mono, 16 kHz, quadros de 20 ms, por UDP num canal próprio. Fora do
  socket JSON de propósito: jogada atrasada por causa de áudio seria inaceitável, áudio
  perdido é só um estalo. PCM cru porque 32 kB/s por falante é trocado numa LAN e um codec
  puxaria dependência nativa.
- Controle (entrar, sair, mic aberto/fechado) vai pelo protocolo JSON normal
  (`ClientMsg.VoiceJoin/VoiceLeave/VoiceMic`), para chegar em ordem e aparecer na lista de
  assentos de todo mundo (`LobbySeat.inVoice`, `micMuted`). A porta do relay viaja no
  `Welcome`; zero significa sala sem voz.
- Captura com `VOICE_COMMUNICATION` + cancelador de eco e supressor de ruído do aparelho,
  em `MODE_IN_COMMUNICATION` com viva-voz: celular na mesa, não no ouvido. Quadros abaixo
  de um limiar de energia não são enviados (silêncio não viaja).
- Na tela: barra **Entrar na voz / Mic aberto / Sair da voz** na sala e na mesa; microfone ao
  lado de cada jogador (dourado falando, vermelho fechou o próprio, riscado quando **você**
  o silenciou); **tocar no jogador silencia só no seu aparelho**.
- `RECORD_AUDIO` é pedida no toque em "Entrar na voz", não na abertura do app.
- Contra bots e por Bluetooth a barra **some** — não fica desabilitada. RFCOMM já carrega o
  jogo; áudio ali disputaria o mesmo canal serial.

Quem cai do socket sai da voz na hora (sem microfone fantasma na lista); no relay o
endereço expira sozinho depois de 5 s sem pacote.

## Publicar na Play Store

- `applicationId`: `com.xandebritez.fodinha` (não muda depois de publicado).
- Chave de assinatura em `keystore/` (ignorada pelo git — guarde backup, sem ela
  não há atualização). `keystore/keystore.properties` alimenta o `signingConfig`.
- `./gradlew.bat :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- Ficha: `store/icon-512.png`, `store/feature-1024x500.png`; política de
  privacidade em `docs/privacidade.md` (publicar via GitHub Pages).

## Permissões

API 31+ pede `BLUETOOTH_SCAN` e `BLUETOOTH_CONNECT` em runtime — o manifest sozinho não basta.
`MainActivity` pede as duas na abertura (ou `ACCESS_FINE_LOCATION` em APIs antigas).
