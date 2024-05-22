package me.nickclifford.mobilecomputingdemo

import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.nickclifford.mobilecomputingdemo.ui.theme.MobileComputingDemoTheme
import org.pytorch.LiteModuleLoader
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<DenoiserViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        viewModel.filesDir = filesDir
        viewModel.outputDir = getExternalFilesDir(null)!!

        viewModel.startProfiling()

        // torch needs a system file, so copy it out of the bundled assets
        // TODO: should this be cached somewhere? it's probably fine for now
        val modelFile = File.createTempFile("demucs", ".ptl", filesDir)
        modelFile.outputStream().use { temp ->
            assets.open("demucs.ptl").use { asset ->
                asset.copyTo(temp)
            }
        }
        viewModel.torchModel = LiteModuleLoader.load(modelFile.path)

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                viewModel.denoisedReady.collect { ready ->
                    if (ready) {
                        viewModel.stopProfiling()
                        navController.goTo("results")
                    }
                }
            }

            MobileComputingDemoTheme {
                Scaffold(
                    topBar = { TopBar() },
                    bottomBar = { BottomNav(navController, viewModel) },
                    modifier = Modifier.fillMaxSize()
                ) { pad ->
                    Surface(modifier = Modifier.padding(pad)) {
                        NavHost(navController, startDestination = "record") {
                            composable("record") {
                                RecordPage(
                                    viewModel,
                                    tryStartRecording = this@MainActivity::tryStartRecording,
                                    onRecordComplete = {
                                        navController.goTo(
                                            "denoise"
                                        )
                                    })
                            }
                            composable("denoise") { DenoisePage() }
                            composable("results") { ResultsPage(viewModel) }
                        }
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for this app.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun tryStartRecording() {
        if (shouldShowRequestPermissionRationale(RECORD_AUDIO)) {
            Toast.makeText(
                this,
                "Microphone access is required to record audio.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            requestPermissionLauncher.launch(RECORD_AUDIO)
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

// contents taken from https://developer.android.com/develop/ui/compose/navigation#bottom-nav
fun NavController.goTo(route: String) {
    this.navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(this@goTo.graph.findStartDestination().id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }
}

fun NavDestination?.isRouteSelected(route: String): Boolean {
    return this?.hierarchy?.any { it.route == route } == true
}

@Composable
fun BottomNav(navController: NavController, viewModel: DenoiserViewModel) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val denoisedReady by viewModel.denoisedReady.collectAsState()

    NavigationBar {
        NavigationBarItem(
            label = { Text("Record") },
            onClick = { navController.goTo("record") },
            icon = {
                Icon(
                    Icons.Filled.GraphicEq, contentDescription = "Record"
                )
            },
            selected = currentDestination.isRouteSelected("record")
        )
        NavigationBarItem(
            label = { Text("Results") },
            onClick = { navController.goTo("results") },
            icon = {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Results"
                )
            },
            selected = currentDestination.isRouteSelected("results"),
            enabled = denoisedReady
        )
    }
}

