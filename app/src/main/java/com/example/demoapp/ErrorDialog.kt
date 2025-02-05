package com.example.demoapp

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView

class ErrorDialog(private val context: Context) {
    fun show(message: String) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_error, null)

        view.findViewById<TextView>(R.id.error_message).text = message
        view.findViewById<Button>(R.id.ok_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}