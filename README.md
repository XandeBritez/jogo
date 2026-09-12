# Fodinha

App Android (Kotlin + Jetpack Compose) do jogo de cartas **Fodinha**: baralho de 40 cartas,
manilha pela carta virada, previsão de vazas e 10 vidas por jogador.

Roda em três modos: **contra bots**, **sala WiFi** na rede local e **Bluetooth**.

## Estrutura

```
:engine   Kotlin puro, sem dependência de Android. Todas as regras moram aqui.
:app      Android: UI Compose, transportes (local/WiFi/Bluetooth), bots no host.
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
- **9 cartas**: aposta antes de olhar; a mão aparece depois que todas as previsões saem.
  Como as cartas por rodada são limitadas a `39 / jogadores vivos`, a rodada de 9 cartas
  **só acontece com até 4 jogadores**. Com 5 o teto é 7 cartas, com 6 é 6 — aí essa regra
  simplesmente não chega a valer.

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
| Animacao Rapida | encurta as transicoes da carta |
| Seu nome | nome do assento, tambem editavel pelo menu |

Nada disso toca a Engine: regra e a mesma para todo mundo na mesa.

## Pausa da vaza

Quando o último jogador solta a carta, a mesa **não** é recolhida na hora: a engine entra em
`Phase.TRICK_REVEAL` com as cartas expostas e o vencedor já decidido (marcado com ✓ na tela).
Passados **5 segundos**, o host chama `Engine.closeTrick`, a mesa limpa e quem levou a vaza sai
na próxima. Ninguém joga durante a exibição — `legalPlays` fica vazio nessa fase.

A pausa vive no host, não na UI, então vale igual contra bot, no WiFi e no Bluetooth: todo
mundo na mesa vê as cartas pelo mesmo tempo.

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
- Ambos usam o mesmo enquadramento: **uma linha JSON por mensagem** (`SocketPipe`).

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

- ✅ `:engine:test` — 25 testes verdes, incluindo 1000 partidas completas (2 a 6 jogadores,
  200 sementes cada) checando: baralho nunca estoura, nenhuma carta duplicada, soma das
  previsões nunca iguala as cartas, vazas batem com as cartas, vidas só caem, jogo sempre
  termina com ranking completo, e `PlayerView` nunca vaza mão alheia.
- ✅ `:app:testDebugUnitTest` — 6 testes do host com transporte falso, exercitando o caminho
  remoto sem aparelho: entrada de cliente, `Welcome`, lobby, partida andando, redação da view
  enviada pela rede, avanço de rodada restrito ao dono e ida-e-volta da serialização.
- ✅ `:app:assembleDebug` — APK compila.
- ✅ **Rodado em aparelho real** (Xiaomi 22101320G / Redmi Note 12 Pro, via USB). Partida contra
  bot jogada de ponta a ponta, sem crash:
  - Rodada 1 (cega, 1 carta): carta do bot visível, a própria escondida, manilha 3 derivada da
    virada 2♥, botão "1" desabilitado no último a prever (a soma não pode dar 1), carta oculta
    jogada sozinha, A♣ venceu K♠ e o bot perdeu 1 vida por prever 0 e fazer 1.
  - Rodada 2 (normal, 2 cartas): mão visível, manilha K derivada da virada J♦, K♥ destacado
    como manilha, dealer rotacionado, previsões 0/1/2 todas liberadas para quem não é o último.
- ⚠️ **Menu novo e tela de opções**: compilam e passam nos testes, mas **não rodaram em aparelho** — nenhum device estava conectado. O relato de partida acima é do fluxo antigo de abertura; as regras e a mesa não mudaram, mas o menu, os diálogos de sala e as opções ainda precisam de um olhar num celular de verdade.
- ⚠️ **WiFi**: precisa de duas instâncias na mesma LAN.
- ⚠️ **Bluetooth**: não funciona em emulador. Exige dois aparelhos físicos pareados.

## Permissões

API 31+ pede `BLUETOOTH_SCAN` e `BLUETOOTH_CONNECT` em runtime — o manifest sozinho não basta.
`MainActivity` pede as duas na abertura (ou `ACCESS_FINE_LOCATION` em APIs antigas).
