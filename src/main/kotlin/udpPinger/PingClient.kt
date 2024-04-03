package udpPinger

import mu.KotlinLogging
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


private val logger = KotlinLogging.logger { }
class PingClient internal constructor(private val socket: DatagramSocket) : Closeable {
    constructor() : this(DatagramSocket())

    private var pingCount = -1
    private val buffer = ByteArray(256)

    fun ping(server: String, port: Int, timeout: Duration = 1.seconds): Duration {
        pingCount++
        socket.soTimeout = timeout.inWholeMilliseconds.toInt()

        val timeBefore = LocalDateTime.now()
        val message = "Pinging $server: number of try: $pingCount, time: ${timeBefore.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        val messageArray = message.toByteArray()

        val serverAddress = InetAddress.getByName(server)

        val packet = DatagramPacket(
            messageArray,
            messageArray.size,
            serverAddress,
            port
        )
        logger.debug { "Send ping to server - ${serverAddress.hostAddress}:$port" }
        socket.send(packet)

        val received = DatagramPacket(buffer, buffer.size)
        try {
            socket.receive(received)
        } catch (e: SocketTimeoutException) {
            println("$pingCount Request timeout - no response")
            throw e
        }

        val timeAfter = LocalDateTime.now()
        val receivesMessage = String(received.data, 0, received.length)
        val timeToResponse =
            (timeAfter.getLong(ChronoField.MILLI_OF_SECOND) - timeBefore.getLong(ChronoField.MILLI_OF_SECOND)).milliseconds
        println("$pingCount|\t Message: $receivesMessage|\t time to response: ${timeToResponse.inWholeMilliseconds} millis")
        return timeToResponse
    }

    override fun close() {
        socket.close()
    }


}