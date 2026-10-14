package com.plantaoseguro.simulador

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** O que abre ao tocar na notificação: faz o papel da conversa no WhatsApp. */
class Conversa : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val conversa = intent.getStringExtra("conversa") ?: "nenhuma"
        setContentView(
            TextView(this).apply {
                text = "Conversa aberta: $conversa"
                textSize = 22f
                gravity = Gravity.CENTER
            }
        )
    }
}
