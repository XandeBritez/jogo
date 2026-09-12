package fodinha.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Uma linha JSON por mensagem sobre qualquer par de streams.
 * WiFi (TCP) e Bluetooth (RFCOMM) usam exatamente o mesmo enquadramento.
 */
class SocketPipe(
    input: InputStream,
    output: OutputStream,
    private val closer: Closeable?,
) : Closeable {

    private val reader: BufferedReader = input.bufferedReader()
    private val writer: BufferedWriter = output.bufferedWriter()

    /** Socket ja fechado (o outro lado saiu, ou eu sai) engole a escrita. */
    suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        synchronized(writer) {
            runCatching {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
        Unit
    }

    /** Bloqueia ate chegar uma linha. null = outro lado fechou. */
    suspend fun readLine(): String? = withContext(Dispatchers.IO) {
        runCatching { reader.readLine() }.getOrNull()
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
        runCatching { closer?.close() }
    }
}

fun HostMsg.encode(): String = ProtocolJson.encodeToString(HostMsg.serializer(), this)
fun ClientMsg.encode(): String = ProtocolJson.encodeToString(ClientMsg.serializer(), this)
fun decodeHost(line: String): HostMsg? =
    runCatching { ProtocolJson.decodeFromString(HostMsg.serializer(), line) }.getOrNull()

fun decodeClient(line: String): ClientMsg? =
    runCatching { ProtocolJson.decodeFromString(ClientMsg.serializer(), line) }.getOrNull()
