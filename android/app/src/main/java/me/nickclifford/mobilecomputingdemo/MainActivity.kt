package me.nickclifford.mobilecomputingdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.nickclifford.mobilecomputingdemo.ui.theme.MobileComputingDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileComputingDemoTheme {
                Scaffold(topBar = { TopBar() },
                    bottomBar = { BottomNav() },
                    modifier = Modifier.fillMaxSize()
                ) { pad ->
                    Surface(modifier = Modifier.padding(pad)) {
                        Text("Hello World!", modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.primary,
    ), title = { Text("Soundscape Savior") })
}

@Composable
fun BottomNav() {
    // TODO: properly implement navigation
    NavigationBar {
        NavigationBarItem(label = { Text("Record") }, onClick = { /*TODO*/ }, icon = {
            Icon(
                Icons.Filled.Call, contentDescription = "Record"
            )
        }, selected = true)
        NavigationBarItem(label = { Text("Denoise") }, onClick = { /*TODO*/ }, icon = {
            Icon(
                Icons.Filled.Build, contentDescription = "Denoise"
            )
        }, selected = false, enabled = false)
        NavigationBarItem(label = { Text("Results") }, onClick = { /*TODO*/ }, icon = {
            Icon(
                Icons.Filled.CheckCircle, contentDescription = "Results"
            )
        }, selected = false, enabled = false)
    }
}

