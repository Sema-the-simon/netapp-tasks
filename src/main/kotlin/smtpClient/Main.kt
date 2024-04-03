package smtpClient

fun main() {

    val simpleClientSetUp = arrayOf("smtp.gmail.com", "abc@gmail.com", "test@gmail.com", "Hello WORLD i'm using SMTP")
    val sslClientSetUp =
        arrayOf("ssl", "smtp.gmail.com", "1@gmail.com", "2@gmail.com", "Hello WORLD i'm using safe SMTP")

    runModel(sslClientSetUp)
}

fun baseExceptionMassage(missingArg: String) =
    "You must provide <SMTP server> <email address FROM> <email address TO>. $missingArg is missing!"

fun runModel(setUp: Array<String>) {
    val isSslClient = setUp.getOrNull(0)?.equals("ssl", ignoreCase = true) == true

    val args = if (isSslClient) setUp.copyOfRange(1, setUp.lastIndex) else setUp


    val smtpServer = args.getOrNull(0) ?: throw IllegalArgumentException(baseExceptionMassage("SMTP server"))
    val fromAddress = args.getOrNull(1) ?: throw IllegalArgumentException(baseExceptionMassage("address FROM"))
    val toAddress = args.getOrNull(2) ?: throw IllegalArgumentException(baseExceptionMassage("address TO"))
    val message = args.asList().subList(3, args.size).joinToString(" ")

    val client = if (isSslClient) SMTPSslClient() else SMTPClient()
    client.use { client ->
        client.openConnectionWith(smtpServer)
        client.send {
            this.from = fromAddress
            this.to = toAddress
            this.text = message
        }
    }
}
