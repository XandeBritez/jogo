# Relay na VPS

Precisa so de Java 17+ e uma porta TCP aberta (padrão 5555).

```bash
# no PC: gera o jar
./gradlew.bat :relay:fatJar
# -> relay/build/libs/fodinha-relay.jar

# manda pra VPS
scp relay/build/libs/fodinha-relay.jar relay/deploy/fodinha-relay.service root@SUA_VPS:/tmp/

# na VPS (Ubuntu/Debian)
apt install -y openjdk-17-jre-headless
useradd -r -s /usr/sbin/nologin fodinha
mkdir -p /opt/fodinha && mv /tmp/fodinha-relay.jar /opt/fodinha/ && chown -R fodinha /opt/fodinha
mv /tmp/fodinha-relay.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now fodinha-relay
ufw allow 5555/tcp        # jogo
ufw allow 5555/udp        # voz (mesma porta, UDP)
journalctl -u fodinha-relay -f   # "fodinha relay na porta 5555"
```

Teste rápido de fora: `nc SUA_VPS 5555`, digita `HOST`, tem que voltar `CODE XXXXX`.

No app: Opções → Servidor de internet → `SUA_VPS:5555` (todo mundo da mesa usa o mesmo).
Pra não precisar digitar, coloque o endereço em `DEFAULT_RELAY_SERVER`
(`app/src/main/kotlin/fodinha/app/ui/Settings.kt`) antes de gerar o APK.

Atualizar: troca o jar e `systemctl restart fodinha-relay` (derruba as salas abertas).

Sem TLS: o tráfego é o JSON do jogo (cartas, previsões, nomes). Nada sensível.

**Atenção (Play Store):** `docs/privacidade.md` diz que o desenvolvedor não opera relay público
e que sem endereço configurado o app não acessa a internet. Se você fixar sua VPS em
`DEFAULT_RELAY_SERVER` na versão publicada, isso deixa de ser verdade: atualize a política
("o app conecta ao servidor X, operado pelo desenvolvedor, sem criptografia") e a
seção Segurança dos dados no Console. Senão a ficha bate com o app e está tudo certo.
