package me.nickclifford.mobilecomputingdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()

            MobileComputingDemoTheme {
                Scaffold(
                    topBar = { TopBar() },
                    bottomBar = { BottomNav(navController) },
                    modifier = Modifier.fillMaxSize()
                ) { pad ->
                    Surface(modifier = Modifier.padding(pad)) {
                        NavHost(navController, startDestination = "record") {
                            composable("record") { RecordPage() }
                            composable("denoise") { DenoisePage() }
                            composable("results") { ResultsPage() }
                        }
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
fun BottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
            label = { Text("Denoise") },
            onClick = { navController.goTo("denoise") },
            icon = {
                Icon(
                    Icons.Filled.Build, contentDescription = "Denoise"
                )
            },
            selected = currentDestination.isRouteSelected("denoise")
        )
        NavigationBarItem(
            label = { Text("Results") },
            onClick = { navController.goTo("results") },
            icon = {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Results"
                )
            },
            selected = currentDestination.isRouteSelected("results")
        )
    }
}

