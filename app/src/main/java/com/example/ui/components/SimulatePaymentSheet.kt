package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.preferences.PaymentChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatePaymentSheet(
    onDismiss: () -> Unit,
    onSimulate: (amount: Double, currency: String, senderName: String?, channel: PaymentChannel) -> Unit,
) {
    var amountText by remember { mutableStateOf("500") }
    var senderText by remember { mutableStateOf("Ramesh Adhikari") }
    var selectedChannel by remember { mutableStateOf(PaymentChannel.ESEWA) }
    var amountError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Simulate Incoming Payment",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Test the Soundbox voice, chime, and ledger flow with a realistic notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    PaymentChannel.ESEWA,
                    PaymentChannel.KHALTI,
                    PaymentChannel.FONEPAY,
                    PaymentChannel.IME_PAY,
                    PaymentChannel.BANK_ALERT,
                ).forEach { channel ->
                    FilterChip(
                        selected = selectedChannel == channel,
                        onClick = { selectedChannel = channel },
                        label = { Text(channel.displayName) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                    amountError = false
                },
                label = { Text("Amount (NPR)") },
                singleLine = true,
                isError = amountError,
                supportingText = if (amountError) {
                    { Text("Enter a valid amount greater than 0") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulate_amount_input"),
            )

            OutlinedTextField(
                value = senderText,
                onValueChange = { senderText = it },
                label = { Text("Sender Name (Optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulate_sender_input"),
            )

            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        amountError = true
                    } else {
                        onSimulate(parsed, "NPR", senderText, selectedChannel)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("confirm_simulate_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = "Announce Payment",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
