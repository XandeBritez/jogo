package fodinha.relay

import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/*
 * Relay de salas pela internet.
 *
 * Nao sabe jogar: so encaminha linhas. O celular de quem abre a sala continua
 * sendo o host autoritativo (roda a Engine); a diferenca e que ele DISCA para
 * ca em vez de abrir porta, porque celular atras de NAT nao aceita conexao.
 *
 * Protocolo, uma linha de texto por mensagem (\n), UTF-8:
 *
 *   host -> relay   HOST                  primeira linha; abre uma sala
 *   relay -> host   CODE <codigo>         sala aberta, este e o codigo
 *   relay -> host   FROM <conn> <json>    linha vinda de um cliente
 *   relay -> host   LEFT <conn>           cliente caiu
 *   host -> relay   TO <conn> <json>      linha para um cliente
 *   host -> relay   KICK <conn>           derruba um cliente
 *
 *   cliente -> relay  JOIN <codigo>       primeira linha; entra na sala
 *   relay -> cliente  OK | ERR <motivo>
 *   cliente -> relay  <json>              vai ao host embrulhado em FROM
 *   relay -> cliente  <json>              o que o host mandou em TO
 *   relay -> cliente  END                 host saiu; a sala acabou
 *
 *   relay -> todos    PING                a cada 30 s. Responda PONG: quem
 *   todos -> relay    PONG                fica 90 s em silencio absoluto cai.
 *
 * `<conn>` e o numero da conexao no relay, nada a ver com o assento do jogo:
 * quem cai e volta ganha outro <conn> e, se o host quiser, o mesmo assento.
 */

const val MAX_ROOMS = 200
const val MAX_CLIENTS = 5
const val MAX_LINE = 64 * 1024
const val PING_MS = 30_000L
const val IDLE_TIMEOUT_MS = 90_000

/** Sem 0/O, 1/I: codigo ditado por telefone nao pode ter par ambiguo. */
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val CODE_LENGTH = 5
private const val NL = 10
private const val CR = 13.toChar()

class RelayServer(
    port: Int,
    private val pingMs: Long = PING_MS,
    private val idleTimeoutMs: Int = IDLE_TIMEOUT_MS,
) {
    private val server = ServerSocket(port)
    private val rooms = ConcurrentHashMap<String, Room>()
    private val connSeq = AtomicInteger(0)
    private val random = SecureRandom()

    @Volatile private var running = true

    val port: Int get() = server.localPort
    val roomCount: Int get() = rooms.size

    fun hasRoom(code: String): Boolean = rooms.containsKey(code)

    private class Conn(val socket: Socket) {
        val reader: BufferedReader = socket.getInputStream().bufferedReader()
        private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()

        /** Escrita falhou = o outro lado ja foi; quem chama decide o que fazer. */
        fun write(line: String): Boolean = synchronized(writer) {
            runCatching {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }.isSuccess
        }

        fun close() = runCatching { socket.close() }
    }

    private class Room(val code: String, val host: Conn) {
        val clients = ConcurrentHashMap<Int, Conn>()
    }

    fun start() {
        Thread({ acceptLoop() }, "relay-accept").apply { isDaemon = true }.start()
        Thread({ pingLoop() }, "relay-ping").apply { isDaemon = true }.start()
    }

    private fun acceptLoop() {
        while (running) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            socket.soTimeout = idleTimeoutMs
            socket.tcpNoDelay = true
            Thread({ serve(socket) }, "relay-conn").apply { isDaemon = true }.start()
        }
    }

    private fun pingLoop() {
        while (running) {
            Thread.sleep(pingMs)
            for (room in rooms.values) {
                room.host.write("PING")
                room.clients.values.forEach { it.write("PING") }
            }
        }
    }

    private fun serve(socket: Socket) {
        val conn = Conn(socket)
        try {
            val first = conn.readLine() ?: return
            when {
                first == "HOST" -> serveHost(conn)
                first.startsWith("JOIN ") -> serveClient(conn, first.removePrefix("JOIN ").trim().uppercase())
                else -> conn.write("ERR primeira linha tem que ser HOST ou JOIN <codigo>")
            }
        } finally {
            conn.close()
        }
    }

    private fun serveHost(host: Conn) {
        if (rooms.size >= MAX_ROOMS) {
            host.write("ERR relay lotado")
            return
        }
        val room = openRoom(host)
        host.write("CODE ${room.code}")
        try {
            while (true) {
                val line = host.readLine() ?: break
                when {
                    line.startsWith("TO ") -> {
                        val (id, payload) = splitIdAndRest(line.removePrefix("TO ")) ?: continue
                        val client = room.clients[id] ?: continue
                        if (!client.write(payload)) dropClient(room, id)
                    }
                    line.startsWith("KICK ") -> {
                        line.removePrefix("KICK ").trim().toIntOrNull()?.let { dropClient(room, it) }
                    }
                    // PONG, lixo: ignora. Ler ja renovou o prazo de inatividade.
                }
            }
        } finally {
            rooms.remove(room.code)
            for (c in room.clients.values) {
                c.write("END")
                c.close()
            }
            room.clients.clear()
        }
    }

    private fun serveClient(conn: Conn, code: String) {
        val room = rooms[code]
        if (room == null) {
            conn.write("ERR sala nao existe")
            return
        }
        if (room.clients.size >= MAX_CLIENTS) {
            conn.write("ERR sala cheia")
            return
        }
        val id = connSeq.incrementAndGet()
        room.clients[id] = conn
        conn.write("OK")
        try {
            while (true) {
                val line = conn.readLine() ?: break
                if (line == "PONG") continue
                if (!room.host.write("FROM $id $line")) break
            }
        } finally {
            if (room.clients.remove(id) != null) room.host.write("LEFT $id")
        }
    }

    /** So fecha o socket: quem tira da sala e avisa LEFT e o serveClient dele. */
    private fun dropClient(room: Room, id: Int) {
        room.clients[id]?.close()
    }

    private fun openRoom(host: Conn): Room {
        while (true) {
            val code = buildString { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
            val room = Room(code, host)
            if (rooms.putIfAbsent(code, room) == null) return room
        }
    }

    /** "12 {...}" -> (12, "{...}"). Null se nao tem id ou nao tem resto. */
    private fun splitIdAndRest(s: String): Pair<Int, String>? {
        val space = s.indexOf(' ')
        if (space <= 0) return null
        val id = s.substring(0, space).toIntOrNull() ?: return null
        return id to s.substring(space + 1)
    }

    /**
     * Le uma linha com teto de tamanho. Linha gigante e ataque ou bug: derruba.
     * Timeout de leitura tambem derruba - e o prazo de inatividade.
     */
    private fun Conn.readLine(): String? {
        val sb = StringBuilder()
        return try {
            while (true) {
                val ch = reader.read()
                if (ch < 0) return if (sb.isEmpty()) null else sb.toString()
                if (ch == NL) return sb.toString().trimEnd(CR)
                sb.append(ch.toChar())
                if (sb.length > MAX_LINE) return null
            }
            @Suppress("UNREACHABLE_CODE") null
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        running = false
        runCatching { server.close() }
        for (room in rooms.values) {
            room.host.close()
            room.clients.values.forEach { it.close() }
        }
        rooms.clear()
    }
}
