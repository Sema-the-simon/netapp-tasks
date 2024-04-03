package udpPinger

import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket


private val log = KotlinLogging.logger { }

class PingServer internal constructor(private val socket: DatagramSocket) : Closeable {
    constructor(port: Int) : this(DatagramSocket(port))

    private lateinit var serverJob: Job
    var isClosed = false
        private set

    private val buffer = ByteArray(256)

    suspend fun start() = coroutineScope {
        if (isClosed) return@coroutineScope
        log.debug { "Server start work on port ${socket.localPort}" }

        serverJob = launch {
            outer@ while (isActive && !isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receiveCancellable(packet) ?: break

                val receivedMassage = String(packet.data, 0, packet.length)
                log.debug { "Received message from client - ${packet.address.hostAddress}:${packet.port} -> $receivedMassage" }

                if (getDropWithChance(30)) {
                    log.debug { "Drop packet" }
                    continue@outer
                }

                val responseMassage = "SECRET CODE START: $receivedMassage :SECRET CODE END"
                log.debug { "Send response to client - ${packet.address.hostAddress}:${packet.port}" }
                socket.send(
                    DatagramPacket(
                        responseMassage.toByteArray(),
                        responseMassage.length,
                        packet.address,
                        packet.port
                    )
                )
            }
        }
    }

    override fun close() {
        isClosed = true
        runBlocking {
            serverJob.cancel()
            socket.close()
        }
    }

}

private fun getDropWithChance(percent: Int): Boolean = (1..100).random() <= percent


private fun DatagramSocket.receiveCancellable(packet: DatagramPacket) = try {
    receive(packet)
} catch (e: IOException) {
    null
}