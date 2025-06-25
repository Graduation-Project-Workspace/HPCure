package com.example.demoapp.Screen.onboarding

import com.example.demoapp.R

data class OnboardingPage(
    val imageRes: Int,
    val title: String,
    val description: String
)
val pages = listOf(
    OnboardingPage(R.drawable.image1, "Welcome to Tumor Track", "A powerful AI-driven tool to analyze MRI scans..."),
    OnboardingPage(R.drawable.image2, "Fast & Accurate Tumor Analysis", "Upload an MRI, AI detects tumors..."),
    OnboardingPage(R.drawable.image3, "Works Anywhere, Anytime!", "AI works offline, ideal for hospitals with limited internet.")
)
