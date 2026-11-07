package com.vts.hisenseremote

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vts.hisenseremote.databinding.ActivityMainBinding
import com.vts.hisenseremote.databinding.DialogKeyboardBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val remote = TvRemote()

    private val prefs by lazy { getSharedPreferences("hisense_remote", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // La IP solo se cambia con pulsacion larga sobre el titulo.
        remote.tvIp = prefs.getString(PREF_TV_IP, remote.tvIp) ?: remote.tvIp

        // Sin textos de estado: solo el color del boton dice si esta conectada.
        remote.onConnectionChanged = { connected ->
            binding.btnPower.setBackgroundResource(
                if (connected) R.drawable.power_circle_green
                else R.drawable.power_circle_red
            )
        }

        binding.btnPower.setOnClickListener {
            if (remote.isConnected) {
                // Ya conectada: el boton apaga/enciende la TV.
                remote.sendKey("KEY_POWER")
            } else {
                // No conectada: WOL por si esta apagada + conectar.
                remote.wakeOnLan()
                remote.connect()
            }
        }

        binding.tvTitle.setOnLongClickListener {
            showEditIpDialog()
            true
        }

        binding.btnKeyboard.setOnClickListener { showKeyboardDialog() }
        binding.btnNetflix.setOnClickListener { remote.launchApp("netflix") }
        binding.btnYoutube.setOnClickListener { remote.launchApp("youtube") }

        // Todos los botones del control llevan android:tag="KEY_..."
        // Recorremos el layout y les ponemos el mismo listener.
        bindKeys(binding.root)

        // Conectar automaticamente al abrir la app.
        remote.connect()
    }

    private fun showEditIpDialog() {
        val input = EditText(this).apply {
            setText(remote.tvIp)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setHint("IP de la TV")
        }
        AlertDialog.Builder(this)
            .setTitle("IP de la TV")
            .setMessage("Pulsacion larga sobre el titulo para cambiarla. Debe estar en el mismo WiFi que la TV.")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    remote.tvIp = ip
                    prefs.edit().putString(PREF_TV_IP, ip).apply()
                    remote.connect()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showKeyboardDialog() {
        val dialogBinding = DialogKeyboardBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        // Teclado numerico: envia KEY_0..KEY_9 por sendkey (probado).
        bindKeys(dialogBinding.root)
        dialogBinding.btnSendText.setOnClickListener {
            remote.sendText(dialogBinding.etKeyboardText.text.toString())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun bindKeys(view: View) {
        if (view is Button) {
            val key = view.tag as? String
            if (key != null && key.startsWith("KEY_")) {
                view.setOnClickListener { remote.sendKey(key) }
            }
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) bindKeys(view.getChildAt(i))
        }
    }

    override fun onDestroy() {
        remote.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val PREF_TV_IP = "tv_ip"
    }
}
