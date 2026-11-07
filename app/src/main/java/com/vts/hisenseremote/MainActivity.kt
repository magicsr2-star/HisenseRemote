package com.vts.hisenseremote

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.vts.hisenseremote.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val remote = TvRemote()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        remote.onStatus = { msg, ok ->
            binding.tvStatus.text = msg
            binding.tvStatus.setTextColor(
                if (ok) getColor(android.R.color.holo_green_light)
                else getColor(android.R.color.holo_red_light)
            )
        }

        binding.etTvIp.setText(remote.tvIp)

        binding.btnConnect.setOnClickListener {
            remote.tvIp = binding.etTvIp.text.toString().trim().ifEmpty { "172.30.218.102" }
            remote.connect()
        }
        binding.btnWake.setOnClickListener { remote.wakeOnLan() }

        // Todos los botones del control llevan android:tag="KEY_..."
        // Recorremos el layout y les ponemos el mismo listener.
        bindKeys(binding.root)
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
}
