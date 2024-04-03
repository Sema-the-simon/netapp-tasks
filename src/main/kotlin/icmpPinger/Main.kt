package icmpPinger

import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.measureTimedValue

fun main() {

    val url = "192.168.1.111"//"yandex.ru"
    runModel(url)
}
fun runModel(uri: String, numberOfIterations: Int = 4, timeout: Int = 1000) {
    val ipAddress = InetAddress.getByName(uri)

    val listOfRTT = mutableListOf<Duration>()
    println("Pockets sending to $uri [${ipAddress.hostAddress}]")
    repeat(numberOfIterations) {
        val (success, duration) = measureTimedValue {
            ipAddress.isReachable(timeout)
        }
        if (success) {
            listOfRTT += duration
            println("Get reply from ${ipAddress.hostAddress}, time = ${duration.inWholeMilliseconds} ms")
        } else {
            println("Request timed out")
        }
    }

    val fails = numberOfIterations - listOfRTT.size

    println("\nPing statistics for ${ipAddress.hostAddress}:")
    println("\tPackets: Sent = $numberOfIterations, Received = ${numberOfIterations - fails}, Lost = $fails (${(fails.toDouble() / numberOfIterations) * 100}% loss)")
    println("Approximate round trip times in milli-seconds:")
    val minTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.min().inWholeMilliseconds
    val maxTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.max().inWholeMilliseconds
    val averageTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.sumOf { it.inWholeMilliseconds } / listOfRTT.size
    println("\tMinimum = ${minTime}ms, Maximum = ${maxTime}ms, Average = ${averageTime}ms")
}
