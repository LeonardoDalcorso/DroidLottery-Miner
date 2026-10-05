package com.droidlottery.miner.stratum.client

import com.droidlottery.miner.stratum.parser.StratumMessageParser
import com.droidlottery.miner.stratum.protocol.StratumRequest
import com.droidlottery.miner.stratum.protocol.StratumResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

interface StratumListener {
    fun onConnected()
    fun onMessage(message: StratumResponse)
    fun onDisconnected(cause: Throwable?)
}

/** Stratum V1 transport. Calls are performed on a worker thread, never the Android UI thread. */
class StratumClient(private val listener: StratumListener) {
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null

    fun connect(host: String, port: Int, tls: Boolean) {
        check(socket == null) { "Client already connected" }
        Thread {
            var failure: Throwable? = null
            try {
                val factory: SocketFactory = if (tls) SSLSocketFactory.getDefault() else SocketFactory.getDefault()
                val connectedSocket = factory.createSocket(host, port)
                socket = connectedSocket
                writer = BufferedWriter(OutputStreamWriter(connectedSocket.getOutputStream(), Charsets.UTF_8))
                listener.onConnected()
                val parser = StratumMessageParser()
                val reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream(), Charsets.UTF_8))
                while (!connectedSocket.isClosed) {
                    val line = reader.readLine() ?: break
                    parser.append("$line\n").forEach(listener::onMessage)
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                closeInternal()
                listener.onDisconnected(failure)
            }
        }.apply { name = "stratum-client"; isDaemon = true }.start()
    }

    @Synchronized fun send(request: StratumRequest) {
        val output = checkNotNull(writer) { "Client is not connected" }
        output.write(request.toJson())
        output.newLine()
        output.flush()
    }

    fun disconnect() = closeInternal()

    @Synchronized private fun closeInternal() {
        runCatching { socket?.close() }
        writer = null
        socket = null
    }
}
