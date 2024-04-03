package webServer

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class HttpClient internal constructor(private val socket: Socket) : Closeable {
    constructor(
        serverAddress: String = "127.0.0.1",
        port: Int = 80,
        timeToLive: Duration = 1000.milliseconds
    ) : this(Socket(serverAddress, port)) {
        socket.soTimeout = timeToLive.toInt(DurationUnit.MILLISECONDS)
    }

    override fun close() {
        socket.close()
    }

    fun stop() = close()

    suspend fun requestFile(filepath: Path) = coroutineScope {

        if (socket.isClosed) return@coroutineScope

        BufferedReader(InputStreamReader(socket.inputStream)).use { inputStream ->
            PrintWriter(OutputStreamWriter(socket.outputStream), true).use { out ->

                out.println(
                    buildMessageRequest(
                        socket.inetAddress.hostAddress,
                        filepath
                    )
                )

                val (code, message) = inputStream.readLine().getResponseCode()

                if (code != 200) {
                    log.error { "Server return an error - $code, with message: $message" }
                } else {
                    log.debug { "Received successful response from the server. 200 OK" }

                    do {
                        val line = inputStream.readLine() ?: break
                    } while (line.isNotBlank())

                    log.debug { "Server sent the file $filepath with content:" }
                    while (isActive && !socket.isClosed) {
                        val fileLine = inputStream.readLine() ?: break
                        println(fileLine)
                    }
                }

                if (!socket.isClosed) socket.close()
                log.debug { "Connection with server closed by client" }
            }
        }
    }
}

private fun String.getResponseCode(): Pair<Int, String> {
    val splitted = split("\\s+".toRegex())
    return Pair(splitted[1].toInt(), splitted.subList(2, splitted.size).joinToString(" "))
}

private fun buildMessageRequest(host: String, filepath: Path) = """
GET $filepath HTTP/1.1
Host: $host
Connection: Keep-Alive
        """.trimIndent()

private val log = KotlinLogging.logger { }
