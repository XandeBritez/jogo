package fodinha.relay

/** `java -jar fodinha-relay.jar [porta]`. Porta padrao 5555. */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 5555
    val server = RelayServer(port)
    server.start()
    // Voz: UDP na MESMA porta. Um numero so para abrir no firewall (tcp e udp).
    val voice = VoiceRelayServer(port, roomExists = server::hasRoom)
    voice.start()
    println("fodinha relay na porta ${server.port} (tcp jogo + udp voz)")
    Runtime.getRuntime().addShutdownHook(Thread { voice.close(); server.close() })
    while (true) {
        Thread.sleep(60_000)
        println("salas abertas: ${server.roomCount}, salas com voz: ${voice.roomCount}")
    }
}
