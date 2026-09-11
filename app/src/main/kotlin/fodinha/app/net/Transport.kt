package fodinha.app.net

import kotlinx.coroutines.flow.Flow

/**
 * Costura entre a mesa e o mundo. Tres implementacoes:
 * local (bots in-process), WiFi (TCP na LAN) e Bluetooth (RFCOMM).
 * A UI so conhece esta interface.
 */
interface ClientTransport {
    /** Mensagens vindas do host. */
    val inbound: Flow<HostMsg>

    suspend fun connect()
    suspend fun send(msg: ClientMsg)
    fun close()
}

/**
 * Lado servidor. O host roda a Engine e manda uma PlayerView redigida
 * para cada assento: mao alheia nunca atravessa o socket.
 */
interface HostTransport {
    /** (playerId, mensagem) recebidos dos clientes remotos. */
    val inbound: Flow<Pair<Int, ClientMsg>>

    /**
     * Chamado quando um cliente remoto entra; devolve o id de assento atribuido
     * (-1 se nao ha vaga). O transporte SO pode registrar o canal de saida depois
     * de ter o id, entao `onJoin` nao manda nada: quem responde e o host, ao
     * receber o Hello pelo `inbound`.
     */
    suspend fun start(onJoin: suspend (name: String) -> Int, onLeave: suspend (Int) -> Unit)

    suspend fun sendTo(playerId: Int, msg: HostMsg)
    fun close()
}

enum class TransportKind { LOCAL, WIFI, BLUETOOTH }
