package proxy

import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket

fun main() {

    val setUp = arrayOf("google.com", "80", "37851", "true")
    runModel(setUp)
}

private val log = KotlinLogging.logger { }
fun runModel(args: Array<String>) {
    val remoteServer =
        args.getOrNull(0) ?: throw IllegalArgumentException("Provide first - remote server address")

    val remotePort = args.getOrNull(1)?.toInt()
        ?: throw IllegalArgumentException("Provide remote server port")

    if (remotePort < 0 || remotePort > 65535)
        throw IllegalStateException("Port $remotePort is illegal. Port should be in range 0..65535")

    val port = args.getOrNull(2)?.toInt()
        ?: throw IllegalArgumentException("Provide port for proxy server")

    if (port < 0 || port > 65535)
        throw IllegalStateException("Port $port is illegal. Port should be in range 0..65535")

    val modeling = args.getOrNull(3)?.toBoolean() ?: false

    val proxy = ProxyServer(remoteServer, remotePort, port)
    runBlocking {
        launch {
            withContext(Dispatchers.IO) {
                proxy.start()
            }
        }
        launch {
            if (!modeling) return@launch
            delay(100)

            log.debug { "Try to GET request of /index.html page" }
            val socket = Socket("127.0.0.1", port)
            val writer = PrintWriter(OutputStreamWriter(socket.outputStream), true)
            val reader = BufferedReader(InputStreamReader(socket.inputStream))

            writer.println("GET /index.html")

            log.debug { "Got answer from the server:" }
            reader.readLines().joinToString("\n").also { println(it) }
        }
    }
}
