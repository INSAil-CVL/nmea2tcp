package com.insail.nmeagpsserver

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.*
import android.content.Context

class NmeaTcpServer(private val port: Int, private val context: Context) {

    companion object {
        private const val TAG = "NmeaTcpServer"
    }

    private var serverSocket: ServerSocket? = null
    private val clients = Collections.synchronizedList(mutableListOf<Socket>())
    private var serverThread: Thread? = null
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun logToUI(message: String) {
        Log.i(TAG, message)
        logCallback?.invoke(message)
    }

    fun start() {
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
                logToUI(context.getString(R.string.tcp_server_started, port))
                while (!Thread.currentThread().isInterrupted) {
                    val client = serverSocket!!.accept()
                    val clientAddress = client.inetAddress.hostAddress
                    logToUI(context.getString(R.string.tcp_client_connected, clientAddress))
                    clients.add(client)

                    Thread {
                        try {
                            client.getInputStream().read()
                        } catch (_: Exception) {
                            // Ignorer les erreurs liées à la lecture
                        } finally {
                            clients.remove(client)
                            client.close()
                            logToUI(context.getString(R.string.tcp_client_disconnected, clientAddress))
                        }
                    }.start()
                }
            } catch (e: IOException) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e(TAG, "Server error: ${e.message}")
                    logToUI(context.getString(R.string.tcp_server_error, e.message ?: ""))
                }
            }
        }
        serverThread?.start()
    }

    fun sendToClients(data: String) {
        synchronized(clients) {
            if (clients.isNotEmpty()) {
                val iterator = clients.iterator()
                var sentCount = 0
                var errorCount = 0

                while (iterator.hasNext()) {
                    val client = iterator.next()
                    try {
                        val out = client.getOutputStream()
                        out.write((data + "\r\n").toByteArray())
                        out.flush()
                        sentCount++
                    } catch (_: IOException) {
                        client.close()
                        iterator.remove()
                        errorCount++
                    }
                }

                if (errorCount > 0) {
                    logToUI(context.getString(R.string.tcp_data_sent, sentCount, errorCount))
                }
            }
        }
    }

    fun getClientCount(): Int {
        return clients.size
    }

    fun stop() {
        logToUI(context.getString(R.string.tcp_server_stopping))
        serverThread?.interrupt()
        clients.forEach {
            try {
                it.close()
            } catch (e: IOException) {
                Log.w(TAG, "Error closing client: ${e.message}")
            }
        }
        clients.clear()
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
        logToUI(context.getString(R.string.tcp_server_stopped))
    }
}

