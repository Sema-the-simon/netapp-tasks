package smtpClient


import mu.KotlinLogging
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

open class SMTPClient : Closeable {

    protected lateinit var socket: Socket
    var isOpened = false
        protected set

    open val port: Int = 25
    open fun openConnectionWith(server: String, timeout: Duration = 10.seconds) {
        socket = Socket(server, port)
        isOpened = true
        socket.soTimeout = timeout.inWholeMilliseconds.toInt()
        socket.getResponseWithCode(220)
    }

    open fun <T> send(buildMessage: Mail.() -> T) {
        require(isOpened) { "It's required to call openConnection() before start work with SMTP client" }

        with(socket) {
            val message = Mail().apply { buildMessage() }
            getResponseWithCode(250, "HELO alice")
            getResponseWithCode(250, "MAIL FROM: <${message.from}>")
            getResponseWithCode(250, "RCPT TO: <${message.to}>")
            getResponseWithCode(354, "DATA")
            getResponseWithCode(250, message.text)
            getResponseWithCode(221, "QUIT")
        }
    }

    override fun close() {
        if (isOpened) {
            log.debug { "SMTP Client socket is closed" }
            socket.close()
        }
    }
}

internal fun Socket.getResponseWithCode(expectedCode: Int, message: String? = null) {
    val reader = BufferedReader(InputStreamReader(inputStream))
    val writer = PrintWriter(OutputStreamWriter(outputStream), true)

    message?.let { msg -> writer.println(msg) }

    val line = reader.readLine() ?: throw IllegalStateException("Unexpected server response! No one response received")
    log.debug { "Response: $line" }


    if (!line.startsWith((expectedCode.toString()))) {
        log.debug { "\t ${reader.readLine()}" }
        throw IllegalStateException("Unexpected server response! Expected $expectedCode code response, but got $line")
    }
}

class Mail {
    var from = "test@test.test"
    var to = "test@test.test"
    var text = ""
}