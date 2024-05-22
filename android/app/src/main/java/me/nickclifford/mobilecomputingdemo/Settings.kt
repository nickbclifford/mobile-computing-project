package me.nickclifford.mobilecomputingdemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(viewModel: DenoiserViewModel, close: () -> Unit) {
    val currentModel by viewModel.currentModel.collectAsState()

    Dialog(onDismissRequest = close) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                Column {
                    Text(
                        "Model Selection",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    for (model in Model.entries) {
                        TextButton(
                            onClick = { viewModel.setModel(model) },
                            border = if (currentModel == model) ButtonDefaults.outlinedButtonBorder else null
                        ) {
                            Text(
                                model.name.lowercase()
                            )
                        }
                    }
                }
            }
        }
    }
}