package com.example.octopusreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.octopusreader.R
import com.example.octopusreader.nfc.BalanceReadSupport
import com.example.octopusreader.nfc.TransitBalance
import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.TransitCardScan
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitCardReaderScreen(
    viewModel: TransitCardReaderViewModel,
    onOpenNfcSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 1,
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
            ProfileSelector(
                selected = state.selectedProfile,
                enabled = !state.isReading && !state.isWaitingForCard,
                onSelect = viewModel::selectProfile,
            )
            InstructionCard()
            StatusCard(state)

            state.lastScan?.let { scan ->
                ResultCard(scan)
            }

            Button(
                onClick = viewModel::requestScan,
                enabled = state.nfcSupported && state.nfcEnabled &&
                    state.selectedProfile != null &&
                    !state.isWaitingForCard && !state.isReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = scanButtonText(state),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (state.nfcSupported && !state.nfcEnabled) {
                TextButton(
                    onClick = onOpenNfcSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_nfc_settings))
                }
            }

            Text(
                text = stringResource(R.string.privacy_notice),
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
                    text = stringResource(R.string.hero_title),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                )
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileSelector(
    selected: TransitCardProfile?,
    enabled: Boolean,
    onSelect: (TransitCardProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.select_card_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(R.string.select_card_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selected?.let { localizedProfileSelection(it) }
                            ?: stringResource(R.string.choose_card),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    TransitCardProfile.entries.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        localizedProfileName(profile),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        localizedRegion(profile),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(profile)
                            },
                        )
                    }
                }
            }
            if (selected == null) {
                Text(
                    text = stringResource(R.string.no_card_selected),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = selected.technologyHint,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                Text(
                    text = localizedCapability(selected.balanceReadSupport),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
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
                stringResource(R.string.how_to_scan),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Step(1, stringResource(R.string.scan_step_1))
            Step(2, stringResource(R.string.scan_step_2))
            Step(3, stringResource(R.string.scan_step_3))
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
private fun StatusCard(state: TransitCardReaderUiState) {
    val isProblem = !state.nfcSupported || !state.nfcEnabled ||
        state.status == ReaderStatus.READ_FAILED
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
                text = localizedStatus(state),
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
private fun ResultCard(scan: TransitCardScan) {
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
                text = localizedProfileName(scan.selectedProfile),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )

            if (scan.balance != null) {
                Text(
                    text = if (scan.balance.isEstimated) {
                        stringResource(R.string.estimated_balance)
                    } else {
                        stringResource(R.string.balance)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatBalance(scan.balance),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = stringResource(R.string.card_detected),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.balance_not_readable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = localizedBalanceExplanation(scan),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            HorizontalDivider()
            ResultRow(
                stringResource(R.string.selected_profile),
                localizedProfileName(scan.selectedProfile),
            )
            ResultRow(stringResource(R.string.nfc_identifier), scan.cardId, monospace = true)
            scan.cardNumber?.let {
                ResultRow(stringResource(R.string.card_number), it, monospace = true)
            }
            scan.systemCode?.let {
                ResultRow(stringResource(R.string.system_code), it, monospace = true)
            }
            ResultRow(stringResource(R.string.technologies), scan.technology)
            ResultRow(stringResource(R.string.read_protocol), scan.protocol)
            ResultRow(stringResource(R.string.scanned), timeFormatter.format(scan.scannedAt))

            scan.rawDataHex?.let { rawData ->
                Text(
                    text = stringResource(R.string.raw_read_only_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                SelectionContainer {
                    Text(
                        text = rawData,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

private fun formatBalance(balance: TransitBalance): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance(balance.currencyCode)
        minimumFractionDigits = balance.fractionDigits
        maximumFractionDigits = balance.fractionDigits
    }
    val amount = BigDecimal.valueOf(balance.amountMinor).movePointLeft(balance.fractionDigits)
    return formatter.format(amount)
}

@Composable
private fun scanButtonText(state: TransitCardReaderUiState): String = when {
    state.isReading -> stringResource(R.string.button_reading)
    state.isWaitingForCard -> stringResource(R.string.button_waiting)
    state.selectedProfile == null -> stringResource(R.string.button_select_first)
    else -> stringResource(
        R.string.button_scan_card,
        localizedProfileName(state.selectedProfile),
    )
}

@Composable
private fun localizedStatus(state: TransitCardReaderUiState): String {
    val profileName = state.selectedProfile?.let { localizedProfileName(it) }.orEmpty()
    return when (state.status) {
        ReaderStatus.SELECT_CARD -> stringResource(R.string.status_select_card)
        ReaderStatus.CARD_SELECTED -> stringResource(R.string.status_card_selected, profileName)
        ReaderStatus.NFC_UNSUPPORTED -> stringResource(R.string.status_nfc_unsupported)
        ReaderStatus.NFC_DISABLED -> stringResource(R.string.status_nfc_disabled)
        ReaderStatus.NFC_READY -> stringResource(R.string.status_nfc_ready)
        ReaderStatus.SELECT_REQUIRED -> stringResource(R.string.status_select_required)
        ReaderStatus.HOLD_CARD -> stringResource(R.string.status_hold_card, profileName)
        ReaderStatus.READING -> stringResource(R.string.status_reading, profileName)
        ReaderStatus.READ_SUCCESS -> stringResource(R.string.status_read_success, profileName)
        ReaderStatus.READ_FAILED -> stringResource(R.string.status_read_failed)
    }
}

@Composable
private fun localizedProfileSelection(profile: TransitCardProfile): String = stringResource(
    R.string.card_selection_format,
    localizedProfileName(profile),
    localizedRegion(profile),
)

@Composable
private fun localizedProfileName(profile: TransitCardProfile): String =
    profile.localName?.let { localName ->
        stringResource(R.string.card_name_format, profile.displayName, localName)
    } ?: profile.displayName

@Composable
private fun localizedRegion(profile: TransitCardProfile): String = stringResource(
    when (profile) {
        TransitCardProfile.OCTOPUS -> R.string.region_hong_kong
        TransitCardProfile.EASYCARD,
        TransitCardProfile.IPASS,
        -> R.string.region_taiwan

        TransitCardProfile.EZLINK -> R.string.region_singapore
        TransitCardProfile.SUICA,
        TransitCardProfile.PASMO,
        TransitCardProfile.ICOCA,
        -> R.string.region_japan

        TransitCardProfile.YANGCHENGTONG -> R.string.region_guangzhou
        TransitCardProfile.SHENZHENTONG -> R.string.region_shenzhen
        TransitCardProfile.T_UNION -> R.string.region_china
        TransitCardProfile.CLIPPER -> R.string.region_san_francisco
        TransitCardProfile.MACAU_PASS -> R.string.region_macau
        TransitCardProfile.TOUCH_N_GO -> R.string.region_malaysia
    },
)

@Composable
private fun localizedCapability(support: BalanceReadSupport): String = stringResource(
    when (support) {
        BalanceReadSupport.ESTIMATED -> R.string.capability_estimated
        BalanceReadSupport.ISSUER_KEYS -> R.string.capability_issuer_keys
        BalanceReadSupport.PROTECTED_FORMAT -> R.string.capability_protected_format
        BalanceReadSupport.LEGACY_CEPAS -> R.string.capability_legacy_cepas
        BalanceReadSupport.PUBLIC_FELICA -> R.string.capability_public_felica
        BalanceReadSupport.CHINA_CARD_VARIANT -> R.string.capability_china_variant
        BalanceReadSupport.CHINA_PUBLIC_PURSE -> R.string.capability_china_public
        BalanceReadSupport.CLASSIC_CLIPPER -> R.string.capability_classic_clipper
    },
)

@Composable
private fun localizedBalanceExplanation(scan: TransitCardScan): String {
    if (scan.balance != null) {
        return if (scan.balance.isEstimated) {
            stringResource(R.string.balance_note_estimated)
        } else {
            stringResource(R.string.balance_note_public_read)
        }
    }

    return stringResource(
        when (scan.selectedProfile.balanceReadSupport) {
            BalanceReadSupport.ESTIMATED -> R.string.balance_reason_not_exposed
            BalanceReadSupport.ISSUER_KEYS -> R.string.balance_reason_issuer_keys
            BalanceReadSupport.PROTECTED_FORMAT -> R.string.balance_reason_protected_format
            BalanceReadSupport.LEGACY_CEPAS -> R.string.balance_reason_legacy_cepas
            BalanceReadSupport.PUBLIC_FELICA -> R.string.balance_reason_not_exposed
            BalanceReadSupport.CHINA_CARD_VARIANT -> R.string.balance_reason_china_variant
            BalanceReadSupport.CHINA_PUBLIC_PURSE -> R.string.balance_reason_not_exposed
            BalanceReadSupport.CLASSIC_CLIPPER -> R.string.balance_reason_clipper
        },
    )
}

@Composable
private fun ResultRow(label: String, value: String, monospace: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SelectionContainer {
            Text(
                text = value,
                fontWeight = FontWeight.Medium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}
