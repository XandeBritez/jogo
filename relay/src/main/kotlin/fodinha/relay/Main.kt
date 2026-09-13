package fodinha.relay

/** `java -jar fodinha-relay.jar [porta]`. Porta padrao 5555. */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 5555
    val server = RelayServer(port)
    server.start()
    println("fodinha relay na porta ${server.port}")
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    while (true) {
        Thread.sleep(60_000)
        println("salas abertas: ${server.roomCount}")
    }
}
