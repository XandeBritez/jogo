package fodinha.app.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/*
 * Sala pela internet, via relay (modulo :relay, rodando numa VPS).
 *
 * Celular nao aceita conexao de fora (NAT da operadora), entao o host tambem
 * DISCA para o relay. Uma conexao TCP so carrega todos os clientes: cada
 * linha vai embrulhada com o numero da conexao (`conn`) no relay.
 *
 * O jogo em si nao muda: o mesmo JSON de HostMsg/ClientMsg atravessa, e o
 * GameHost nao sabe se esta falando com WiFi, Bluetooth ou relay.
 *
 * Protocolo de linhas: veja o cabecalho de RelayServer.kt.
 */

/** "host:porta" digitado nas opcoes. Porta padrao 5555. */
fun parseRelayAddress(text: String): Pair<String, Int>? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val colon = t.lastIndexOf(':')
    if (colon < 0) return t to 5555
    val host = t.substring(0, colon).trim()
    val port = t.substring(colon + 1).toIntOrNull() ?: return null
    if (host.isEmpty() || port !in 1..65535) return null
    return host to port
}

private const val CONNECT_TIMEOUT_MS = 8_000

private fun dial(host: String, port: Int): SocketPipe {
    val socket = Socket()
    socket.tcpNoDelay = true
    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
    return SocketPipe(socket.getInputStream(), socket.getOutputStream(), socket)
}

/** Lado de quem abre a sala. O codigo chega em `code` assim que o relay responde. */
class RelayHostTransport(
    private val relayHost: String,
    private val relayPort: Int,
) : HostTransport {

    private val _inbound = MutableSharedFlow<Pair<Int, ClientMsg>>(extraBufferCapacity = 64)
    override val inbound: Flow<Pair<Int, ClientMsg>> = _inbound.asSharedFlow()

    /** Codigo da sala, ou a falha (string de erro) se o relay nao respondeu. */
    val code = CompletableDeferred<Result<String>>()

    private var pipe: SocketPipe? = null

    /**
     * conn do relay <-> assento do jogo. Sao espacos diferentes: quem cai e
     * volta com o mesmo nome ganha um conn novo e o MESMO assento (o GameHost
     * reaproveita), entao o mapa tem que ser reescrito, nunca so acrescido.
     */
    private val seatByConn = ConcurrentHashMap<Int, Int>()
    private val connBySeat = ConcurrentHashMap<Int, Int>()

    /** Completa quando a conexao com o relay termina depois de a sala existir. */
    val ended = CompletableDeferred<Unit>()

    override suspend fun start(onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val p = runCatching { dial(relayHost, relayPort) }.getOrElse { e ->
                code.complete(Result.failure(e))
                return@withContext
            }
            pipe = p
            p.writeLine("HOST")
            val first = p.readLine()
            if (first == null || !first.startsWith("CODE ")) {
                code.complete(Result.failure(IllegalStateException(first?.removePrefix("ERR ") ?: "relay fechou a conexao")))
                p.close()
                return@withContext
            }
            code.complete(Result.success(first.removePrefix("CODE ").trim()))

            while (true) {
                val line = p.readLine() ?: break
                when {
                    line.startsWith("FROM ") -> {
                        val (conn, payload) = splitIdAndRest(line.removePrefix("FROM ")) ?: continue
                        val msg = decodeClient(payload) ?: continue
                        val seat = seatByConn[conn]
                        if (seat != null) {
                            _inbound.emit(seat to msg)
                        } else if (msg is ClientMsg.Hello) {
                            // Primeira linha de um cliente novo: reserva o assento.
                            val id = onJoin(msg.name)
                            if (id < 0) {
                                p.writeLine("TO $conn " + HostMsg.Error("Sala cheia ou jogo em andamento").encode())
                                p.writeLine("KICK $conn")
                            } else {
                                connBySeat[id]?.let { old -> seatByConn.remove(old) }
                                seatByConn[conn] = id
                                connBySeat[id] = conn
                                _inbound.emit(id to msg)
                            }
                        }
                        // Qualquer outra coisa antes do Hello: ignora.
                    }
                    line == "PING" -> p.writeLine("PONG")
                    line.startsWith("LEFT ") -> {
                        val conn = line.removePrefix("LEFT ").trim().toIntOrNull() ?: continue
                        val seat = seatByConn.remove(conn) ?: continue
                        if (connBySeat[seat] == conn) connBySeat.remove(seat)
                        onLeave(seat)
                    }
                }
            }
            // Relay caiu: todo mundo que estava nele caiu junto.
            val seats = seatByConn.values.toList()
            seatByConn.clear()
            connBySeat.clear()
            seats.forEach { onLeave(it) }
            ended.complete(Unit)
        }
    }

    override suspend fun sendTo(playerId: Int, msg: HostMsg) {
        val conn = connBySeat[playerId] ?: return
        pipe?.writeLine("TO $conn " + msg.encode())
    }

    override fun close() {
        pipe?.close()
        pipe = null
        if (!code.isCompleted) code.complete(Result.failure(IllegalStateException("fechado")))
    }
}

/** Lado de quem entra com o codigo. */
class RelayClientTransport(
    private val relayHost: String,
    private val relayPort: Int,
    private val roomCode: String,
    private val playerName: String,
) : ClientTransport {

    private val _inbound = MutableSharedFlow<HostMsg>(extraBufferCapacity = 64)
    override val inbound: Flow<HostMsg> = _inbound.asSharedFlow()

    private var pipe: SocketPipe? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val p = dial(relayHost, relayPort)
            pipe = p
            p.writeLine("JOIN ${roomCode.trim().uppercase()}")
            val answer = p.readLine() ?: throw IllegalStateException("relay fechou a conexao")
            if (answer != "OK") throw IllegalStateException(answer.removePrefix("ERR "))
            p.writeLine(ClientMsg.Hello(playerName).encode())
            while (true) {
                val line = p.readLine() ?: break
                if (line == "PING") {
                    p.writeLine("PONG")
                    continue
                }
                if (line == "END") {
                    _inbound.emit(HostMsg.Error("O host saiu da sala"))
                    break
                }
                decodeHost(line)?.let { _inbound.emit(it) }
            }
        }
    }

    override suspend fun send(msg: ClientMsg) {
        pipe?.writeLine(msg.encode())
    }

    override fun close() {
        pipe?.close()
        pipe = null
    }
}

/** "12 {...}" -> (12, "{...}"). */
private fun splitIdAndRest(s: String): Pair<Int, String>? {
    val space = s.indexOf(' ')
    if (space <= 0) return null
    val id = s.substring(0, space).toIntOrNull() ?: return null
    return id to s.substring(space + 1)
}
