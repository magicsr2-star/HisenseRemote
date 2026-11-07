package com.vts.hisenseremote

import android.os.Handler
import android.os.Looper
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.Executors

/**
 * MQTT legacy Hisense VIDAA, directo a la TV, sin puente.
 * Tu TV: HU50N6000UW, 172.30.218.102:36669
 * Usuario hisenseservice / password multimqttservice, sin PIN.
 * Topic: /remoteapp/tv/remote_service/{clientId}/actions/sendkey
 * Payload: texto plano, ej. KEY_POWER
 */
class TvRemote(
    var tvIp: String = "172.30.218.102",
    var tvMac: String = "10:c7:53:19:5c:f4",
    var clientId: String = "user4321\$normal_v4"
) {
    var onStatus: ((String, Boolean) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var client: MqttClient? = null

    val isConnected: Boolean get() = client?.isConnected == true

    private fun post(msg: String, ok: Boolean = true) {
        main.post { onStatus?.invoke(msg, ok) }
    }

    fun connect() {
        io.execute {
            try {
                disconnectQuietly()
                val uri = "tcp://$tvIp:36669"
                val c = MqttClient(uri, clientId, MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    userName = "hisenseservice"
                    password = "multimqttservice".toCharArray()
                    isCleanSession = true
                    keepAliveInterval = 90
                    connectionTimeout = 10
                    // Will topic como en tu captura PCAP
                    setWill("/mobile/${UUID.randomUUID()}", ByteArray(0), 0, false)
                }
                post("Conectando a $uri…")
                c.connect(opts)
                client = c
                post("Conectado a $tvIp:36669", true)
            } catch (e: Exception) {
                post("Error: ${e.message}", false)
            }
        }
    }

    fun sendKey(key: String) {
        io.execute {
            try {
                val c = client
                if (c == null || !c.isConnected) {
                    post("No conectado. Toca Conectar primero.", false)
                    return@execute
                }
                val topic = "/remoteapp/tv/remote_service/$clientId/actions/sendkey"
                c.publish(topic, MqttMessage(key.toByteArray(Charsets.UTF_8)).apply {
                    qos = 0
                    isRetained = false
                })
                post("$key → $topic", true)
            } catch (e: Exception) {
                post("Error $key: ${e.message}", false)
            }
        }
    }

    fun wakeOnLan() {
        io.execute {
            try {
                val mac = tvMac.replace(":", "").replace("-", "")
                val macBytes = ByteArray(6) { i ->
                    mac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                val packet = ByteArray(6 + 16 * 6)
                for (i in 0 until 6) packet[i] = 0xFF.toByte()
                for (i in 0 until 16) macBytes.copyInto(packet, 6 + i * 6)
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), 9))
                }
                post("Wake-on-LAN a $tvMac", true)
            } catch (e: Exception) {
                post("Error WOL: ${e.message}", false)
            }
        }
    }

    fun disconnect() {
        io.execute { disconnectQuietly(); post("Desconectado") }
    }

    private fun disconnectQuietly() {
        try { client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
    }
}
