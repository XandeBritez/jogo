package fodinha.app.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** UUID fixo da mesa. Host e cliente precisam bater. */
val FODINHA_UUID: UUID = UUID.fromString("6f0d1a7a-f0d1-4a11-9c3e-f0d1a0000001")
private const val SDP_NAME = "Fodinha"

fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

/** Host RFCOMM. Aceita conexoes ate a mesa encher. */
@SuppressLint("MissingPermission")
class BluetoothHostTransport(
    private val adapter: BluetoothAdapter,
    private val scope: CoroutineScope,
) : HostTransport {

    private val _inbound = MutableSharedFlow<Pair<Int, ClientMsg>>(extraBufferCapacity = 64)
    override val inbound: Flow<Pair<Int, ClientMsg>> = _inbound.asSharedFlow()

    private val pipes = ConcurrentHashMap<Int, SocketPipe>()
    private var server: BluetoothServerSocket? = null

    override suspend fun start(onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val ss = adapter.listenUsingRfcommWithServiceRecord(SDP_NAME, FODINHA_UUID)
            server = ss
            while (true) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                scope.launch { serve(socket, onJoin, onLeave) }
            }
        }
    }

    private suspend fun serve(socket: BluetoothSocket, onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
        val pipe = SocketPipe(socket.inputStream, socket.outputStream, socket)
        val hello = pipe.readLine()?.let { decodeClient(it) } as? ClientMsg.Hello
        if (hello == null) {
            pipe.close()
            return
        }
        val id = onJoin(hello.name)
        if (id < 0) {
            pipe.writeLine(HostMsg.Error("Sala cheia ou jogo em andamento").encode())
            pipe.close()
            return
        }
        pipes[id] = pipe
        _inbound.emit(id to hello)

        while (true) {
            val line = pipe.readLine() ?: break
            decodeClient(line)?.let { _inbound.emit(id to it) }
        }
        pipes.remove(id)
        pipe.close()
        onLeave(id)
    }

    override suspend fun sendTo(playerId: Int, msg: HostMsg) {
        pipes[playerId]?.writeLine(msg.encode())
    }

    override fun close() {
        pipes.values.forEach { it.close() }
        pipes.clear()
        runCatching { server?.close() }
    }
}

/** Cliente RFCOMM. O aparelho precisa estar pareado com o host. */
@SuppressLint("MissingPermission")
class BluetoothClientTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val playerName: String,
) : ClientTransport {

    private val _inbound = MutableSharedFlow<HostMsg>(extraBufferCapacity = 64)
    override val inbound: Flow<HostMsg> = _inbound.asSharedFlow()

    private var pipe: SocketPipe? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            runCatching { adapter.cancelDiscovery() }
            val socket = device.createRfcommSocketToServiceRecord(FODINHA_UUID)
            socket.connect()
            val p = SocketPipe(socket.inputStream, socket.outputStream, socket)
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
