package com.example.demoapp.Screen

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.demoapp.R

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var skipButton: TextView
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        skipButton = findViewById(R.id.skipButton)
        nextButton = findViewById(R.id.nextButton)

        val onboardingScreens = listOf(
            OnboardingItem("Welcome to Tumor Track", "A powerful AI-driven tool to analyze MRI scans.", R.drawable.image1),
            OnboardingItem("Fast & Accurate Tumor Analysis", "Upload an MRI, let AI detect tumors, and get precise volume calculations.", R.drawable.image2),
            OnboardingItem("Works Anywhere, Anytime!", "Our app functions offline, perfect for hospitals with limited internet.", R.drawable.image3)
        )

        val adapter = OnboardingAdapter(onboardingScreens)
        viewPager.adapter = adapter

        nextButton.setOnClickListener {
            if (viewPager.currentItem < onboardingScreens.size - 1) {
                viewPager.currentItem += 1
            } else {
                startActivity(Intent(this, HomeScreen::class.java))
                finish()
            }
        }
        skipButton.setOnClickListener {
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
        }
    }
}
