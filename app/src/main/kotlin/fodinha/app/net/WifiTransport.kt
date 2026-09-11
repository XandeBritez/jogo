package fodinha.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_TYPE = "_fodinha._tcp."

/** Sala aberta na LAN: ServerSocket + anuncio por NSD/mDNS. */
class WifiHostTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val roomName: String,
) : HostTransport {

    private val _inbound = MutableSharedFlow<Pair<Int, ClientMsg>>(extraBufferCapacity = 64)
    override val inbound: Flow<Pair<Int, ClientMsg>> = _inbound.asSharedFlow()

    private val pipes = ConcurrentHashMap<Int, SocketPipe>()
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    var port: Int = 0
        private set

    override suspend fun start(onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val ss = ServerSocket(0)
            server = ss
            port = ss.localPort
            registerService(port)

            while (!ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                scope.launch { serveClient(socket, onJoin, onLeave) }
            }
        }
    }

    private suspend fun serveClient(socket: Socket, onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
        val pipe = SocketPipe(socket.getInputStream(), socket.getOutputStream(), socket)
        // A primeira linha tem que ser o Hello, senao nao sabemos que assento dar.
        val first = pipe.readLine()?.let { decodeClient(it) } as? ClientMsg.Hello
        if (first == null) {
            pipe.close()
            return
        }
        val id = onJoin(first.name)
        if (id < 0) {
            pipe.writeLine(HostMsg.Error("Sala cheia ou jogo em andamento").encode())
            pipe.close()
            return
        }
        pipes[id] = pipe
        _inbound.emit(id to first)

        while (true) {
            val line = pipe.readLine() ?: break
            val msg = decodeClient(line) ?: continue
            _inbound.emit(id to msg)
        }
        pipes.remove(id)
        pipe.close()
        onLeave(id)
    }

    override suspend fun sendTo(playerId: Int, msg: HostMsg) {
        pipes[playerId]?.writeLine(msg.encode())
    }

    private fun registerService(port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = manager
        val info = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(s: NsdServiceInfo?, code: Int) = Unit
            override fun onUnregistrationFailed(s: NsdServiceInfo?, code: Int) = Unit
            override fun onServiceRegistered(s: NsdServiceInfo?) = Unit
            override fun onServiceUnregistered(s: NsdServiceInfo?) = Unit
        }
        registration = listener
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun close() {
        registration?.let { r -> runCatching { nsd?.unregisterService(r) } }
        pipes.values.forEach { it.close() }
        pipes.clear()
        runCatching { server?.close() }
    }
}

/** Cliente que entra numa sala WiFi. */
class WifiClientTransport(
    private val host: InetAddress,
    private val port: Int,
    private val playerName: String,
) : ClientTransport {

    private val _inbound = MutableSharedFlow<HostMsg>(extraBufferCapacity = 64)
    override val inbound: Flow<HostMsg> = _inbound.asSharedFlow()

    private var pipe: SocketPipe? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val socket = Socket(host, port)
            val p = SocketPipe(socket.getInputStream(), socket.getOutputStream(), socket)
            pipe = p
            p.writeLine(ClientMsg.Hello(playerName).encode())
            while (true) {
                val line = p.readLine() ?: break
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

data class DiscoveredRoom(val name: String, val host: InetAddress, val port: Int)

/** Salas anunciadas na LAN. Emite a lista acumulada a cada mudanca. */
fun discoverRooms(context: Context): Flow<List<DiscoveredRoom>> = callbackFlow {
    val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val found = mutableMapOf<String, DiscoveredRoom>()

    // Callbacks do NSD chegam numa thread de binder, nao nesta. Tudo que e
    // estado compartilhado passa por este lock, senao a fila de resolve trava
    // de vez com `resolving` preso em true.
    val lock = Any()
    val resolveQueue = ArrayDeque<NsdServiceInfo>()
    var resolving = false

    fun resolveNext() {
        val next = synchronized(lock) {
            if (resolving) return
            val n = resolveQueue.removeFirstOrNull() ?: return
            resolving = true
            n
        }
        manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, code: Int) {
                synchronized(lock) { resolving = false }
                resolveNext()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val address = info.host
                if (address != null) {
                    val snapshot = synchronized(lock) {
                        found[info.serviceName] = DiscoveredRoom(info.serviceName, address, info.port)
                        found.values.toList()
                    }
                    trySend(snapshot)
                }
                synchronized(lock) { resolving = false }
                resolveNext()
            }
        })
    }

    val listener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(type: String?, code: Int) = Unit
        override fun onStopDiscoveryFailed(type: String?, code: Int) = Unit
        override fun onDiscoveryStarted(type: String?) = Unit
        override fun onDiscoveryStopped(type: String?) = Unit

        override fun onServiceFound(info: NsdServiceInfo) {
            if (info.serviceType.contains("fodinha")) {
                synchronized(lock) { resolveQueue.addLast(info) }
                resolveNext()
            }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            val snapshot = synchronized(lock) {
                found.remove(info.serviceName)
                found.values.toList()
            }
            trySend(snapshot)
        }
    }

    manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
}
