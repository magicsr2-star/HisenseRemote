package com.vts.hisenseremote

import android.os.Handler
import android.os.Looper
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
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
 * TV: HU50N6000UW, MQTT 36669 legacy, sin PIN.
 * Usuario hisenseservice / password multimqttservice.
 * Topic sendkey: /remoteapp/tv/remote_service/{clientId}/actions/sendkey
 * Payload: texto plano, ej. KEY_POWER
 *
 * La IP por defecto es 172.30.218.102; se cambia solo desde el
 * dialogo de pulsacion larga sobre el titulo (MainActivity).
 * Los mensajes de estado NO incluyen la IP ni el topic.
 */
class TvRemote(
    var tvIp: String = "172.30.218.102",
    var tvMac: String = "10:c7:53:19:5c:f4",
    var clientId: String = "user4321\$normal_v4"
) {
    var onStatus: ((String, Boolean) -> Unit)? = null

    /** true al conectar con exito, false al desconectar o fallar. */
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var client: MqttClient? = null

    val isConnected: Boolean get() = client?.isConnected == true

    private fun post(msg: String, ok: Boolean = true) {
        main.post { onStatus?.invoke(msg, ok) }
    }

    private fun notifyConnection(connected: Boolean) {
        main.post { onConnectionChanged?.invoke(connected) }
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
                    // Will topic como en la captura PCAP
                    setWill("/mobile/${UUID.randomUUID()}", ByteArray(0), 0, false)
                }
                c.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        notifyConnection(false)
                        post("Conexion perdida", false)
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {}
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                post("Conectando…")
                c.connect(opts)
                client = c
                post("Conectada", true)
                notifyConnection(true)
            } catch (e: Exception) {
                notifyConnection(false)
                post("Error: ${e.message}", false)
            }
        }
    }

    /** Publica un payload de texto en un topic de acciones. Devuelve true si se envio. */
    private fun publish(topic: String, payload: String): Boolean {
        val c = client
        if (c == null || !c.isConnected) {
            post("No conectado. Toca el boton de encendido para conectar.", false)
            notifyConnection(false)
            return false
        }
        return try {
            c.publish(topic, MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                qos = 0
                isRetained = false
            })
            true
        } catch (e: Exception) {
            post("Error: ${e.message}", false)
            false
        }
    }

    fun sendKey(key: String) {
        io.execute {
            if (publish("/remoteapp/tv/remote_service/$clientId/actions/sendkey", key)) {
                post("$key enviado", true)
            }
        }
    }

    /**
     * Abre una app en la TV (ej. "netflix", "youtube").
     * launchapp vive en ui_service (NO en remote_service) y el
     * payload es JSON, no texto plano: por eso los botones no
     * hacian nada en v2.2. Netflix appId "1" esta documentado
     * en varios modelos VIDAA; YouTube se manda con appId vacio
     * y url "youtube" (la TV lo resuelve por nombre/url).
     */
    fun launchApp(app: String) {
        io.execute {
            val name = if (app == "netflix") "Netflix" else "YouTube"
            val appId = if (app == "netflix") "1" else ""
            val payload = "{\"appIcon\":\"\",\"appId\":\"$appId\",\"has_detail_page\":0,\"isLocalApp\":1,\"name\":\"$name\",\"storeType\":0,\"type\":0,\"url\":\"$app\",\"urlType\":37}"
            if (publish("/remoteapp/tv/ui_service/$clientId/actions/launchapp", payload)) {
                post("Abriendo $name…", true)
            }
        }
    }

    /**
     * Envia texto tecleado a la TV (actions/input).
     * Tampoco esta verificado en este modelo: si escribir no
     * funciona, usar el teclado numerico + D-pad en el teclado
     * en pantalla de la TV.
     */
    fun sendText(text: String) {
        if (text.isBlank()) return
        io.execute {
            if (publish("/remoteapp/tv/remote_service/$clientId/actions/input", text)) {
                post("Texto enviado", true)
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
                post("Senal de encendido enviada", true)
            } catch (e: Exception) {
                post("Error: ${e.message}", false)
            }
        }
    }

    fun disconnect() {
        io.execute {
            disconnectQuietly()
            notifyConnection(false)
            post("Desconectado")
        }
    }

    private fun disconnectQuietly() {
        try { client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
    }
}
