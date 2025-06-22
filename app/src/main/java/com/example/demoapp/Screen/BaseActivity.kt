package com.example.demoapp.Screen

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.demoapp.UI.NetworkMonitorDrawer
import com.example.network.ui.MainViewModel
import com.example.network.ui.MainViewModelFactory
import com.example.demoapp.Core.RoiPredictor
import com.example.demoapp.Core.SeedPredictor

// Shared singleton for MainViewModel to persist logs across activities
object SharedViewModel {
    private var instance: MainViewModel? = null
    
    fun getInstance(context: FragmentActivity): MainViewModel {
        if (instance == null) {
            val roiPredictor = RoiPredictor(context)
            val seedPredictor = SeedPredictor(context)
            instance = MainViewModelFactory(context, roiPredictor, seedPredictor)
                .create(MainViewModel::class.java)
        }
        return instance!!
    }
}

abstract class BaseActivity : FragmentActivity() {
    private var drawerStateCompose by mutableStateOf(false)

    fun openDrawer() {
        drawerStateCompose = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = SharedViewModel.getInstance(this)

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    getMainContent()()

                    if (drawerStateCompose) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)) // semi-transparent overlay
                                .clickable { drawerStateCompose = false } // tap outside to dismiss
                        ) {
                            ModalDrawerSheet(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .align(Alignment.CenterEnd),
                                drawerContainerColor = Color.Black
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Optional close button at top right
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = { drawerStateCompose = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Drawer",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    // Actual drawer content
                                    NetworkMonitorDrawer(
                                        viewModel = viewModel,
                                        context = this@BaseActivity,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    abstract fun getMainContent(): @Composable () -> Unit
}