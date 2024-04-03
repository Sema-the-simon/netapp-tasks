package proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

private val logger = KotlinLogging.logger { }
class ProxyServer(val server: String, remotePort: Int, port: Int = remotePort) : Closeable {
    init {
        require(remotePort >= 0)
        require(port >= 0)
    }

    private val serverSocket = ServerSocket(port)
    private val socket = Socket(server, remotePort)

    suspend fun start() = coroutineScope {
        if (socket.isClosed) return@coroutineScope

        logger.debug { "Waiting for connection at port ${serverSocket.localPort}" }
        val client = serverSocket.accept()
        logger.debug { "Established connection with client: ${client.inetAddress}:${client.port}" }

        launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(client.inputStream)).use { clientReader ->
                PrintWriter(OutputStreamWriter(client.outputStream), true).use { clientWriter ->
                    BufferedReader(InputStreamReader(socket.inputStream)).use { remoteReader ->
                        PrintWriter(OutputStreamWriter(socket.outputStream), true).use { remoteWriter ->
                            val clientRequest = clientReader.readLine() ?: return@launch
                            logger.debug { "Got request from client: $clientRequest" }

                            val dir = File(
                                javaClass.classLoader.getResource("lighthouse")?.toURI() ?: return@launch
                            ).parentFile

                            val file = File(dir, clientRequest.split("\\s+".toRegex())[1])
                            if (file.isFile) {
                                logger.debug { "Requested file $file exists in the cache" }
                                logger.debug { "Return it content directly" }
                                clientWriter.println(makeFileResponse(file.readText()))
                            } else {
                                logger.debug { "Requested file $file does not exist in the cache" }
                                logger.debug { "Request it from the server $server:${socket.port}" }

                                remoteWriter.println(clientRequest)

                                val content = remoteReader.readLines().dropWhile { it.isNotBlank() }.joinToString("\n")
                                file.writeText(content)
                                logger.debug { "File $file is saved in the cache" }
                                clientWriter.println(makeFileResponse(content))
                            }
                        }
                    }
                }
            }

        }
    }

    private fun makeFileResponse(content: String) = """
HTTP/1.1 200 OK

$content
""".trimIndent()

    override fun close() {
        if (!socket.isClosed) socket.close()
        if (!serverSocket.isClosed) serverSocket.close()
    }
}