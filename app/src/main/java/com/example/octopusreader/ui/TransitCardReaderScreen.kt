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
import com.example.octopusreader.nfc.OctopusBalanceBasis
import com.example.octopusreader.nfc.TransitBalance
import com.example.octopusreader.nfc.TransitCardDetail
import com.example.octopusreader.nfc.TransitCardDetailType
import com.example.octopusreader.nfc.TransitCardProfile
import com.example.octopusreader.nfc.TransitCardScan
import com.example.octopusreader.nfc.TransitTransaction
import com.example.octopusreader.nfc.TransitTransactionType
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
    currentLanguageTag: String,
    onSelectLanguage: (String) -> Unit,
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
                octopusBalanceBasis = state.octopusBalanceBasis,
                enabled = !state.isReading && !state.isWaitingForCard,
                onSelect = viewModel::selectProfile,
                onSelectOctopusBalanceBasis = viewModel::selectOctopusBalanceBasis,
            )
            LanguageSelector(
                currentLanguageTag = currentLanguageTag,
                onSelectLanguage = onSelectLanguage,
            )
            InstructionCard()
            StatusCard(state)

            if (state.lastScans.size > 1) {
                Text(
                    text = stringResource(
                        R.string.multiple_applications_detected,
                        state.lastScans.size,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            state.lastScans.forEach { scan ->
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
private fun LanguageSelector(
    currentLanguageTag: String,
    onSelectLanguage: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguage = AppLanguage.fromLanguageTag(currentLanguageTag)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.app_language),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(localizedLanguageName(selectedLanguage))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    AppLanguage.entries.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(localizedLanguageName(language)) },
                            onClick = {
                                expanded = false
                                onSelectLanguage(language.languageTag)
                            },
                        )
                    }
                }
            }
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
    octopusBalanceBasis: OctopusBalanceBasis,
    enabled: Boolean,
    onSelect: (TransitCardProfile) -> Unit,
    onSelectOctopusBalanceBasis: (OctopusBalanceBasis) -> Unit,
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
                if (
                    selected == TransitCardProfile.AUTOMATIC ||
                    selected == TransitCardProfile.OCTOPUS
                ) {
                    OctopusBalanceBasisSelector(
                        selected = octopusBalanceBasis,
                        enabled = enabled,
                        onSelect = onSelectOctopusBalanceBasis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OctopusBalanceBasisSelector(
    selected: OctopusBalanceBasis,
    enabled: Boolean,
    onSelect: (OctopusBalanceBasis) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    HorizontalDivider()
    Text(
        text = stringResource(R.string.octopus_card_type),
        fontWeight = FontWeight.SemiBold,
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(localizedOctopusBalanceBasis(selected))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            OctopusBalanceBasis.entries.forEach { basis ->
                DropdownMenuItem(
                    text = { Text(localizedOctopusBalanceBasis(basis)) },
                    onClick = {
                        expanded = false
                        onSelect(basis)
                    },
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
            ResultRow(stringResource(R.string.detected_card), scan.detectedName)
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

            if (scan.details.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.card_details),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                scan.details.forEach { detail ->
                    ResultRow(
                        label = localizedDetailLabel(detail.type),
                        value = localizedDetailValue(detail),
                        monospace = detail.monospace,
                    )
                }
            }

            if (scan.transactions.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.recent_transactions),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Text(
                    text = stringResource(
                        if (scan.selectedProfile == TransitCardProfile.T_MONEY) {
                            R.string.tmoney_transaction_notice
                        } else {
                            R.string.transaction_code_notice
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                scan.transactions.forEachIndexed { index, transaction ->
                    TransactionCard(index + 1, transaction)
                }
            }

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

@Composable
private fun TransactionCard(index: Int, transaction: TransitTransaction) {
    val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.MEDIUM,
        FormatStyle.SHORT,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.transaction_title,
                    index,
                    localizedTransactionType(transaction.type),
                ),
                fontWeight = FontWeight.Bold,
            )
            transaction.timestamp?.let {
                ResultRow(stringResource(R.string.transaction_time), dateTimeFormatter.format(it))
            }
            ResultRow(
                stringResource(R.string.transaction_amount),
                formatBalance(
                    TransitBalance(
                        currencyCode = transaction.currencyCode,
                        amountMinor = transaction.amountMinor,
                        fractionDigits = transaction.fractionDigits,
                    ),
                ),
            )
            transaction.balanceAfterMinor?.let {
                ResultRow(
                    stringResource(R.string.transaction_balance_after),
                    formatBalance(
                        TransitBalance(
                            currencyCode = transaction.currencyCode,
                            amountMinor = it,
                            fractionDigits = transaction.fractionDigits,
                        ),
                    ),
                )
            }
            transaction.routeCode?.let {
                ResultRow(stringResource(R.string.bus_route_code), it, monospace = true)
            }
            if (transaction.type == TransitTransactionType.METRO) {
                ResultRow(
                    stringResource(R.string.boarding_station),
                    transaction.boardingStationCode ?: stringResource(R.string.not_stored_on_card),
                    monospace = transaction.boardingStationCode != null,
                )
                ResultRow(
                    stringResource(R.string.alighting_station_code),
                    transaction.alightingStationCode ?: stringResource(R.string.not_stored_on_card),
                    monospace = transaction.alightingStationCode != null,
                )
                transaction.gateCode?.let {
                    ResultRow(stringResource(R.string.gate_code), it, monospace = true)
                }
            }
            ResultRow(
                stringResource(R.string.terminal_operator_code),
                transaction.terminalCode,
                monospace = true,
            )
            ResultRow(
                stringResource(R.string.transaction_type_code),
                "%02X".format(transaction.transactionCode),
                monospace = true,
            )
            ResultRow(
                stringResource(R.string.transaction_sequence),
                transaction.sequenceCounter.toString(),
                monospace = true,
            )
            if (transaction.overdraftMinor > 0) {
                ResultRow(
                    stringResource(R.string.transaction_overdraft),
                    formatBalance(
                        TransitBalance(
                            currencyCode = transaction.currencyCode,
                            amountMinor = transaction.overdraftMinor,
                            fractionDigits = transaction.fractionDigits,
                        ),
                    ),
                )
            }
            ResultRow(
                stringResource(R.string.raw_transaction_record),
                transaction.rawDataHex,
                monospace = true,
            )
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
private fun localizedOctopusBalanceBasis(basis: OctopusBalanceBasis): String = stringResource(
    when (basis) {
        OctopusBalanceBasis.SOLD -> R.string.octopus_basis_sold
        OctopusBalanceBasis.ON_LOAN_OR_ELECTRONIC -> R.string.octopus_basis_on_loan_or_electronic
    },
)

@Composable
private fun localizedLanguageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.TRADITIONAL_CHINESE -> R.string.language_traditional_chinese
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_simplified_chinese
        AppLanguage.JAPANESE -> R.string.language_japanese
        AppLanguage.KOREAN -> R.string.language_korean
        AppLanguage.MALAY -> R.string.language_malay
    },
)

@Composable
private fun localizedDetailLabel(type: TransitCardDetailType): String = stringResource(
    when (type) {
        TransitCardDetailType.NFC_ID_LENGTH -> R.string.nfc_id_length
        TransitCardDetailType.MANUFACTURER_PARAMETERS -> R.string.manufacturer_parameters
        TransitCardDetailType.ANDROID_DISCOVERY_SYSTEM -> R.string.android_discovery_system
        TransitCardDetailType.FELICA_SYSTEM_CODES -> R.string.felica_system_codes
        TransitCardDetailType.RAW_BALANCE_UNITS -> R.string.raw_balance_units
        TransitCardDetailType.OCTOPUS_BALANCE_BASIS -> R.string.octopus_balance_basis
        TransitCardDetailType.APPLICATION_VERSION -> R.string.application_version
        TransitCardDetailType.ISSUER_CODE -> R.string.issuer_code
        TransitCardDetailType.VALID_FROM -> R.string.valid_from
        TransitCardDetailType.VALID_UNTIL -> R.string.valid_until
        TransitCardDetailType.BALANCE_PURSE_LAYOUT -> R.string.balance_purse_layout
        TransitCardDetailType.TRANSACTION_RECORDS_READ -> R.string.transaction_records_read
        TransitCardDetailType.APPLICATION_ID -> R.string.application_id
        TransitCardDetailType.CARD_TYPE_CODE -> R.string.card_type_code
        TransitCardDetailType.ISSUE_DATE -> R.string.issue_date
        TransitCardDetailType.MAXIMUM_BALANCE -> R.string.maximum_balance
        TransitCardDetailType.CARD_GENERATION -> R.string.card_generation
        TransitCardDetailType.MEMORY_SIZE -> R.string.memory_size
        TransitCardDetailType.SECTOR_COUNT -> R.string.sector_count
        TransitCardDetailType.BLOCK_COUNT -> R.string.block_count
        TransitCardDetailType.DESFIRE_HARDWARE_VERSION -> R.string.desfire_hardware_version
        TransitCardDetailType.DESFIRE_SOFTWARE_VERSION -> R.string.desfire_software_version
        TransitCardDetailType.DESFIRE_CHIP_IDENTIFIER -> R.string.desfire_chip_identifier
    },
)

@Composable
private fun localizedDetailValue(detail: TransitCardDetail): String =
    if (detail.type == TransitCardDetailType.OCTOPUS_BALANCE_BASIS) {
        localizedOctopusBalanceBasis(OctopusBalanceBasis.valueOf(detail.value))
    } else {
        detail.value
    }

@Composable
private fun localizedTransactionType(type: TransitTransactionType): String = stringResource(
    when (type) {
        TransitTransactionType.TOP_UP -> R.string.transaction_top_up
        TransitTransactionType.BUS -> R.string.transaction_bus
        TransitTransactionType.METRO -> R.string.transaction_metro
        TransitTransactionType.PURCHASE -> R.string.transaction_purchase
        TransitTransactionType.TRANSIT_RIDE -> R.string.transaction_transit_ride
        TransitTransactionType.UNKNOWN -> R.string.transaction_unknown
    },
)

@Composable
private fun scanButtonText(state: TransitCardReaderUiState): String = when {
    state.isReading -> stringResource(R.string.button_reading)
    state.isWaitingForCard -> stringResource(R.string.button_waiting)
    state.selectedProfile == null -> stringResource(R.string.button_select_first)
    state.selectedProfile == TransitCardProfile.AUTOMATIC ->
        stringResource(R.string.button_scan_automatic)
    else -> stringResource(
        R.string.button_scan_card,
        localizedProfileName(state.selectedProfile),
    )
}

@Composable
private fun localizedStatus(state: TransitCardReaderUiState): String {
    val profileName = state.selectedProfile?.let { localizedProfileName(it) }.orEmpty()
    val isAutomatic = state.selectedProfile == TransitCardProfile.AUTOMATIC
    return when (state.status) {
        ReaderStatus.SELECT_CARD -> stringResource(R.string.status_select_card)
        ReaderStatus.CARD_SELECTED -> if (isAutomatic) {
            stringResource(R.string.status_auto_ready)
        } else {
            stringResource(R.string.status_card_selected, profileName)
        }
        ReaderStatus.NFC_UNSUPPORTED -> stringResource(R.string.status_nfc_unsupported)
        ReaderStatus.NFC_DISABLED -> stringResource(R.string.status_nfc_disabled)
        ReaderStatus.NFC_READY -> if (isAutomatic) {
            stringResource(R.string.status_auto_ready)
        } else {
            stringResource(R.string.status_nfc_ready)
        }
        ReaderStatus.SELECT_REQUIRED -> stringResource(R.string.status_select_required)
        ReaderStatus.HOLD_CARD -> if (isAutomatic) {
            stringResource(R.string.status_hold_any_card)
        } else {
            stringResource(R.string.status_hold_card, profileName)
        }
        ReaderStatus.READING -> if (isAutomatic) {
            stringResource(R.string.status_auto_reading)
        } else {
            stringResource(R.string.status_reading, profileName)
        }
        ReaderStatus.READ_SUCCESS -> if (isAutomatic) {
            stringResource(R.string.status_auto_read_success, state.lastScans.size)
        } else {
            stringResource(R.string.status_read_success, profileName)
        }
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
    if (profile == TransitCardProfile.AUTOMATIC) {
        stringResource(R.string.automatic_detection)
    } else profile.localName?.let { localName ->
        stringResource(R.string.card_name_format, profile.displayName, localName)
    } ?: profile.displayName

@Composable
private fun localizedRegion(profile: TransitCardProfile): String = stringResource(
    when (profile) {
        TransitCardProfile.AUTOMATIC -> R.string.region_supported_cards
        TransitCardProfile.OCTOPUS -> R.string.region_hong_kong
        TransitCardProfile.EASYCARD,
        TransitCardProfile.IPASS,
        -> R.string.region_taiwan

        TransitCardProfile.EZLINK -> R.string.region_singapore
        TransitCardProfile.T_MONEY -> R.string.region_south_korea
        TransitCardProfile.JAPAN_TRANSIT_IC -> R.string.region_japan

        TransitCardProfile.YANGCHENGTONG -> R.string.region_guangzhou
        TransitCardProfile.SHENZHENTONG -> R.string.region_shenzhen
        TransitCardProfile.T_UNION -> R.string.region_china
        TransitCardProfile.CLIPPER -> R.string.region_san_francisco
        TransitCardProfile.MACAU_PASS -> R.string.region_macau
        TransitCardProfile.TOUCH_N_GO -> R.string.region_malaysia
        TransitCardProfile.OYSTER -> R.string.region_london_uk
    },
)

@Composable
private fun localizedCapability(support: BalanceReadSupport): String = stringResource(
    when (support) {
        BalanceReadSupport.AUTOMATIC -> R.string.capability_automatic
        BalanceReadSupport.ESTIMATED -> R.string.capability_estimated
        BalanceReadSupport.ISSUER_KEYS -> R.string.capability_issuer_keys
        BalanceReadSupport.PROTECTED_FORMAT -> R.string.capability_protected_format
        BalanceReadSupport.LEGACY_CEPAS -> R.string.capability_legacy_cepas
        BalanceReadSupport.PUBLIC_FELICA -> R.string.capability_public_felica
        BalanceReadSupport.CHINA_CARD_VARIANT -> R.string.capability_china_variant
        BalanceReadSupport.CHINA_PUBLIC_PURSE -> R.string.capability_china_public
        BalanceReadSupport.CLASSIC_CLIPPER -> R.string.capability_classic_clipper
        BalanceReadSupport.KOREAN_PUBLIC_PURSE -> R.string.capability_korean_public
        BalanceReadSupport.OYSTER_PROTECTED -> R.string.capability_oyster_protected
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
            BalanceReadSupport.AUTOMATIC -> R.string.balance_reason_automatic_unknown
            BalanceReadSupport.ESTIMATED -> R.string.balance_reason_not_exposed
            BalanceReadSupport.ISSUER_KEYS -> R.string.balance_reason_issuer_keys
            BalanceReadSupport.PROTECTED_FORMAT -> R.string.balance_reason_protected_format
            BalanceReadSupport.LEGACY_CEPAS -> R.string.balance_reason_legacy_cepas
            BalanceReadSupport.PUBLIC_FELICA -> R.string.balance_reason_not_exposed
            BalanceReadSupport.CHINA_CARD_VARIANT -> R.string.balance_reason_china_variant
            BalanceReadSupport.CHINA_PUBLIC_PURSE -> R.string.balance_reason_not_exposed
            BalanceReadSupport.CLASSIC_CLIPPER -> R.string.balance_reason_clipper
            BalanceReadSupport.KOREAN_PUBLIC_PURSE -> R.string.balance_reason_not_exposed
            BalanceReadSupport.OYSTER_PROTECTED -> R.string.balance_reason_oyster
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
