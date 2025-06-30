package com.example.demoapp.Screen

import android.os.Bundle
import android.util.Log
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
import com.example.demoapp.Core.ParallelRoiPredictor
import com.example.demoapp.Core.ParallelSeedPredictor
import com.example.demoapp.UI.NetworkMonitorDrawer
import com.example.network.ui.MainViewModel
import com.example.network.ui.MainViewModelFactory


// Shared singleton for MainViewModel to persist logs across activities
object SharedViewModel {
    private var instance: MainViewModel? = null
    
    fun getInstance(context: FragmentActivity): MainViewModel {
        if (instance == null) {
            val roiPredictor = ParallelRoiPredictor(context)
            val seedPredictor = ParallelSeedPredictor(context)
            instance = MainViewModelFactory(context, roiPredictor, seedPredictor)
                .create(MainViewModel::class.java)
        }
        return instance!!
    }
}

abstract class BaseActivity : FragmentActivity() {
    private var openDrawerCallback: (() -> Unit)? = null

    fun openDrawer() {
        Log.d("BaseActivity", "openDrawer() called, invoking openDrawerCallback")
        openDrawerCallback?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("BaseActivity", "onCreate called for ${this::class.java.simpleName}")
        super.onCreate(savedInstanceState)
        val viewModel = SharedViewModel.getInstance(this)

        setContent {
            Log.d("BaseActivity", "setContent block recomposed")
            var drawerStateCompose by remember { mutableStateOf(false) }
            // Register the callback so openDrawer() can trigger Compose state
            DisposableEffect(Unit) {
                Log.d("BaseActivity", "DisposableEffect: registering openDrawerCallback in ${this@BaseActivity::class.java.simpleName}")
                openDrawerCallback = { drawerStateCompose = true }
                onDispose {
                    Log.d("BaseActivity", "DisposableEffect: unregistering openDrawerCallback in ${this@BaseActivity::class.java.simpleName}")
                    openDrawerCallback = null
                }
            }

            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    getMainContent()()

                    if (drawerStateCompose) {
                        Log.d("BaseActivity", "Drawer overlay is being composed!")
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

    override fun onResume() {
        super.onResume()
        Log.d("BaseActivity", "onResume called for ${this::class.java.simpleName}")
    }

    abstract fun getMainContent(): @Composable () -> Unit
}