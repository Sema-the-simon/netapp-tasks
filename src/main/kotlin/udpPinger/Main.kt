package udpPinger

import kotlinx.coroutines.*
import java.net.SocketTimeoutException
import kotlin.time.Duration

fun main(args: Array<String>) {

    val setUp = arrayOf("1234", "8")

    runModel(setUp)

}

fun runModel(args: Array<String>) {
    val port = args.getOrElse(0) { "${(1024..65535).random()}" }.toInt()
    if (port < 0 || port > 65535)
        throw IllegalStateException("Port $port is illegal. Port should be in range 0..65535")
    
    val numberOfPings = args.getOrElse(1) { "10" }.toInt()
    if (numberOfPings < 0)
        throw IllegalStateException("Number of pings should be positive integer, but got $numberOfPings")
    
    val server = PingServer(port)
    val client = PingClient()

    runBlocking {
        launch {
            launch {
                withContext(Dispatchers.IO) {
                    server.start()
                }
            }

            launch {
                delay(100)
                var failures = 0
                val responseTimes = ArrayList<Duration>(numberOfPings)

                repeat(numberOfPings) {
                    try {
                        responseTimes += client.ping("127.0.0.1", port)
                    } catch (_: SocketTimeoutException) {
                        failures++
                    }
                }
                client.close()
                server.close()

                println("\nPing statistics:")
                println("\tSent = $numberOfPings, Received = ${numberOfPings - failures}, Lost = $failures (${(failures.toDouble() / numberOfPings) * 100}% loss)")
                println("\tMin time = ${responseTimes.min().inWholeMilliseconds}ms,")
                println("\tMax time = ${responseTimes.max().inWholeMilliseconds}ms,")
                println("\tAverage = ${responseTimes.sumOf { it.inWholeMilliseconds } / responseTimes.size}ms")
            }

        }
    }
}