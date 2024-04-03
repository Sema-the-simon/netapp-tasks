package webServer

import kotlinx.coroutines.*
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class WorkType {
    SERVER, CLIENT, BOTH
}

fun main(args: Array<String>) {

    val serverSetUp = arrayOf("server", "8080", "10000")
    val clientSetUp = arrayOf("client", "127.0.0.1", "8080", "./src/main/resources/someText.txt")
    val clientServerSetUp = arrayOf("both", "8080", "10000")

    runModel(clientServerSetUp)

}

fun runModel(args: Array<String>) {
    val workType = WorkType.entries.firstOrNull {
        it.name.equals(args[0], ignoreCase = true)
    } ?: throw Exception("First argument should be either server / client / both !")

    runBlocking {
        when (workType) {
            WorkType.SERVER -> {
                val serverPort = args.getOrElse(1) { "80" }.toInt()
                if (serverPort < 0 || serverPort > 65535)
                    throw IllegalStateException("Port $serverPort is illegal. Port should be in range 0..65535")

                val serverWorkTime = args.getOrNull(2)?.toLong()?.milliseconds

                launch {
                    runServer(serverPort, serverWorkTime)
                }
            }

            WorkType.CLIENT -> {
                val serverAddress = args.getOrNull(1)
                    ?: throw IllegalStateException("Provide server address as the second argument")

                val serverPort = args.getOrElse(2) { "80" }.toInt()
                if (serverPort < 0 || serverPort > 65535)
                    throw IllegalStateException("Port $serverPort is illegal. Port should be in range 0..65535")

                val filePathToRequest = args.getOrNull(3)
                    ?: throw IllegalStateException("Expected filepath to be requested from the server as the forth argument!")

                launch {
                    val client = HttpClient(serverAddress, serverPort)
                    client.requestFile(Path(filePathToRequest))
                }
            }

            WorkType.BOTH -> {
                val serverPort = args.getOrElse(1) { "80" }.toInt()
                if (serverPort < 0 || serverPort > 65535)
                    throw IllegalStateException("Port $serverPort is illegal. Port should be in range 0..65535")

                val serverWorkTime = args.getOrNull(2)?.toLong()?.milliseconds

                launch {
                    modelBoth(serverPort, serverWorkTime)
                }
            }

        }

    }
}

fun CoroutineScope.runServer(port: Int, timeToWork: Duration? = null): HttpServer {
    val server = HttpServer(port = port)
    launch {
        withContext(Dispatchers.IO) {
            server.start()
        }
    }
    launch {
        timeToWork?.let { time ->
            delay(time)
            server.stop()
        }
    }
    return server
}

private val randomPaths = arrayOf(
    Path("/bin/thisBADfilePath"),
    Path("C:\\Windows\\explorer.FAKEexe"),
    Path("./src/main/resources/someText.txt"),
    Path("./src/main/resources/someHTML.html"),
)

fun CoroutineScope.modelBoth(port: Int = 80, timeToWork: Duration? = 10.seconds) {
    var isServerRunningLambda = { false }
    launch {
        val server = runServer(port, timeToWork = timeToWork)
        isServerRunningLambda = { !server.isClosed }
    }

    launch {
        while (!isServerRunningLambda()) {
            delay(50)
        }

        while (isServerRunningLambda()) {
            launch {
                try {
                    val client = HttpClient("127.0.0.1", port)
                    delay((500L..2000L).random())
                    client.requestFile(randomPaths.random())
                } catch (_: Exception) {
                }
            }
            delay((100L..2000L).random())
        }
    }
}