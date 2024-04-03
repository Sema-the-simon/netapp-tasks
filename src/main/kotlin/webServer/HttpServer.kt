package webServer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val log = KotlinLogging.logger { }
private const val INTERNAL_SERVER_ERROR_CODE = 500

class HttpServer internal constructor(private val socket: ServerSocket) : Closeable {
    constructor(port: Int = 80, timeToLive: Duration = 5000.milliseconds) : this(ServerSocket(port)) {
        socket.soTimeout = timeToLive.toInt(DurationUnit.MILLISECONDS)
    }

    private lateinit var serverJob: Job

    var isClosed = false
        private set

    private var activeConnectionsCount = AtomicInteger(0)

    suspend fun start() = coroutineScope {
        if (isClosed) return@coroutineScope

        serverJob = launch {
            outer@ while (isActive) {
                log.debug { "Waiting for connection at port ${socket.localPort}..." }

                val deferredClient = async { socket.acceptCancellable() }
                val client = deferredClient.await() ?: break@outer

                activeConnectionsCount.incrementAndGet()

                log.debug { "Connected with client - ${client.inetAddress}:${client.port}" }
                log.debug { "Current active connection - $activeConnectionsCount" }
                workWithClient(client)
            }
            log.debug { "Exit main looper" }
        }
    }

    private fun CoroutineScope.workWithClient(client: Socket) = launch {
        BufferedReader(InputStreamReader(client.inputStream)).use { inputStream ->
            PrintWriter(OutputStreamWriter(client.outputStream)).use { out ->
                try {
                    val path = readFileRequest(client, inputStream)
                    log.debug { "Request on file $path received from ${client.inetAddress.hostAddress}:${client.port}" }

                    if (path.isRegularFile()) {
                        log.debug { "Send response to ${client.inetAddress.hostAddress}:${client.port}" }
                        sendResponse(out, path.readText())
                    } else {
                        log.debug { "File $path, requested from ${client.inetAddress.hostAddress}:${client.port}, not found!!!" }
                        RequestException("Not Found", 404).sendError(out)
                    }
                } catch (e: RequestException) {
                    log.error { "Error during request from ${client.inetAddress.hostAddress}:${client.port} -> $e" }
                    e.sendError(out)
                }
            }
        }

        delay(100)
        log.debug { "Close connection with the client ${client.inetAddress}:${client.port}" }
        client.close()

        activeConnectionsCount.decrementAndGet()
    }

    private fun CoroutineScope.readFileRequest(client: Socket, inputStream: BufferedReader): Path {
        if (!isActive || client.isClosed)
            throw RequestException("Connection already closed")

        val clientMessage = inputStream.readLine() ?: throw RequestException("No messages were received")

        return clientMessage.parseClientMassage()
    }

    private fun RequestException.sendError(out: PrintWriter) {
        val code = errorCode ?: INTERNAL_SERVER_ERROR_CODE
        out.println(
            """
HTTP/1.1 $code $message
            """.trimIndent()
        )
    }

    private fun sendResponse(out: PrintWriter, content: String, contentType: String = "text/plain") {
        out.println(
            """
HTTP/1.1 200 OK
Content-Type: $contentType

$content
            """.trimIndent()
        )
    }

    override fun close() {
        isClosed = true
        runBlocking {
            serverJob.cancel()
            socket.close()
        }
    }

    fun stop() = close()
}



private fun ServerSocket.acceptCancellable() = try {
    accept()
} catch (e: IOException) {
    null
}

private fun String.parseClientMassage(): Path {
    val withoutSpace = trimIndent()
    if (withoutSpace.isEmpty()) {
        throw RequestException("Invalid empty request")
    }

    val splitMessage = withoutSpace.split("""\s+""".toRegex())
    //only GET methods
    if (!splitMessage.first().equals("GET", ignoreCase = true)) {
        throw RequestException("Unknown method: ${splitMessage.first()}")
    }
    //only HTTP v1.1 connection
    if (!splitMessage.last().startsWith("HTTP/", ignoreCase = true)) {
        throw RequestException("Unknown scheme: ${splitMessage.last()}")
    }
    if (splitMessage.last().substringAfter("HTTP/") != "1.1") {
        throw RequestException("Unknown HTTP version: ${splitMessage.last().substringAfter("HTTP/")}")
    }

    return Path(splitMessage.subList(1, splitMessage.lastIndex).joinToString(" "))
}

class RequestException(message: String, val errorCode: Int? = null) : Exception(message) {
    override fun toString(): String = "RequestException(code=${errorCode}, message: ${message})"
}

