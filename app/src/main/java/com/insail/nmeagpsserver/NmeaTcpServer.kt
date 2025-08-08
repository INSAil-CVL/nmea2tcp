package com.insail.nmeagpsserver

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.*

class NmeaTcpServer(private val port: Int) {

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
                logToUI("Serveur démarré sur le port $port")
                while (!Thread.currentThread().isInterrupted) {
                    val client = serverSocket!!.accept()
                    val clientAddress = client.inetAddress.hostAddress
                    logToUI("Client connecté: $clientAddress")
                    clients.add(client)

                    Thread {
                        try {
                            client.getInputStream().read()
                        } catch (_: Exception) {
                            // Ignorer les erreurs liées à la lecture
                        } finally {
                            clients.remove(client)
                            client.close()
                            logToUI("Client déconnecté: $clientAddress")
                        }
                    }.start()
                }
            } catch (e: IOException) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e(TAG, "Server error: ${e.message}")
                    logToUI("Erreur serveur: ${e.message}")
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
                    } catch (e: IOException) {
                        client.close()
                        iterator.remove()
                        errorCount++
                    }
                }

                if (errorCount > 0) {
                    logToUI("Données envoyées à $sentCount clients, $errorCount déconnectés")
                }
            }
        }
    }

    fun getClientCount(): Int {
        return clients.size
    }

    fun stop() {
        logToUI("Arrêt du serveur TCP...")
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
        logToUI("Serveur TCP arrêté")
    }
}
