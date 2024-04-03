package smtpClient

import mu.KotlinLogging
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration

private val logger = KotlinLogging.logger { }

class SMTPSslClient : SMTPClient() {

    private lateinit var sslSocket: SSLSocket
    override val port: Int = 587
    override fun openConnectionWith(server: String, timeout: Duration) {
        super.openConnectionWith(server, timeout)
        socket.getResponseWithCode(250, "EHLO alice")
        socket.getResponseWithCode(220, "STARTTLS")
        System.setProperty("javax.net.ssl.trustStore", "C:\\env\\JDKs\\openjdk-17.0.1\\lib\\security\\cacerts")

        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        sslSocket = sslFactory.createSocket(socket, socket.inetAddress.hostAddress, socket.port, true) as SSLSocket
        sslSocket.useClientMode = true
        sslSocket.enableSessionCreation = true

        logger.debug { "Start SSL handshake with the server" }
        sslSocket.startHandshake()
        logger.debug { "Secured connection with the server is established" }
    }

    override fun <T> send(buildMessage: Mail.() -> T) {
        require(isOpened) { "It's required to call openConnection() before start work with SMTP client" }
        with(sslSocket) {
            val message = Mail().apply { buildMessage() }

            getResponseWithCode(334, "AUTH LOGIN")
            println("Please, specify the username for the SMTP server:")
            getResponseWithCode(334, readlnOrNull() ?: "anonymous")
            println("Please, specify the password for the SMTP server:")
            getResponseWithCode(235, readlnOrNull() ?: "anonymous")
            getResponseWithCode(250, "MAIL FROM: <${message.from}>")
            getResponseWithCode(250, "RCPT TO: <${message.to}>")
            getResponseWithCode(354, "DATA")
            getResponseWithCode(250, message.text)
            getResponseWithCode(221, "QUIT")
        }
    }

    override fun close() {
        sslSocket.close()
        super.close()
    }
}