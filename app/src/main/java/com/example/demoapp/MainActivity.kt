package com.example.demoapp

import android.os.Bundle
import android.widget.Button
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
            // Toast.makeText(context: Context, text: CharSequence, duration: Int)
            Toast.makeText(this, "Button was clicked", Toast.LENGTH_SHORT).show()
        }
    }


}