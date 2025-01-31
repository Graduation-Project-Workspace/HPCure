package com.example.demoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    // Main ACTIVITY REPRESENTS one screen (Important) , each activity=> screen

    override fun onCreate(savedInstanceState: Bundle?) { // onCreate with one parameter
        super.onCreate(savedInstanceState)
        // should contain layout attached(UI) , layout(xml file)
        setContentView(R.layout.activity_main)
        // view? views are small entities in layout
        val btnShowToast = findViewById<Button>(R.id.btnShowToast)
        btnShowToast.setOnClickListener {
            Toast.makeText(this, "Button was clicked", Toast.LENGTH_SHORT).show()
        }

        var btnSendMsgToNextActivity = findViewById<Button>(R.id.btnSendMsgToNextActivity)
        btnSendMsgToNextActivity.setOnClickListener{
            val message: String = findViewById<EditText>(R.id.etUserMessage).text.toString()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // intent (navigation logic)
            val intent = Intent(this , SecondActivity::class.java)
            startActivity(intent)


        }
    }


}