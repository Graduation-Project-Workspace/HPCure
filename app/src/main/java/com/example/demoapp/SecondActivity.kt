package com.example.demoapp

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        val bundle: Bundle?= intent.extras
        val msg = bundle?.getString("user_message") ?: "Default message"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        findViewById<TextView>(R.id.txtUserMessage).text = msg

    }
}