package me.nickclifford.mobilecomputingdemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DenoisePage() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        ColumnCenterLayout(200.dp) {
            CircularProgressIndicator(modifier = Modifier.width(100.dp))
            Text("Denoising...", style = MaterialTheme.typography.headlineSmall)
        }
    }
}