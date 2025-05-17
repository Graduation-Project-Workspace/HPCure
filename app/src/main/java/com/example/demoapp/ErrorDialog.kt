package com.example.demoapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.demoapp.R

class ErrorDialog(private val context: Context) {
    fun show(message: String) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_error, null)

        // Set the message
        val messageView = view.findViewById<TextView>(R.id.error_message)
        messageView.text = message

        // Set up the OK button
        val okButton = view.findViewById<Button>(R.id.ok_button)
        okButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)

        // Set dialog width and properties
        dialog.window?.let { window ->
            // Set dim amount for background
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.5f)

            // Set layout parameters
            val params = window.attributes
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = params
        }

        dialog.show()
    }
}