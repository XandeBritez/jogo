package fodinha.app.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/*
 * Chat de voz da sala WiFi.
 *
 * Topologia: cada aparelho manda um fluxo so, para o host; o host devolve o
 * que recebeu para todos os outros (VoiceRelay). Nao ha mistura no host - quem
 * mistura e cada ouvinte (VoiceChat), somando o PCM de cada vizinho. Assim o
 * host nao vira gargalo de CPU e "mutar alguem" e decisao local: e o meu
 * ouvido, nao precisa passar pela rede.
 *
 * Canal proprio, UDP, fora do socket JSON do jogo: jogada atrasada por causa
 * de audio seria inaceitavel, e audio perdido e so um estalo.
 *
 * Formato do datagrama:
 *   [0]    tipo: 1 = audio, 2 = presenca (keepalive)
 *   [1]    id do assento de quem manda
 *   [2..3] sequencia (so audio)
 *   [4..]  PCM 16 bits little-endian, mono, 16 kHz, 20 ms = 320 amostras
 *
 * Na LAN o audio vai em PCM cru: 32 kB/s por falante e trocado, e nao ha
 * o que comprimir. Pela internet (sala com codigo) vai em AMR-WB pelo
 * MediaCodec do proprio Android (~3 kB/s), e o datagrama ganha o codigo da
 * sala depois do cabecalho, para o relay da VPS saber a quem repassar:
 *   [4..8]  codigo da sala, 5 bytes ASCII
 *   [9..]   um quadro AMR-WB (20 ms)
 */

const val VOICE_SAMPLE_RATE = 16_000
const val VOICE_FRAME_SAMPLES = 320
private const val VOICE_FRAME_BYTES = VOICE_FRAME_SAMPLES * 2
private const val HEADER_BYTES = 4
private const val CODE_BYTES = 5
private const val AMR_WB_MIME = "audio/amr-wb"
private const val AMR_WB_BITRATE = 23_850
/** AMR-WB a 23.85 kbps da ~61 bytes por quadro; folga para o cabecalho. */
private const val MAX_PACKET = HEADER_BYTES + CODE_BYTES + VOICE_FRAME_BYTES
private const val TYPE_AUDIO: Byte = 1
private const val TYPE_PRESENCE: Byte = 2

/** Quem some por mais que isto deixa de receber o audio dos outros. */
private const val PEER_TIMEOUT_MS = 5_000L
private const val KEEPALIVE_MS = 1_000L

/** Abaixo disto o quadro e silencio: nao vale mandar. */
private const val SPEECH_RMS = 350.0

/**
 * Quadros que continuam saindo depois que a energia cai (200 ms). Sem isto o
 * fim de cada palavra e a primeira silaba depois de uma pausa somem.
 */
private const val HANGOVER_FRAMES = 10

/** Fila por vizinho: 8 quadros = 160 ms de folga contra jitter. */
private const val JITTER_FRAMES = 8

// ---------------------------------------------------------------------------
// Host
// ---------------------------------------------------------------------------

/**
 * Espelho de audio no host. Aprende o endereco de cada assento pelo primeiro
 * datagrama que chega dele e repassa cada quadro de audio a todos os outros.
 */
class VoiceRelay {
    private class Peer(@Volatile var address: SocketAddress, @Volatile var lastSeen: Long)

    private val socket = DatagramSocket(0)
    private val peers = ConcurrentHashMap<Int, Peer>()

    @Volatile private var running = true

    val port: Int get() = socket.localPort

    fun start() {
        Thread({ loop() }, "voice-relay").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val buf = ByteArray(HEADER_BYTES + VOICE_FRAME_BYTES)
        val packet = DatagramPacket(buf, buf.size)
        while (running) {
            runCatching { socket.receive(packet) }.onFailure { return }
            if (packet.length < 2) continue
            val type = buf[0]
            val from = buf[1].toInt()
            val now = System.currentTimeMillis()

            val peer = peers[from]
            if (peer == null) {
                peers[from] = Peer(packet.socketAddress, now)
            } else {
                peer.address = packet.socketAddress
                peer.lastSeen = now
            }

            // Varredura de quem sumiu, antes de repassar: assim a lista de
            // destino ja esta limpa e nao se mexe nela no meio da iteracao.
            peers.entries.removeIf { now - it.value.lastSeen > PEER_TIMEOUT_MS }

            if (type != TYPE_AUDIO) continue
            val out = DatagramPacket(buf, packet.length)
            for ((id, p) in peers) {
                if (id == from) continue
                out.socketAddress = p.address
                runCatching { socket.send(out) }
            }
        }
    }

    fun close() {
        running = false
        socket.close()
        peers.clear()
    }
}

// ---------------------------------------------------------------------------
// Cliente (inclusive o proprio host, que fala com o relay por loopback)
// ---------------------------------------------------------------------------

data class VoiceState(
    val connected: Boolean = false,
    val micMuted: Boolean = false,
    /** Assentos que eu silenciei. Decisao local, nao viaja pela rede. */
    val mutedPeers: Set<Int> = emptySet(),
    /** Quem esta com audio chegando neste instante (inclui eu, se estou falando). */
    val speaking: Set<Int> = emptySet(),
    /** Faltou permissao de microfone ou o audio nao abriu. */
    val error: String? = null,
)

/**
 * Lado do ouvinte/falante. Tres threads: captura e envio, recepcao, mistura e
 * reproducao. Threads de verdade, nao coroutines: AudioRecord.read e
 * AudioTrack.write bloqueiam, e e esse bloqueio que da o ritmo de 20 ms.
 */
class VoiceChat(
    private val context: Context,
    private val host: InetAddress,
    private val port: Int,
    private val myId: Int,
    /** Codigo da sala pela internet. Null = LAN (PCM cru, sem codigo no pacote). */
    private val roomCode: String? = null,
) {
    private val internet = roomCode != null
    private val headerBytes = if (internet) HEADER_BYTES + CODE_BYTES else HEADER_BYTES
    private val codeBytes: ByteArray = roomCode?.take(CODE_BYTES)?.padEnd(CODE_BYTES)
        ?.toByteArray(Charsets.US_ASCII) ?: ByteArray(0)

    /** Codec so pela internet. Um decodificador por vizinho: AMR guarda estado. */
    private var encoder: AmrWb? = null
    private val decoders = ConcurrentHashMap<Int, AmrWb>()

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var socket: DatagramSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private val threads = mutableListOf<Thread>()

    @Volatile private var running = false
    @Volatile private var micMuted = false
    @Volatile private var mutedPeers: Set<Int> = emptySet()

    /** Quadros a tocar, por vizinho. Quem esta mudo nem entra aqui. */
    private val queues = ConcurrentHashMap<Int, ArrayDeque<ShortArray>>()
    private val lastAudio = ConcurrentHashMap<Int, Long>()

    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL

    /** Abre microfone, alto-falante e socket. Falha vira `state.error`, nao excecao. */
    fun start() {
        if (running) return
        val opened = runCatching { openAudio() }
        opened.onFailure { e ->
            _state.value = VoiceState(error = e.message ?: "nao deu para abrir o audio")
            closeAudio()
            return
        }
        running = true
        _state.value = VoiceState(connected = true)
        threads += Thread({ captureLoop() }, "voice-capture")
        threads += Thread({ receiveLoop() }, "voice-receive")
        threads += Thread({ playLoop() }, "voice-play")
        threads.forEach { it.isDaemon = true; it.start() }
    }

    fun setMicMuted(muted: Boolean) {
        micMuted = muted
        _state.value = _state.value.copy(micMuted = muted)
    }

    fun toggleMutePeer(id: Int) {
        val next = if (id in mutedPeers) mutedPeers - id else mutedPeers + id
        mutedPeers = next
        if (id in next) queues.remove(id)
        _state.value = _state.value.copy(mutedPeers = next)
    }

    fun close() {
        if (!running && socket == null) return
        running = false
        runCatching { socket?.close() }
        threads.forEach { runCatching { it.join(500) } }
        threads.clear()
        closeAudio()
        queues.clear()
        lastAudio.clear()
        _state.value = VoiceState()
    }

    // ---- audio ----

    private fun openAudio() {
        val am = audioManager
        previousMode = am.mode
        // Modo de chamada: liga o cancelamento de eco do aparelho e o alto-falante,
        // que e como esse chat vai ser usado - celular na mesa, nao no ouvido.
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true

        val minIn = AudioRecord.getMinBufferSize(
            VOICE_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            VOICE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minIn, VOICE_FRAME_BYTES * 4),
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "microfone nao abriu (permissao?)" }
        record = rec
        if (AcousticEchoCanceler.isAvailable()) {
            echo = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noise = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }

        val minOut = AudioTrack.getMinBufferSize(
            VOICE_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val tr = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(VOICE_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(minOut, VOICE_FRAME_BYTES * 4),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        check(tr.state == AudioTrack.STATE_INITIALIZED) { "alto-falante nao abriu" }
        track = tr

        if (internet) encoder = AmrWb(encode = true)

        socket = DatagramSocket()
        rec.startRecording()
        tr.play()
    }

    private fun closeAudio() {
        runCatching { encoder?.release() }; encoder = null
        decoders.values.forEach { runCatching { it.release() } }
        decoders.clear()
        runCatching { echo?.release() }; echo = null
        runCatching { noise?.release() }; noise = null
        runCatching { record?.stop() }
        runCatching { record?.release() }; record = null
        runCatching { track?.stop() }
        runCatching { track?.release() }; track = null
        runCatching { socket?.close() }; socket = null
        runCatching {
            val am = audioManager
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = previousMode
        }
    }

    // ---- threads ----

    private fun captureLoop() {
        val rec = record ?: return
        val sock = socket ?: return
        val target = InetSocketAddress(host, port)
        val samples = ShortArray(VOICE_FRAME_SAMPLES)
        val buf = ByteArray(MAX_PACKET)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ByteArray(VOICE_FRAME_BYTES)
        val pcmBuf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var seq = 0
        var lastKeepalive = 0L
        var hangover = 0

        // Presenca primeiro: e ela que registra meu endereco no relay, senao os
        // outros falam e eu nao ouvo ate abrir a boca.
        sendPresence(sock, target)

        while (running) {
            val n = rec.read(samples, 0, samples.size)
            if (n <= 0) continue
            val now = System.currentTimeMillis()
            val loud = !micMuted && rms(samples, n) >= SPEECH_RMS
            if (loud) hangover = HANGOVER_FRAMES else if (hangover > 0) hangover--
            if (loud || (hangover > 0 && !micMuted)) {
                val enc = encoder
                if (enc == null) {
                    bb.clear()
                    putHeader(bb, TYPE_AUDIO, seq++)
                    for (i in 0 until n) bb.putShort(samples[i])
                    runCatching { sock.send(DatagramPacket(buf, bb.position(), target)) }
                } else {
                    // AMR-WB: o codec pode devolver 0, 1 ou 2 quadros por chamada.
                    pcmBuf.clear()
                    for (i in 0 until n) pcmBuf.putShort(samples[i])
                    for (frame in enc.process(pcm, n * 2)) {
                        bb.clear()
                        putHeader(bb, TYPE_AUDIO, seq++)
                        bb.put(frame, 0, minOf(frame.size, buf.size - bb.position()))
                        runCatching { sock.send(DatagramPacket(buf, bb.position(), target)) }
                    }
                }
                lastAudio[myId] = now
            }
            if (now - lastKeepalive >= KEEPALIVE_MS) {
                sendPresence(sock, target)
                lastKeepalive = now
            }
        }
    }

    private fun sendPresence(sock: DatagramSocket, target: SocketAddress) {
        val bb = ByteBuffer.allocate(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        putHeader(bb, TYPE_PRESENCE, 0)
        runCatching { sock.send(DatagramPacket(bb.array(), bb.position(), target)) }
    }

    private fun putHeader(bb: ByteBuffer, type: Byte, seq: Int) {
        bb.put(type).put(myId.toByte()).putShort((seq and 0xFFFF).toShort())
        if (internet) bb.put(codeBytes)
    }

    private fun receiveLoop() {
        val sock = socket ?: return
        val buf = ByteArray(MAX_PACKET)
        val packet = DatagramPacket(buf, buf.size)
        while (running) {
            runCatching { sock.receive(packet) }.onFailure { return }
            if (packet.length <= headerBytes || buf[0] != TYPE_AUDIO) continue
            val from = buf[1].toInt()
            if (from == myId || from in mutedPeers) continue
            val payloadLen = packet.length - headerBytes
            if (internet) {
                val dec = decoders.getOrPut(from) { AmrWb(encode = false) }
                val frameBytes = buf.copyOfRange(headerBytes, packet.length)
                for (pcm in dec.process(frameBytes, payloadLen)) enqueuePcm(from, pcm, pcm.size)
            } else {
                enqueuePcm(from, buf.copyOfRange(headerBytes, packet.length), payloadLen)
            }
            lastAudio[from] = System.currentTimeMillis()
        }
    }

    /** PCM16 LE de qualquer tamanho vira quadros de 320 amostras na fila do vizinho. */
    private fun enqueuePcm(from: Int, pcm: ByteArray, len: Int) {
        val bb = ByteBuffer.wrap(pcm, 0, len).order(ByteOrder.LITTLE_ENDIAN)
        val q = queues.getOrPut(from) { ArrayDeque() }
        while (bb.remaining() >= 2) {
            val frame = ShortArray(VOICE_FRAME_SAMPLES)
            var i = 0
            while (i < VOICE_FRAME_SAMPLES && bb.remaining() >= 2) frame[i++] = bb.getShort()
            synchronized(q) {
                if (q.size >= JITTER_FRAMES) q.removeFirst()
                q.addLast(frame)
            }
        }
    }

    /**
     * Soma um quadro de cada vizinho e toca. Sempre escreve um quadro, mesmo
     * de silencio: e o write bloqueante do AudioTrack que marca os 20 ms.
     */
    private fun playLoop() {
        val tr = track ?: return
        val mix = IntArray(VOICE_FRAME_SAMPLES)
        val out = ShortArray(VOICE_FRAME_SAMPLES)
        var lastPublish = 0L
        while (running) {
            mix.fill(0)
            for ((_, q) in queues) {
                val frame = synchronized(q) { q.removeFirstOrNull() } ?: continue
                for (i in 0 until VOICE_FRAME_SAMPLES) mix[i] += frame[i]
            }
            for (i in 0 until VOICE_FRAME_SAMPLES) {
                out[i] = mix[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            tr.write(out, 0, out.size)

            val now = System.currentTimeMillis()
            if (now - lastPublish >= 200) {
                lastPublish = now
                val speaking = lastAudio.filterValues { now - it < 400 }.keys
                val cur = _state.value
                if (cur.speaking != speaking) _state.value = cur.copy(speaking = speaking)
            }
        }
    }

    private fun rms(samples: ShortArray, n: Int): Double {
        var acc = 0.0
        for (i in 0 until n) {
            val v = samples[i].toDouble()
            acc += v * v
        }
        return sqrt(acc / n)
    }
}

/**
 * AMR-WB pelo MediaCodec, uso sincrono: entra um quadro, saem os que o codec
 * ja tiver prontos (0, 1 ou 2 - ha um quadro de atraso no comeco). Sem
 * dependencia nova: e o codec de voz que todo Android traz de fabrica.
 */
private class AmrWb(encode: Boolean) {
    private val codec: MediaCodec = if (encode) {
        MediaCodec.createEncoderByType(AMR_WB_MIME)
    } else {
        MediaCodec.createDecoderByType(AMR_WB_MIME)
    }
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L

    init {
        val fmt = MediaFormat.createAudioFormat(AMR_WB_MIME, VOICE_SAMPLE_RATE, 1)
        if (encode) fmt.setInteger(MediaFormat.KEY_BIT_RATE, AMR_WB_BITRATE)
        codec.configure(fmt, null, null, if (encode) MediaCodec.CONFIGURE_FLAG_ENCODE else 0)
        codec.start()
    }

    fun process(input: ByteArray, len: Int): List<ByteArray> {
        val inIdx = codec.dequeueInputBuffer(20_000)
        if (inIdx >= 0) {
            val ib = codec.getInputBuffer(inIdx) ?: return emptyList()
            ib.clear()
            ib.put(input, 0, len)
            codec.queueInputBuffer(inIdx, 0, len, pts, 0)
            pts += 20_000
        }
        val out = ArrayList<ByteArray>(2)
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(info, 0)
            when {
                outIdx >= 0 -> {
                    val ob = codec.getOutputBuffer(outIdx)
                    if (ob != null && info.size > 0) {
                        val arr = ByteArray(info.size)
                        ob.position(info.offset)
                        ob.get(arr)
                        out += arr
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                else -> break
            }
        }
        return out
    }

    fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }
}
