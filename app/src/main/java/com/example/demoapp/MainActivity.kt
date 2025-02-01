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

            val intent = Intent(this , SecondActivity::class.java) // specify the target activity
            startActivity(intent)

            intent.putExtra("user_message", message)
            startActivity(intent)


            // 1 - Explicit Intent (you know the target activity)

            // 2 - Implicit (do not know the target activity)
            // share button => dialog box => select app to share => implicit intent
        }
        var btnShareToOtherApps = findViewById<Button>(R.id.btnShareToOtherApps)
        btnShareToOtherApps.setOnClickListener{
            val message: String = findViewById<EditText>(R.id.etUserMessage).text.toString()
            val intent = Intent()  // we do not know the target activity
            intent.action = Intent.ACTION_SEND  // action to perform
            intent.putExtra(Intent.EXTRA_TEXT, message) // key value pair (key is predefined for action_send)
            intent.type = "text/plain" // type of data
            startActivity(Intent.createChooser(intent, "Please select app: "))

        }
    }


}