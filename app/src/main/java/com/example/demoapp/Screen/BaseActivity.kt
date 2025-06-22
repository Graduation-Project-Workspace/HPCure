package com.example.demoapp.Screen

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.demoapp.UI.NetworkMonitorDrawer
import com.example.network.ui.MainViewModel
import com.example.network.ui.MainViewModelFactory
import com.example.demoapp.Core.RoiPredictor
import com.example.demoapp.Core.SeedPredictor

@OptIn(ExperimentalMaterial3Api::class)
abstract class BaseActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel
    private var drawerState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roiPredictor = RoiPredictor(this)
        val seedPredictor = SeedPredictor(this)
        viewModel = MainViewModelFactory(this, roiPredictor, seedPredictor).create(MainViewModel::class.java)

        setContent {
            MaterialTheme {
                Scaffold (
                    topBar = {
                        TopAppBar (
                            title = { Text(getScreenTitle()) },
                            navigationIcon = {
                                IconButton(onClick = { drawerState = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF1E1E1E),
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        // Main content
                        getMainContent()

                        // Navigation drawer
                        if (drawerState) {
                            ModalDrawerSheet(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                drawerContainerColor = Color.Black
                            ) {
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

    abstract fun getScreenTitle(): String
    abstract fun getMainContent(): @Composable () -> Unit
} 