package com.example.octopusreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.octopusreader.nfc.OctopusScan
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OctopusReaderScreen(
    viewModel: OctopusReaderViewModel,
    onOpenNfcSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Octopus Reader",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard()
            InstructionCard()
            StatusCard(state)

            state.lastScan?.let { scan ->
                ResultCard(scan)
            }

            Button(
                onClick = viewModel::requestScan,
                enabled = state.nfcSupported && state.nfcEnabled &&
                    !state.isWaitingForCard && !state.isReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = when {
                        state.isReading -> "Reading…"
                        state.isWaitingForCard -> "Waiting for card…"
                        else -> "Scan Octopus Card"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (state.nfcSupported && !state.nfcEnabled) {
                TextButton(
                    onClick = onOpenNfcSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open NFC settings")
                }
            }

            Text(
                text = "Physical cards only. The app reads one public-facing FeliCa service and never writes to the card.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text(
                    text = "NFC",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "Tap. Read. Keep it private.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                )
                Text(
                    text = "Check a physical Octopus card directly on your Android phone.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun InstructionCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "How to scan",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Step(1, "Tap “Scan Octopus Card”.")
            Step(2, "Hold the card flat against the back of the phone.")
            Step(3, "Keep it still until the result appears.")
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Text(text = text, lineHeight = 21.sp)
    }
}

@Composable
private fun StatusCard(state: OctopusReaderUiState) {
    val isProblem = !state.nfcSupported || !state.nfcEnabled ||
        (!state.isWaitingForCard && !state.isReading && state.status != "Octopus card read successfully." &&
            state.status != "Ready to scan a physical Octopus card." &&
            state.status != "NFC is ready. Tap the scan button to begin.")

    Surface(
        color = if (isProblem) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            Text(
                text = state.status,
                color = if (isProblem) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun ResultCard(scan: OctopusScan) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(
        Locale.Builder().setLanguage("en").setRegion("HK").build(),
    )
    val timeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = "Estimated balance",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = currencyFormatter.format(scan.estimatedBalanceHkd),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Community-decoded estimate — verify important amounts with the official Octopus app or a supported reader.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            HorizontalDivider()
            ResultRow("Card ID", scan.cardId, monospace = true)
            ResultRow("System code", scan.systemCode, monospace = true)
            ResultRow("Raw balance", scan.rawBalance.toString(), monospace = true)
            ResultRow("Scanned", timeFormatter.format(scan.scannedAt))

            Text(
                text = "Raw block",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            SelectionContainer {
                Text(
                    text = scan.rawBlockHex,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
